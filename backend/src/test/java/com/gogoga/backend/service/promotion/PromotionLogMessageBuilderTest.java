package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionLogMessageBuilderTest {

    private final PromotionLogMessageBuilder builder = new PromotionLogMessageBuilder();

    @Test
    void shouldTreatNullDecisionReasonAsNonReminder() {
        assertFalse(builder.isReminderDecisionReason(null));

        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setExecuteStatus("FAILED");

        assertFalse(builder.isReminderRecord(record));
        assertEquals("自动调价执行失败", builder.resolveStatusText(record));
        assertTrue(builder.buildFallbackMarkdown(record).contains("自动调价执行失败"));
    }

    @Test
    void shouldHideInternalUntrustedMetricReasonFromVisibleMessage() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setExecuteStatus("SKIPPED");
        record.setDecisionReason("UNTRUSTED_PROMOTION_METRICS:PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING");

        assertEquals("当前槽位推广指标不可信，已跳过自动调价",
                builder.resolveDecisionReasonText(record.getDecisionReason()));
        assertFalse(builder.buildPlainText(record).contains("PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING"));
        assertFalse(builder.buildFallbackMarkdown(record).contains("PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING"));
    }

    @Test
    void shouldHideUnknownInternalEnumsAndPlaceholdersFromVisibleMessage() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setExecuteStatus("FAILED");
        record.setDecisionReason("NEW_INTERNAL_REASON_MISSING");
        record.setErrorMsg("ACCESS_TOKEN_MISSING {{error_detail}}");

        String plainText = builder.buildPlainText(record);
        String fallbackMarkdown = builder.buildFallbackMarkdown(record);

        assertEquals("状态待确认", builder.resolveExecuteStatusText("UNKNOWN_STATUS"));
        assertTrue(plainText.contains("系统暂未返回可展示的原因说明"));
        assertTrue(plainText.contains("系统执行异常，请稍后重试或联系客服处理"));
        assertFalse(plainText.contains("NEW_INTERNAL_REASON_MISSING"));
        assertFalse(plainText.contains("ACCESS_TOKEN_MISSING"));
        assertFalse(plainText.contains("{{error_detail}}"));
        assertFalse(fallbackMarkdown.contains("NEW_INTERNAL_REASON_MISSING"));
        assertFalse(fallbackMarkdown.contains("ACCESS_TOKEN_MISSING"));
        assertFalse(fallbackMarkdown.contains("{{error_detail}}"));
    }
}
