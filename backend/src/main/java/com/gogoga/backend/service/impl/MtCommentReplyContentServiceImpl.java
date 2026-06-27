package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
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
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtCommentReplyContentService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.util.JsonUtil;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
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
    private final CommentImageVisionService commentImageVisionService;
    private final CommentReplyEmbeddingService commentReplyEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final MtCommentReplyGenerationMapper mtCommentReplyGenerationMapper;
    private final SystemPromptService systemPromptService;

    @Override
    public CommentReplyContentResultDTO generateReply(MtShop shop, MtComment comment) {
        return generateReplyInternal(shop, comment, true);
    }

    @Override
    public CommentReplyContentResultDTO previewReply(MtShop shop, MtComment comment) {
        return generateReplyInternal(shop, comment, false);
    }

    private CommentReplyContentResultDTO generateReplyInternal(MtShop shop, MtComment comment, boolean persistGeneration) {
        long startNanos = System.nanoTime();
        if (shop == null || comment == null || comment.getCommentId() == null) {
            throw new BusinessException(400, "评价回复上下文不能为空");
        }
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromProductAndPackingScore(
                comment.getProductScore(),
                comment.getPackingScore()
        );
        if (sentiment == CommentReplySentimentEnum.UNKNOWN) {
            log.warn("评价自动回复跳过，原因=商品/包装评分异常，shopId={}, commentId={}, productScore={}, packingScore={}",
                    shop.getId(), comment.getCommentId(), comment.getProductScore(), comment.getPackingScore());
            CommentReplyContentResultDTO result = new CommentReplyContentResultDTO();
            result.setShouldReply(false);
            result.setSkipReason("PRODUCT_PACKING_SCORE_INVALID");
            result.setSentiment(CommentReplySentimentEnum.UNKNOWN.name());
            return finishResult(result, startNanos, shop, comment, "SCORE_INVALID");
        }

        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        MtPowerAccount account = mtPowerAccountService.getAccount(shop.getId());
        if (account == null || account.getBalance() == null || account.getBalance() <= knowledge.getRagBalanceThreshold()) {
            return finishResult(buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "POWER_BALANCE_NOT_ENOUGH", null, persistGeneration, durationMs(startNanos)), startNanos, shop, comment, "POWER_BALANCE_NOT_ENOUGH");
        }

        CommentImageSummaryResultDTO imageSummary = null;
        try {
            long imageStart = System.nanoTime();
            imageSummary = commentImageVisionService.resolveForReply(shop, comment, true);
            log.info("评论回复图片识别阶段完成，shopId={}, commentId={}, durationMs={}",
                    shop.getId(), comment.getCommentId(), durationMs(imageStart));
            String queryText = buildQueryText(comment, imageSummary);
            List<KnowledgeSearchHitDTO> hits = resolvePromptKnowledgeHits(shop, sentiment, queryText);
            String knowledgeHitJson = hits.isEmpty() ? null : toHitJson(hits);
            long chatStart = System.nanoTime();
            String reply = callChatModel(sentiment, comment, imageSummary, hits);
            log.info("评论回复大模型阶段完成，shopId={}, commentId={}, durationMs={}",
                    shop.getId(), comment.getCommentId(), durationMs(chatStart));
            if (StringUtils.isBlank(reply)) {
                return finishResult(attachImageSummary(buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "LLM_EMPTY_REPLY", null, persistGeneration, durationMs(startNanos)), imageSummary), startNanos, shop, comment, "LLM_EMPTY_REPLY");
            }
            int powerAmount = Math.max(knowledge.getFallbackRagPowerAmount(), 1);
            if (persistGeneration) {
                consumePower(shop.getId(), comment.getCommentId(), powerAmount);
            }
            MtCommentReplyGeneration generation = buildGeneration(
                    shop,
                    comment,
                    hits.isEmpty() ? CommentReplySourceEnum.LLM : CommentReplySourceEnum.SHOP_RAG,
                    sentiment,
                    StringUtils.left(StringUtils.trim(reply), 1000),
                    null,
                    knowledgeHitJson,
                    null,
                    null,
                    persistGeneration ? powerAmount : null,
                    durationMs(startNanos)
            );
            if (persistGeneration) {
                mtCommentReplyGenerationMapper.insert(generation);
            }
            return finishResult(attachImageSummary(toResult(generation, true, null), imageSummary), startNanos, shop, comment, generation.getReplySource());
        } catch (Exception e) {
            log.error("大模型评论回复生成失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            return finishResult(attachImageSummary(buildOfficialResult(shop, comment, sentiment, CommentReplySourceEnum.FALLBACK_OFFICIAL, "LLM_ERROR", e.getMessage(), persistGeneration, durationMs(startNanos)), imageSummary), startNanos, shop, comment, "LLM_ERROR");
        }
    }

    private List<KnowledgeSearchHitDTO> resolvePromptKnowledgeHits(MtShop shop,
                                                                   CommentReplySentimentEnum sentiment,
                                                                   String queryText) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        if (!knowledge.isEnabled() || !knowledgeVectorStore.hasShopKnowledge(shop.getId())) {
            log.info("评论回复跳过知识检索，shopId={}, reason={}",
                    shop.getId(), knowledge.isEnabled() ? "SHOP_KNOWLEDGE_EMPTY" : "KNOWLEDGE_DISABLED");
            return Collections.emptyList();
        }
        long embeddingStart = System.nanoTime();
        float[] queryEmbedding = commentReplyEmbeddingService.embed(queryText);
        log.info("评论回复向量阶段完成，shopId={}, durationMs={}", shop.getId(), durationMs(embeddingStart));
        long searchStart = System.nanoTime();
        List<KnowledgeSearchHitDTO> hits = knowledgeVectorStore.searchTopK(shop.getId(), sentiment, queryEmbedding);
        if (hits == null) {
            hits = Collections.emptyList();
        }
        double minCosineScore = knowledge.getMinContextCosineScore();
        List<KnowledgeSearchHitDTO> promptHits = hits.stream()
                .filter(hit -> hit != null && hit.getChunk() != null)
                .filter(hit -> hit.getCosineScore() != null && hit.getCosineScore() >= minCosineScore)
                .collect(Collectors.toList());
        log.info("评论回复知识检索阶段完成，shopId={}, hitCount={}, promptHitCount={}, minCosineScore={}, durationMs={}",
                shop.getId(), hits.size(), promptHits.size(), minCosineScore, durationMs(searchStart));
        return promptHits;
    }

    private CommentReplyContentResultDTO buildOfficialResult(MtShop shop,
                                                             MtComment comment,
                                                             CommentReplySentimentEnum sentiment,
                                                             CommentReplySourceEnum source,
                                                             String fallbackReason,
                                                             String errorMessage,
                                                             boolean persistGeneration,
                                                             Integer durationMs) {
        MtReplyKnowledgeItem template = officialTemplateService.selectRandomEnabledTemplate(sentiment);
        String reply = template == null || StringUtils.isBlank(template.getAnswer())
                ? StringUtils.defaultIfBlank(mtProperties.getComment().getDefaultReply(), "很好")
                : StringUtils.trim(template.getAnswer());
        MtCommentReplyGeneration generation = buildGeneration(
                shop,
                comment,
                source,
                sentiment,
                reply,
                template == null ? null : template.getId(),
                null,
                fallbackReason,
                errorMessage,
                null,
                durationMs
        );
        if (persistGeneration) {
            mtCommentReplyGenerationMapper.insert(generation);
        }
        return toResult(generation, true, null);
    }

    private String callChatModel(CommentReplySentimentEnum sentiment,
                                 MtComment comment,
                                 CommentImageSummaryResultDTO imageSummary,
                                 List<KnowledgeSearchHitDTO> hits) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addSystemMessage(buildSystemPrompt(sentiment))
                .addUserMessage(buildUserPrompt(comment, imageSummary, hits))
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
        String fallback = "你是外卖商家客服。请根据顾客评价生成商家回复；如果提供了商户知识，只能在和评价高度相关时作为补充依据。"
                + "回复必须礼貌、自然、简短，30到100个中文字符。"
                + "不要编造评价和商户知识之外的具体事实。不要承诺退款、赔偿、补发或私下联系，除非商户知识明确提供。"
                + "只输出最终商家回复文本，不要解释。"
                + strategy;
        String promptKey = switch (sentiment) {
            case GOOD -> SystemPromptKeyConstant.COMMENT_REPLY_SYSTEM_GOOD;
            case NEUTRAL -> SystemPromptKeyConstant.COMMENT_REPLY_SYSTEM_NEUTRAL;
            case BAD -> SystemPromptKeyConstant.COMMENT_REPLY_SYSTEM_BAD;
            case UNKNOWN -> null;
        };
        if (StringUtils.isBlank(promptKey)) {
            return fallback;
        }
        return systemPromptService.resolve(promptKey, fallback).getContent();
    }

    private String buildUserPrompt(MtComment comment, CommentImageSummaryResultDTO imageSummary, List<KnowledgeSearchHitDTO> hits) {
        String contexts = hits.stream()
                .map(hit -> "- " + hit.getChunk().getChunkText())
                .collect(Collectors.joining("\n"));
        String fallback = "顾客评价：\n" + buildQueryText(comment, imageSummary)
                + "\n\n高匹配商户知识（无则显示“无”）：\n" + StringUtils.defaultIfBlank(contexts, "无")
                + "\n\n请生成一条商家回复。";
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("commentText", buildQueryText(comment, imageSummary));
        variables.put("knowledgeContexts", StringUtils.defaultIfBlank(contexts, "无"));
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_REPLY_USER,
                variables,
                fallback
        );
        return prompt.getContent();
    }

    private String buildQueryText(MtComment comment, CommentImageSummaryResultDTO imageSummary) {
        String text = "评价内容：" + StringUtils.defaultString(comment.getCommentContent()) + "\n"
                + "追加评价：" + StringUtils.defaultString(comment.getAddComment()) + "\n"
                + "评价标签：" + StringUtils.defaultString(comment.getCommentLables()) + "\n"
                + "点赞菜品：" + StringUtils.defaultString(comment.getPraiseFoodList()) + "\n"
                + "差评菜品：" + StringUtils.defaultString(comment.getCriticFoodList());
        if (imageSummary == null || StringUtils.isBlank(imageSummary.getSummaryText())) {
            return text;
        }
        return text + "\n评价图片摘要：" + imageSummary.getSummaryText();
    }

    private String toHitJson(List<KnowledgeSearchHitDTO> hits) {
        List<Map<String, Object>> rows = hits.stream().map(hit -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chunkId", hit.getChunk().getId());
            row.put("sourceType", hit.getChunk().getSourceType());
            row.put("sourceId", hit.getChunk().getSourceId());
            row.put("itemId", hit.getChunk().getItemId());
            row.put("documentId", hit.getChunk().getDocumentId());
            row.put("sentiment", hit.getChunk().getSentiment());
            row.put("chunkText", StringUtils.left(StringUtils.defaultString(hit.getChunk().getChunkText()), 1000));
            row.put("tokenCount", hit.getChunk().getTokenCount());
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

    private MtCommentReplyGeneration buildGeneration(MtShop shop,
                                                      MtComment comment,
                                                      CommentReplySourceEnum source,
                                                      CommentReplySentimentEnum sentiment,
                                                      String reply,
                                                      Long officialItemId,
                                                      String knowledgeHitJson,
                                                      String fallbackReason,
                                                      String errorMessage,
                                                      Integer powerAmount,
                                                      Integer durationMs) {
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
        boolean generatedByLlm = source == CommentReplySourceEnum.SHOP_RAG || source == CommentReplySourceEnum.LLM;
        generation.setLlmProvider(generatedByLlm ? LLM_PROVIDER_TOKENHUB : null);
        generation.setLlmModel(generatedByLlm ? knowledge.getChatModel() : null);
        generation.setEmbeddingProvider(source == CommentReplySourceEnum.SHOP_RAG ? knowledge.getEmbeddingProvider() : null);
        generation.setEmbeddingModel(source == CommentReplySourceEnum.SHOP_RAG ? knowledge.getEmbeddingModel() : null);
        generation.setPowerAmount(powerAmount);
        generation.setDurationMs(durationMs);
        generation.setFallbackReason(fallbackReason);
        generation.setErrorStage(StringUtils.isBlank(errorMessage) ? null : fallbackReason);
        generation.setErrorMessage(StringUtils.left(errorMessage, 1000));
        generation.setCreateTime(now);
        generation.setUpdateTime(now);
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
        result.setReplyDurationMs(generation.getDurationMs());
        return result;
    }

    private CommentReplyContentResultDTO finishResult(CommentReplyContentResultDTO result,
                                                      long startNanos,
                                                      MtShop shop,
                                                      MtComment comment,
                                                      String stage) {
        int durationMs = durationMs(startNanos);
        if (result != null && result.getReplyDurationMs() == null) {
            result.setReplyDurationMs(durationMs);
        }
        log.info("评论回复生成完成，shopId={}, commentId={}, stage={}, shouldReply={}, durationMs={}",
                shop == null ? null : shop.getId(),
                comment == null ? null : comment.getCommentId(),
                stage,
                result == null ? null : result.isShouldReply(),
                durationMs);
        return result;
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }

    private CommentReplyContentResultDTO attachImageSummary(CommentReplyContentResultDTO result, CommentImageSummaryResultDTO imageSummary) {
        if (result == null || imageSummary == null) {
            return result;
        }
        result.setImageSummaryStatus(imageSummary.getStatus());
        result.setImageSummaryText(imageSummary.getSummaryText());
        result.setImageSummaryJson(imageSummary.getSummaryJson());
        result.setImageSummaryErrorMessage(imageSummary.getErrorMessage());
        result.setImagePromptTokens(imageSummary.getPromptTokens());
        result.setImageCompletionTokens(imageSummary.getCompletionTokens());
        result.setImageTotalTokens(imageSummary.getTotalTokens());
        result.setImagePowerAmount(imageSummary.getPowerAmount());
        return result;
    }
}
