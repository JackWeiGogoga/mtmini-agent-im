package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentReplyGeneration;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.enums.CommentReplySourceEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtCommentReplyGenerationMapper;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplyOfficialTemplateService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtCommentReplyContentService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.util.JsonUtil;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtCommentReplyContentServiceImpl implements MtCommentReplyContentService {

    private static final String LLM_PROVIDER_TOKENHUB = "TOKENHUB";
    private static final String POWER_SOURCE_TYPE = "COMMENT_RAG_REPLY";

    private final MtProperties mtProperties;
    private final CommentReplyOfficialTemplateService officialTemplateService;
    private final CommentReplyEmbeddingService commentReplyEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final MtCommentReplyGenerationMapper mtCommentReplyGenerationMapper;

    @Override
    public CommentReplyContentResultDTO generateReply(MtShop shop, MtComment comment) {
        if (shop == null || comment == null || comment.getCommentId() == null) {
            throw new BusinessException(400, "评价回复上下文不能为空");
        }
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromFoodCommentScore(comment.getFoodCommentScore());
        if (sentiment == CommentReplySentimentEnum.UNKNOWN) {
            log.warn("评价自动回复跳过，原因=food_comment_score异常，shopId={}, commentId={}, foodCommentScore={}",
                    shop.getId(), comment.getCommentId(), comment.getFoodCommentScore());
            CommentReplyContentResultDTO result = new CommentReplyContentResultDTO();
            result.setShouldReply(false);
            result.setSkipReason("FOOD_COMMENT_SCORE_INVALID");
            result.setSentiment(CommentReplySentimentEnum.UNKNOWN.name());
            return result;
        }

        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        if (!knowledge.isEnabled() || !knowledgeVectorStore.hasShopKnowledge(shop.getId())) {
            return buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.OFFICIAL_RANDOM, null, null);
        }

        MtPowerAccount account = mtPowerAccountService.getAccount(shop.getId());
        if (account == null || account.getBalance() == null || account.getBalance() <= knowledge.getRagBalanceThreshold()) {
            return buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "POWER_BALANCE_NOT_ENOUGH", null);
        }

        try {
            String queryText = buildQueryText(comment);
            float[] queryEmbedding = commentReplyEmbeddingService.embed(queryText);
            List<KnowledgeSearchHitDTO> hits = knowledgeVectorStore.searchTopK(shop.getId(), sentiment, queryEmbedding);
            if (hits.isEmpty()) {
                return buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "RAG_NO_HIT", null);
            }
            String knowledgeHitJson = toHitJson(hits);
            String reply = callChatModel(sentiment, comment, hits);
            if (StringUtils.isBlank(reply)) {
                return buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "LLM_EMPTY_REPLY", null);
            }
            int powerAmount = Math.max(knowledge.getFallbackRagPowerAmount(), 1);
            consumePower(shop.getId(), comment.getCommentId(), powerAmount);
            MtCommentReplyGeneration generation = insertGeneration(
                    shop,
                    comment,
                    CommentReplySourceEnum.SHOP_RAG,
                    sentiment,
                    StringUtils.left(StringUtils.trim(reply), 1000),
                    null,
                    knowledgeHitJson,
                    null,
                    null,
                    powerAmount
            );
            return toResult(generation, true, null);
        } catch (Exception e) {
            log.error("RAG 评论回复生成失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            return buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "RAG_ERROR", e.getMessage());
        }
    }

    private CommentReplyContentResultDTO buildOfficialResult(MtShop shop,
                                                             MtComment comment,
                                                             CommentReplySentimentEnum sentiment,
                                                             CommentReplySourceEnum source,
                                                             String fallbackReason,
                                                             String errorMessage) {
        MtReplyKnowledgeItem template = officialTemplateService.selectRandomEnabledTemplate(sentiment);
        String reply = template == null || StringUtils.isBlank(template.getAnswer())
                ? StringUtils.defaultIfBlank(mtProperties.getComment().getDefaultReply(), "很好")
                : StringUtils.trim(template.getAnswer());
        MtCommentReplyGeneration generation = insertGeneration(
                shop,
                comment,
                source,
                sentiment,
                reply,
                template == null ? null : template.getId(),
                null,
                fallbackReason,
                errorMessage,
                null
        );
        return toResult(generation, true, null);
    }

    private String callChatModel(CommentReplySentimentEnum sentiment, MtComment comment, List<KnowledgeSearchHitDTO> hits) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addSystemMessage(buildSystemPrompt(sentiment))
                .addUserMessage(buildUserPrompt(comment, hits))
                .build();
        ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(
                knowledge.getModelBaseUrl(),
                knowledge.getModelApiKey(),
                params
        );
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            return null;
        }
        return completion.choices().get(0).message().content().orElse(null);
    }

    private String buildSystemPrompt(CommentReplySentimentEnum sentiment) {
        String strategy = switch (sentiment) {
            case GOOD -> "好评回复：感谢顾客，结合反馈自然回应，邀请下次光临。";
            case NEUTRAL -> "中评回复：感谢反馈，承认不足，表达改进意愿。";
            case BAD -> "差评回复：先表达歉意，再回应具体问题，表达改进意愿。";
            case UNKNOWN -> "";
        };
        return "你是外卖商家客服。只能根据提供的知识和评价内容生成商家回复。"
                + "回复必须礼貌、自然、简短，30到100个中文字符。"
                + "不要编造知识库不存在的信息。不要承诺退款、赔偿、补发或私下联系，除非知识库明确提供。"
                + "只输出最终商家回复文本，不要解释。"
                + strategy;
    }

    private String buildUserPrompt(MtComment comment, List<KnowledgeSearchHitDTO> hits) {
        String contexts = hits.stream()
                .map(hit -> "- " + hit.getChunk().getChunkText())
                .collect(Collectors.joining("\n"));
        return "顾客评价：\n" + buildQueryText(comment)
                + "\n\n可用商户知识：\n" + contexts
                + "\n\n请生成一条商家回复。";
    }

    private String buildQueryText(MtComment comment) {
        return "评价内容：" + StringUtils.defaultString(comment.getCommentContent()) + "\n"
                + "追加评价：" + StringUtils.defaultString(comment.getAddComment()) + "\n"
                + "评价标签：" + StringUtils.defaultString(comment.getCommentLables()) + "\n"
                + "点赞菜品：" + StringUtils.defaultString(comment.getPraiseFoodList()) + "\n"
                + "差评菜品：" + StringUtils.defaultString(comment.getCriticFoodList());
    }

    private String toHitJson(List<KnowledgeSearchHitDTO> hits) {
        List<Map<String, Object>> rows = hits.stream().map(hit -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", hit.getChunk().getId());
            row.put("sourceType", hit.getChunk().getSourceType());
            row.put("sourceId", hit.getChunk().getSourceId());
            row.put("sentiment", hit.getChunk().getSentiment());
            row.put("score", hit.getScore());
            row.put("cosineScore", hit.getCosineScore());
            return row;
        }).collect(Collectors.toList());
        return JsonUtil.toJson(rows);
    }

    private void consumePower(Long shopId, Long commentId, Integer powerAmount) {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(commentId));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + commentId);
        request.setPowerAmount(powerAmount);
        request.setRemark("评论 RAG 自动回复");
        mtPowerConsumeService.consume(request);
    }

    private MtCommentReplyGeneration insertGeneration(MtShop shop,
                                                      MtComment comment,
                                                      CommentReplySourceEnum source,
                                                      CommentReplySentimentEnum sentiment,
                                                      String reply,
                                                      Long officialItemId,
                                                      String knowledgeHitJson,
                                                      String fallbackReason,
                                                      String errorMessage,
                                                      Integer powerAmount) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        MtCommentReplyGeneration generation = new MtCommentReplyGeneration();
        generation.setShopId(shop.getId());
        generation.setMtCommentId(comment.getId());
        generation.setCommentId(comment.getCommentId());
        generation.setReplySource(source.name());
        generation.setSentiment(sentiment.name());
        generation.setReplyContent(reply);
        generation.setOfficialItemId(officialItemId);
        generation.setKnowledgeHitJson(knowledgeHitJson);
        generation.setLlmProvider(source == CommentReplySourceEnum.SHOP_RAG ? LLM_PROVIDER_TOKENHUB : null);
        generation.setLlmModel(source == CommentReplySourceEnum.SHOP_RAG ? knowledge.getChatModel() : null);
        generation.setEmbeddingProvider(source == CommentReplySourceEnum.SHOP_RAG ? knowledge.getEmbeddingProvider() : null);
        generation.setEmbeddingModel(source == CommentReplySourceEnum.SHOP_RAG ? knowledge.getEmbeddingModel() : null);
        generation.setPowerAmount(powerAmount);
        generation.setFallbackReason(fallbackReason);
        generation.setErrorStage(StringUtils.isBlank(errorMessage) ? null : fallbackReason);
        generation.setErrorMessage(StringUtils.left(errorMessage, 1000));
        generation.setCreateTime(now);
        generation.setUpdateTime(now);
        mtCommentReplyGenerationMapper.insert(generation);
        return generation;
    }

    private CommentReplyContentResultDTO toResult(MtCommentReplyGeneration generation, boolean shouldReply, String skipReason) {
        CommentReplyContentResultDTO result = new CommentReplyContentResultDTO();
        result.setShouldReply(shouldReply);
        result.setSkipReason(skipReason);
        result.setReplyContent(generation.getReplyContent());
        result.setReplySource(generation.getReplySource());
        result.setSentiment(generation.getSentiment());
        result.setGenerationId(generation.getId());
        result.setKnowledgeHitJson(generation.getKnowledgeHitJson());
        result.setModel(Optional.ofNullable(generation.getLlmModel()).orElse(generation.getEmbeddingModel()));
        result.setPowerAmount(generation.getPowerAmount());
        return result;
    }
}
