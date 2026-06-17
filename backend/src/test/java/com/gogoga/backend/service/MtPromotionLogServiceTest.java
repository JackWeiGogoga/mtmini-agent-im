package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionLogReadState;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionLogReadStateMapper;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.vo.MtH5PromotionLogVO;
import com.github.pagehelper.PageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MtPromotionLogServiceTest {

    @Mock
    private MtPromotionBidRecordMapper mtPromotionBidRecordMapper;

    @Mock
    private MtPromotionLogReadStateMapper mtPromotionLogReadStateMapper;

    private MtPromotionLogService mtPromotionLogService;

    @BeforeEach
    void setUp() {
        mtPromotionLogService = new MtPromotionLogService(
                mtPromotionBidRecordMapper,
                mtPromotionLogReadStateMapper,
                new PromotionLogMessageBuilder()
        );
    }

    @Test
    void shouldRenderMerchantFriendlyMessageWhenRuleFallbackReasonExists() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 12, 30));
        record.setExecuteStatus("SUCCESS");
        record.setDecisionAction("DECREASE");
        record.setDecisionReason("TOTAL_COST_RATIO_ABOVE_MAX");
        record.setTotalCost(new BigDecimal("5.40"));
        record.setTotalIncome(new BigDecimal("53.47"));
        record.setTotalCostRatio(new BigDecimal("0.100991"));
        record.setCostDelta(new BigDecimal("0.30"));
        record.setNextBidAmount(new BigDecimal("0.30"));
        record.setLogMarkdownStatus("READY");

        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode("rule");
        detail.setDecisionSource("rule");
        detail.setLlmFailureSummary("RATE_ABOVE_MAX_SKIP_LLM");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        String message = pageInfo.getList().get(0).getMessage();
        assertTrue(message.contains("当前窗口累计费用率超过策略上限"));
        assertTrue(message.contains("当前窗口累计费用率已经超过上限，这次先按控成本方式自动降价"));
        assertTrue(!message.contains("RATE_ABOVE_MAX_SKIP_LLM"));
        assertTrue(pageInfo.getList().get(0).getMarkdownMessage().contains("自动调价执行成功"));
        assertEquals("READY", pageInfo.getList().get(0).getMarkdownStatus());
    }

    @Test
    void shouldRenderPlanStatusOperationMessage() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 8, 0));
        record.setExecuteStatus("SKIPPED");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("OUT_OF_BUSINESS_WINDOW");
        record.setUpdatePayloadJson("{\"planStatus\":{\"status\":1}}");

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        String message = pageInfo.getList().get(0).getMessage();
        assertTrue(message.contains("已关闭商家端推广"));
    }

    @Test
    void shouldRenderBudgetExhaustedNoticeMessage() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 12, 30, 1));
        record.setExecuteStatus("SKIPPED");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("PROMOTION_DAILY_BUDGET_EXHAUSTED");

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        String message = pageInfo.getList().get(0).getMessage();
        assertTrue(message.contains("今日美团推广预算已用尽，本槽位已跳过自动调价"));
    }

    @Test
    void shouldRenderReminderRecordWithoutSkippedCopy() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 12, 30, 1));
        record.setExecuteStatus("SKIPPED");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("POWER_BELOW_WARNING_THRESHOLD_REMINDER");

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        MtH5PromotionLogVO log = pageInfo.getList().get(0);
        assertEquals("提醒", log.getPrice());
        assertTrue(log.getMessage().contains("推广提醒"));
        assertTrue(log.getMessage().contains("当前算力余额已低于20000，建议尽快充值，避免后续时段AI推广中断"));
        assertTrue(!log.getMessage().contains("当前时段未执行自动调价"));
        assertTrue(log.getMarkdownMessage().contains("**推广提醒**"));
    }

    @Test
    void shouldRenderLowPeakBaselineMessage() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 15, 30));
        record.setExecuteStatus("SUCCESS");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("LOW_PEAK_BASELINE_BID");
        record.setPreviousBidAmount(new BigDecimal("0.50"));
        record.setNextBidAmount(new BigDecimal("0.40"));
        record.setLogMarkdownStatus("READY");

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        String message = pageInfo.getList().get(0).getMessage();
        assertTrue(message.contains("当前处于营业低峰期，按时段节奏控制出价"));
        assertTrue(!message.contains("低峰基准价"));
        assertTrue(pageInfo.getList().get(0).getMarkdownMessage().contains("本次出价 0.4 元"));
        assertEquals("READY", pageInfo.getList().get(0).getMarkdownStatus());
    }

    @Test
    void shouldExposeFallbackMarkdownStatusWhenAsyncFallbackPersisted() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 15, 30));
        record.setExecuteStatus("FAILED");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("REMOTE_PLAN_STATUS_CLOSED");
        record.setMarkdownMessage("fallback markdown");
        record.setLogMarkdownStatus("FALLBACK");
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setLogMarkdownSource("fallback");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        assertEquals("FALLBACK", pageInfo.getList().get(0).getMarkdownStatus());
        assertEquals("fallback markdown", pageInfo.getList().get(0).getMarkdownMessage());
    }

    @Test
    void shouldPreferPersistedMarkdownStatusOverDerivedPendingState() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 15, 30));
        record.setExecuteStatus("SUCCESS");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("LOW_PEAK_BASELINE_BID");
        record.setLogMarkdownStatus("READY");

        when(mtPromotionBidRecordMapper.selectRecentByShopId(eq(1L), any())).thenReturn(List.of(record));

        MtShop shop = new MtShop();
        shop.setId(1L);
        PageInfo<MtH5PromotionLogVO> pageInfo = mtPromotionLogService.listLogs(shop, 1, 10);

        assertEquals("READY", pageInfo.getList().get(0).getMarkdownStatus());
    }

    @Test
    void shouldReportUnreadWhenLatestVisibleLogIsAfterReadCursor() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        MtPromotionLogReadState state = new MtPromotionLogReadState();
        state.setLastReadRecordId(9L);
        when(mtPromotionBidRecordMapper.selectLatestVisibleLogIdByShopId(1L)).thenReturn(10L);
        when(mtPromotionLogReadStateMapper.selectByShopId(1L)).thenReturn(state);

        assertTrue(mtPromotionLogService.hasUnreadLogs(shop));
    }

    @Test
    void shouldMarkLatestVisibleLogRead() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        when(mtPromotionBidRecordMapper.selectLatestVisibleLogIdByShopId(1L)).thenReturn(10L);

        mtPromotionLogService.markCurrentShopLogsRead(shop);

        verify(mtPromotionLogReadStateMapper).upsertReadState(eq(1L), eq(10L), any());
    }
}
