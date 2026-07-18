package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentLogAnalysisPreviewDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.CommentLogAnalysisService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.gogoga.backend.service.llm.LlmRateLimitContext;
import com.gogoga.backend.service.async.MtCommentLogAnalysisTaskPayload;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评论日志 AI 分析生成服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentLogAnalysisServiceImpl implements CommentLogAnalysisService {

    private static final String POWER_SOURCE_TYPE = "COMMENT_LOG_ANALYSIS";
    private static final int MAX_ANALYSIS_TEXT_LENGTH = 500;

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final SystemPromptService systemPromptService;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final MtCommentMapper mtCommentMapper;
    private final MtShopMapper mtShopMapper;

    @Override
    public void executeCommentLogAnalysisTask(MtCommentLogAnalysisTaskPayload payload) {
        if (payload == null || payload.getShopId() == null || payload.getCommentId() == null) {
            throw new IllegalArgumentException("COMMENT_LOG_ANALYSIS_PAYLOAD_INVALID");
        }
        MtComment comment = mtCommentMapper.selectByCommentId(payload.getCommentId());
        if (comment == null) {
            return;
        }
        if (!payload.getShopId().equals(comment.getShopId())) {
            throw new IllegalStateException("COMMENT_LOG_ANALYSIS_SHOP_MISMATCH");
        }
        if (!StringUtils.equals(comment.getAutoReplyStatus(), "SUCCESS")) {
            throw new IllegalStateException("COMMENT_LOG_ANALYSIS_AUTO_REPLY_NOT_SUCCESS");
        }
        if (StringUtils.equals(comment.getAnalysisStatus(), "SUCCESS")) {
            return;
        }
        MtShop shop = mtShopMapper.selectById(payload.getShopId());
        if (shop == null) {
            throw new IllegalStateException("COMMENT_LOG_ANALYSIS_SHOP_MISSING");
        }
        generateAfterAutoReply(shop, comment);
    }

    @Override
    public void generateAfterAutoReply(MtShop shop, MtComment comment) {
        long startNanos = System.nanoTime();
        if (shop == null || shop.getId() == null || comment == null || comment.getId() == null || comment.getCommentId() == null) {
            return;
        }
        if (StringUtils.equals(comment.getAnalysisStatus(), "SUCCESS")) {
            return;
        }
        try {
            AnalysisResponse response = callModel(comment);
            if (StringUtils.isBlank(response.content)) {
                throw new IllegalStateException("COMMENT_LOG_ANALYSIS_EMPTY_REPLY");
            }
            int powerAmount = calculatePowerAmount(response);
            consumePower(shop.getId(), comment.getCommentId(), powerAmount);
            LocalDateTime now = LocalDateTime.now();
            mtCommentMapper.markAnalysisSuccess(
                    comment.getId(),
                    StringUtils.left(StringUtils.trim(response.content), MAX_ANALYSIS_TEXT_LENGTH),
                    response.model,
                    response.promptKey,
                    response.promptVersion,
                    response.promptTokens,
                    response.completionTokens,
                    powerAmount,
                    durationMs(startNanos),
                    now,
                    now
            );
            log.info("评论日志 AI 分析生成完成，shopId={}, commentId={}, durationMs={}",
                    shop.getId(), comment.getCommentId(), durationMs(startNanos));
        } catch (LlmCapacityDeferredException e) {
            throw e;
        } catch (Exception e) {
            String error = StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), "COMMENT_LOG_ANALYSIS_FAILED"), 1000);
            mtCommentMapper.markAnalysisFailed(comment.getId(), error, LocalDateTime.now());
            log.warn("评论日志 AI 分析生成失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            throw new IllegalStateException(error, e);
        }
    }

    @Override
    public CommentLogAnalysisPreviewDTO preview(MtComment comment) {
        long startNanos = System.nanoTime();
        CommentLogAnalysisPreviewDTO preview = new CommentLogAnalysisPreviewDTO();
        try {
            AnalysisResponse response = LlmRateLimitContext.interactive(() -> callModel(comment));
            preview.setAnalysisText(StringUtils.left(StringUtils.trim(response.content), MAX_ANALYSIS_TEXT_LENGTH));
            preview.setModel(response.model);
            preview.setPromptTokens(response.promptTokens);
            preview.setCompletionTokens(response.completionTokens);
            preview.setTotalTokens(sumTokens(response.promptTokens, response.completionTokens));
            preview.setEstimatedPowerAmount(calculatePowerAmount(response));
            preview.setDurationMs(durationMs(startNanos));
            log.info("评论日志 AI 分析预览生成完成，commentId={}, durationMs={}",
                    comment == null ? null : comment.getCommentId(), preview.getDurationMs());
        } catch (LlmCapacityDeferredException e) {
            preview.setErrorMessage("模型繁忙，请稍后重试");
            preview.setDurationMs(durationMs(startNanos));
            log.info("评论日志 AI 分析预览等待模型容量，commentId={}",
                    comment == null ? null : comment.getCommentId());
        } catch (Exception e) {
            preview.setErrorMessage(StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), "COMMENT_LOG_ANALYSIS_PREVIEW_FAILED"), 1000));
            preview.setDurationMs(durationMs(startNanos));
            log.warn("评论日志 AI 分析预览生成失败", e);
        }
        return preview;
    }

    private AnalysisResponse callModel(MtComment comment) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_LOG_ANALYSIS,
                buildVariables(comment),
                defaultPromptFallback()
        );
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addUserMessage(prompt.getContent())
                .build();
        ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(
                knowledge.getModelProvider(),
                knowledge.getModelBaseUrl(),
                knowledge.getModelApiKey(),
                params
        );
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new IllegalStateException("COMMENT_LOG_ANALYSIS_EMPTY_RESPONSE");
        }
        AnalysisResponse response = new AnalysisResponse();
        response.content = completion.choices().get(0).message().content().orElse(null);
        response.model = StringUtils.defaultIfBlank(completion.model(), knowledge.getChatModel());
        response.promptKey = prompt.getPromptKey();
        response.promptVersion = prompt.getVersion();
        completion.usage().ifPresent(usage -> fillUsage(response, usage));
        return response;
    }

    private void fillUsage(AnalysisResponse response, CompletionUsage usage) {
        response.promptTokens = toInt(usage.promptTokens());
        response.completionTokens = toInt(usage.completionTokens());
    }

    private Integer toInt(long value) {
        if (value <= 0L) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Integer sumTokens(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        return Math.max(promptTokens == null ? 0 : promptTokens, 0)
                + Math.max(completionTokens == null ? 0 : completionTokens, 0);
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }

    private int calculatePowerAmount(AnalysisResponse response) {
        Long promptTokens = response.promptTokens == null ? null : response.promptTokens.longValue();
        Long completionTokens = response.completionTokens == null ? null : response.completionTokens.longValue();
        try {
            return powerProperties.getBilling().calculateLlmPowerAmount(promptTokens, completionTokens);
        } catch (Exception e) {
            return Math.max(mtProperties.getComment().getKnowledge().getAnalysisFallbackPowerAmount(), 1);
        }
    }

    private void consumePower(Long shopId, Long commentId, int powerAmount) {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(commentId));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + commentId);
        request.setPowerAmount(powerAmount);
        request.setRemark("评论日志 AI 分析");
        mtPowerConsumeService.consume(request);
    }

    private Map<String, Object> buildVariables(MtComment comment) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sentiment", resolveSentiment(comment));
        variables.put("orderCommentScore", comment.getOrderCommentScore());
        variables.put("deliveryCommentScore", comment.getDeliveryCommentScore());
        variables.put("productScore", comment.getProductScore());
        variables.put("packingScore", comment.getPackingScore());
        variables.put("commentLabels", comment.getCommentLables());
        variables.put("overDeliveryTimeDesc", comment.getOverDeliveryTimeDesc());
        variables.put("commentContent", comment.getCommentContent());
        variables.put("addComment", comment.getAddComment());
        variables.put("imageSummaryText", comment.getImageSummaryText());
        return variables;
    }

    private String resolveSentiment(MtComment comment) {
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromScores(
                comment.getProductScore(),
                comment.getPackingScore(),
                comment.getDeliveryCommentScore(),
                comment.getFoodCommentScore()
        );
        return sentiment == null ? CommentReplySentimentEnum.UNKNOWN.name() : sentiment.name();
    }

    private String defaultPromptFallback() {
        return "你是一名外卖商家评价运营分析助手。请基于以下评价数据，输出给商家查看的分析日志文案。\n"
                + "要求：500 个中文字符以内；先给整体判断，再指出主要原因和可执行改进建议；"
                + "语气专业、克制、易懂，可使用最多 2 个表情符号；不要输出 JSON、Markdown 表格或标题编号；"
                + "不得承诺退款、补发、赔偿或私下联系顾客。\n"
                + "评价类型：{{sentiment}}\n"
                + "口味顾客评分：{{orderCommentScore}}\n"
                + "配送顾客评分：{{deliveryCommentScore}}\n"
                + "商品顾客评分：{{productScore}}\n"
                + "包装顾客评分：{{packingScore}}\n"
                + "评价标签：{{commentLabels}}\n"
                + "配送时效：{{overDeliveryTimeDesc}}\n"
                + "顾客首次评价内容：{{commentContent}}\n"
                + "追评内容：{{addComment}}\n"
                + "评价图片摘要：{{imageSummaryText}}";
    }

    private static class AnalysisResponse {
        private String content;
        private String model;
        private String promptKey;
        private Integer promptVersion;
        private Integer promptTokens;
        private Integer completionTokens;
    }
}
