package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.enums.MtPromotionLogMarkdownStatusEnum;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.service.MtPromotionAdDataService;
import com.gogoga.backend.service.PromotionPowerConsumeService;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 推广日志异步任务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionLogAsyncTaskService {

    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final PromotionLogMarkdownService promotionLogMarkdownService;
    private final PromotionLogMessageBuilder promotionLogMessageBuilder;
    private final MtPromotionAdDataService mtPromotionAdDataService;
    private final PromotionPowerConsumeService promotionPowerConsumeService;

    public void generateAndPersistMarkdown(Long recordId, String accessToken) {
        if (recordId == null) {
            return;
        }
        MtPromotionBidRecord record = mtPromotionBidRecordMapper.selectById(recordId);
        if (record == null) {
            log.warn("推广日志异步任务未找到执行记录，recordId={}", recordId);
            return;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        if (detail == null) {
            detail = new PromotionDecisionDetail();
        }
        if ("MANUAL".equals(record.getRecordSource())) {
            record.setMarkdownMessage(promotionLogMessageBuilder == null
                    ? record.getMarkdownMessage()
                    : promotionLogMessageBuilder.buildFallbackMarkdown(record));
            detail.setLogMarkdownSource("manual");
            detail.setLogLlmFailureSummary(null);
            detail.setLogLlmRawResponseSummary(null);
            detail.setLogLlmPromptTokens(null);
            detail.setLogLlmCompletionTokens(null);
            detail.setLogLlmTotalTokens(null);
            mtPromotionBidRecordMapper.updateMarkdownAndDecisionDetail(
                    record.getId(),
                    record.getMarkdownMessage(),
                    MtPromotionLogMarkdownStatusEnum.READY.name(),
                    JsonUtil.toJson(detail),
                    java.time.LocalDateTime.now()
            );
            return;
        }
        if ("SUCCESS".equals(record.getExecuteStatus())
                && StringUtils.isNotBlank(accessToken)
                && mtPromotionAdDataService != null) {
            mtPromotionAdDataService.ensureRecentAdDataBackfillBeforeLogGeneration(record.getShopId(), accessToken);
        }

        PromotionLogMarkdownResult markdownResult = buildMarkdownResult(record);
        record.setMarkdownMessage(markdownResult.getMarkdownMessage());
        detail.setLogMarkdownSource(markdownResult.isFallback() ? "fallback" : "llm");
        detail.setLogLlmFailureSummary(markdownResult.getFailureSummary());
        detail.setLogLlmRawResponseSummary(markdownResult.getRawResponseSummary());
        detail.setLogLlmPromptTokens(markdownResult.getPromptTokens());
        detail.setLogLlmCompletionTokens(markdownResult.getCompletionTokens());
        detail.setLogLlmTotalTokens(markdownResult.getTotalTokens());

        if (markdownResult.getTotalTokens() != null
                && markdownResult.getTotalTokens() > 0
                && promotionPowerConsumeService != null) {
            try {
                promotionPowerConsumeService.consumeForLogGeneration(
                        record.getShopId(),
                        record.getSlotTime(),
                        markdownResult.getPromptTokens(),
                        markdownResult.getCompletionTokens()
                );
            } catch (Exception e) {
                detail.setLogPowerConsumeFailureSummary(StringUtils.left(e.getMessage(), 300));
                log.warn("推广日志异步任务算力扣减失败，recordId={}, shopId={}, slotTime={}, reason={}",
                        recordId, record.getShopId(), record.getSlotTime(), e.getMessage());
            }
        }

        mtPromotionBidRecordMapper.updateMarkdownAndDecisionDetail(
                record.getId(),
                record.getMarkdownMessage(),
                markdownResult.isFallback()
                        ? MtPromotionLogMarkdownStatusEnum.FALLBACK.name()
                        : MtPromotionLogMarkdownStatusEnum.READY.name(),
                JsonUtil.toJson(detail),
                java.time.LocalDateTime.now()
        );
    }

    private PromotionLogMarkdownResult buildMarkdownResult(MtPromotionBidRecord record) {
        if (record == null) {
            PromotionLogMarkdownResult result = new PromotionLogMarkdownResult();
            result.setFallback(true);
            result.setFailureSummary("RECORD_MISSING");
            return result;
        }
        if (promotionLogMarkdownService == null) {
            return buildFallbackResult(record, "LOG_MARKDOWN_SERVICE_MISSING");
        }
        if (!"SUCCESS".equals(record.getExecuteStatus())) {
            return buildFallbackResult(record, "NON_SUCCESS_RECORD_FALLBACK");
        }
        PromotionLogMarkdownResult result = promotionLogMarkdownService.generate(record);
        if (StringUtils.isBlank(result.getMarkdownMessage())) {
            return buildFallbackResult(record, StringUtils.defaultIfBlank(result.getFailureSummary(), "EMPTY_MARKDOWN"));
        }
        return result;
    }

    private PromotionLogMarkdownResult buildFallbackResult(MtPromotionBidRecord record, String reason) {
        PromotionLogMarkdownResult result = new PromotionLogMarkdownResult();
        result.setFallback(true);
        result.setFailureSummary(reason);
        result.setMarkdownMessage(promotionLogMessageBuilder == null ? null : promotionLogMessageBuilder.buildFallbackMarkdown(record));
        return result;
    }
}
