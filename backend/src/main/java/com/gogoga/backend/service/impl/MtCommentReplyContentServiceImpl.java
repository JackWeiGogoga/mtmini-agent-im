package com.gogoga.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.dto.CommentReplySensitiveWordHitDTO;
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
import com.gogoga.backend.service.CommentReplySensitiveWordService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MtCommentReplyContentServiceImpl implements MtCommentReplyContentService {

    private static final String LLM_PROVIDER_TOKENHUB = "TOKENHUB";
    private static final String POWER_SOURCE_TYPE = "COMMENT_RAG_REPLY";
    private static final String COMMENT_REPLY_COMPLIANCE_RULES = "回复必须符合美团平台商家回复规范："
            + "不引导用户添加微信、QQ、电话、支付宝、其他平台、二维码或任何站外联系方式；"
            + "不引导线下交易、私下退款、平台外优惠或绕开平台处理；"
            + "不使用辱骂、威胁、歧视、低俗色情、违法犯罪、政治宗教敏感、平台攻击类表达；"
            + "不使用第一、最好、100%、根治、永久、无风险等极限广告或医疗功效承诺；"
            + "不承诺好评返现、删评退款、补偿改差评、刷单刷评；"
            + "如需售后处理，只引导用户通过美团订单、平台客服或店内合规流程继续沟通。";

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
    private final CommentReplySensitiveWordService commentReplySensitiveWordService;

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
            SensitiveReplyResult sensitiveResult = resolveSensitiveReply(shop, comment, sentiment, imageSummary, hits, reply);
            int powerAmount = Math.max(knowledge.getFallbackRagPowerAmount(), 1) * Math.max(1, 1 + sensitiveResult.getRewriteCount());
            if (persistGeneration) {
                consumePower(shop.getId(), comment.getCommentId(), powerAmount);
            }
            MtCommentReplyGeneration generation = buildGeneration(
                    shop,
                    comment,
                    sensitiveResult.getSource(),
                    sentiment,
                    StringUtils.left(StringUtils.trim(sensitiveResult.getReply()), 1000),
                    sensitiveResult.getOfficialItemId(),
                    sensitiveResult.getSource() == CommentReplySourceEnum.SHOP_RAG ? knowledgeHitJson : null,
                    sensitiveResult.getFallbackReason(),
                    null,
                    persistGeneration ? powerAmount : null,
                    durationMs(startNanos),
                    sensitiveResult.getSensitiveHitJson(),
                    sensitiveResult.getRewriteCount(),
                    sensitiveResult.isFallbackUsed()
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

    private SensitiveReplyResult resolveSensitiveReply(MtShop shop,
                                                       MtComment comment,
                                                       CommentReplySentimentEnum sentiment,
                                                       CommentImageSummaryResultDTO imageSummary,
                                                       List<KnowledgeSearchHitDTO> hits,
                                                       String initialReply) {
        CommentReplySourceEnum source = hits.isEmpty() ? CommentReplySourceEnum.LLM : CommentReplySourceEnum.SHOP_RAG;
        CommentReplySensitiveWordHitDTO initialHit = commentReplySensitiveWordService.detect(initialReply);
        if (!initialHit.isHit()) {
            return SensitiveReplyResult.llm(initialReply, source);
        }
        String sensitiveHitJson = toSensitiveHitJson("INITIAL_REPLY", initialHit);
        log.warn("评论回复敏感词命中，shopId={}, commentId={}, stage=INITIAL_REPLY, categories={}, hitCount={}",
                shop.getId(), comment.getCommentId(), initialHit.getCategories(), initialHit.getHitCount());
        int maxRewriteAttempts = Math.max(0, mtProperties.getComment().getSensitiveWord().getMaxRewriteAttempts());
        if (maxRewriteAttempts > 0) {
            try {
                String rewritten = callSensitiveRewriteModel(sentiment, comment, imageSummary, hits, initialReply, initialHit);
                CommentReplySensitiveWordHitDTO rewriteHit = commentReplySensitiveWordService.detect(rewritten);
                if (StringUtils.isNotBlank(rewritten) && !rewriteHit.isHit()) {
                    return SensitiveReplyResult.rewritten(rewritten, source, sensitiveHitJson, 1);
                }
                if (rewriteHit.isHit()) {
                    sensitiveHitJson = mergeSensitiveHitJson(sensitiveHitJson, "REWRITE_REPLY", rewriteHit);
                    log.warn("评论回复改写后仍命中敏感词，shopId={}, commentId={}, categories={}, hitCount={}",
                            shop.getId(), comment.getCommentId(), rewriteHit.getCategories(), rewriteHit.getHitCount());
                }
            } catch (Exception e) {
                log.warn("评论回复敏感词改写失败，shopId={}, commentId={}, error={}",
                        shop.getId(), comment.getCommentId(), StringUtils.left(e.getMessage(), 200));
            }
        }
        SafeOfficialReply fallback = selectSafeOfficialFallback(sentiment);
        return SensitiveReplyResult.fallback(
                fallback.getReply(),
                fallback.getOfficialItemId(),
                sensitiveHitJson,
                maxRewriteAttempts > 0 ? 1 : 0
        );
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
                .filter(hit -> {
                    CommentReplySensitiveWordHitDTO sensitiveHit = commentReplySensitiveWordService.detect(hit.getChunk().getChunkText());
                    if (!sensitiveHit.isHit()) {
                        return true;
                    }
                    log.warn("评论回复知识上下文敏感词过滤，shopId={}, chunkId={}, categories={}, hitCount={}",
                            shop.getId(), hit.getChunk().getId(), sensitiveHit.getCategories(), sensitiveHit.getHitCount());
                    return false;
                })
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
                durationMs,
                null,
                0,
                false
        );
        if (persistGeneration) {
            mtCommentReplyGenerationMapper.insert(generation);
        }
        return toResult(generation, true, null);
    }

    private String callSensitiveRewriteModel(CommentReplySentimentEnum sentiment,
                                             MtComment comment,
                                             CommentImageSummaryResultDTO imageSummary,
                                             List<KnowledgeSearchHitDTO> hits,
                                             String initialReply,
                                             CommentReplySensitiveWordHitDTO hit) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addSystemMessage("你是外卖商家客服回复合规改写助手。请在不新增承诺、不引导站外联系、不复述敏感词的前提下，把商家回复改写为礼貌、自然、简短的合规文本。"
                        + COMMENT_REPLY_COMPLIANCE_RULES
                        + "只输出最终回复文本。")
                .addUserMessage(buildSensitiveRewritePrompt(comment, imageSummary, hits, initialReply, hit))
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

    private String buildSensitiveRewritePrompt(MtComment comment,
                                               CommentImageSummaryResultDTO imageSummary,
                                               List<KnowledgeSearchHitDTO> hits,
                                               String initialReply,
                                               CommentReplySensitiveWordHitDTO hit) {
        String contexts = hits.stream()
                .map(item -> "- " + item.getChunk().getChunkText())
                .collect(Collectors.joining("\n"));
        return "顾客评价：\n" + buildQueryText(comment, imageSummary)
                + "\n\n高匹配商户知识（如无则为无）：\n" + StringUtils.defaultIfBlank(contexts, "无")
                + "\n\n原商家回复：\n" + StringUtils.defaultString(initialReply)
                + "\n\n命中敏感类别：\n" + StringUtils.join(hit.getCategories(), "、")
                + "\n\n上一版回复包含平台敏感或违规表达。请删除相关表达，在不改变安抚、解释、致歉、邀评等业务目的的前提下，改写为自然、克制、合规的商家回复。不要解释敏感词规则，也不要复述被命中的词。";
    }

    private SafeOfficialReply selectSafeOfficialFallback(CommentReplySentimentEnum sentiment) {
        List<MtReplyKnowledgeItem> templates = new ArrayList<>(officialTemplateService.listEnabledTemplates(sentiment));
        while (!templates.isEmpty()) {
            int index = ThreadLocalRandom.current().nextInt(templates.size());
            MtReplyKnowledgeItem template = templates.remove(index);
            String reply = template == null ? null : StringUtils.trimToNull(template.getAnswer());
            if (StringUtils.isBlank(reply)) {
                continue;
            }
            CommentReplySensitiveWordHitDTO hit = commentReplySensitiveWordService.detect(reply);
            if (!hit.isHit()) {
                return new SafeOfficialReply(reply, template.getId());
            }
            log.warn("官方兜底回复敏感词过滤，templateId={}, sentiment={}, categories={}",
                    template.getId(), sentiment, hit.getCategories());
        }
        String defaultReply = StringUtils.defaultIfBlank(mtProperties.getComment().getDefaultReply(), "很好");
        CommentReplySensitiveWordHitDTO hit = commentReplySensitiveWordService.detect(defaultReply);
        if (hit.isHit()) {
            throw new BusinessException("默认回复命中敏感词类别：" + StringUtils.join(hit.getCategories(), "、"));
        }
        return new SafeOfficialReply(defaultReply, null);
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
                + COMMENT_REPLY_COMPLIANCE_RULES
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
                                                      Integer durationMs,
                                                      String sensitiveHitJson,
                                                      Integer sensitiveRewriteCount,
                                                      Boolean sensitiveFallbackUsed) {
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
        generation.setSensitiveHitJson(sensitiveHitJson);
        generation.setSensitiveRewriteCount(sensitiveRewriteCount);
        generation.setSensitiveFallbackUsed(sensitiveFallbackUsed);
        generation.setFallbackReason(fallbackReason);
        generation.setErrorStage(StringUtils.isBlank(errorMessage) ? null : fallbackReason);
        generation.setErrorMessage(StringUtils.left(errorMessage, 1000));
        generation.setCreateTime(now);
        generation.setUpdateTime(now);
        return generation;
    }

    private String toSensitiveHitJson(String stage, CommentReplySensitiveWordHitDTO hit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(sensitiveHitRow(stage, hit));
        return JsonUtil.toJson(rows);
    }

    private String mergeSensitiveHitJson(String currentJson, String stage, CommentReplySensitiveWordHitDTO hit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("stage", "INITIAL_REPLY"));
        if (StringUtils.isNotBlank(currentJson)) {
            try {
                List<Map<String, Object>> parsed = JsonUtil.toBean(currentJson, new TypeReference<List<Map<String, Object>>>() {});
                rows.clear();
                if (parsed != null) {
                    rows.addAll(parsed);
                }
            } catch (Exception ignored) {
                rows.clear();
            }
        }
        rows.add(sensitiveHitRow(stage, hit));
        return JsonUtil.toJson(rows);
    }

    private Map<String, Object> sensitiveHitRow(String stage, CommentReplySensitiveWordHitDTO hit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stage", stage);
        row.put("categories", hit == null ? List.of() : hit.getCategories());
        row.put("hitCount", hit == null ? 0 : hit.getHitCount());
        row.put("highestSeverity", hit == null ? null : hit.getHighestSeverity());
        return row;
    }

    @lombok.Value
    private static class SafeOfficialReply {
        String reply;
        Long officialItemId;
    }

    @lombok.Value
    private static class SensitiveReplyResult {
        String reply;
        CommentReplySourceEnum source;
        Long officialItemId;
        String sensitiveHitJson;
        int rewriteCount;
        boolean fallbackUsed;
        String fallbackReason;

        static SensitiveReplyResult llm(String reply, CommentReplySourceEnum source) {
            return new SensitiveReplyResult(reply, source, null, null, 0, false, null);
        }

        static SensitiveReplyResult rewritten(String reply, CommentReplySourceEnum source, String sensitiveHitJson, int rewriteCount) {
            return new SensitiveReplyResult(reply, source, null, sensitiveHitJson, rewriteCount, false, null);
        }

        static SensitiveReplyResult fallback(String reply, Long officialItemId, String sensitiveHitJson, int rewriteCount) {
            return new SensitiveReplyResult(reply, CommentReplySourceEnum.FALLBACK_OFFICIAL, officialItemId, sensitiveHitJson, rewriteCount, true, "SENSITIVE_WORD_FALLBACK");
        }
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
