package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import com.gogoga.backend.util.JsonUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * 推广日志 Markdown 真实模型手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. LLM_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. LLM_OPENAI_COMPATIBLE_DEFAULT_BASE_URL=兼容服务 baseUrl，例如 https://api.lkeap.cloud.tencent.com/v1</p>
 * <p>3. LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY=你的服务 apiKey</p>
 * <p>4. LLM_TEST_MODEL=实际可用模型名</p>
 *
 * <p>执行命令：</p>
 * <p>/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=PromotionLogMarkdownManualTest test</p>
 */
@SpringBootTest(properties = {
        "mt.promotion.llm.model=${LLM_TEST_MODEL:}"
})
class PromotionLogMarkdownManualTest {

    private static final Long SHOP_ID = 9527L;
    private static final LocalDateTime SLOT_TIME = LocalDateTime.of(2026, 4, 8, 12, 30);
    private static final LocalDateTime WINDOW_START_AT = LocalDateTime.of(2026, 4, 8, 12, 0);
    private static final String CURRENT_HOUR_RANGE = "12:00-13:00";

    @Autowired
    private PromotionLogMarkdownService promotionLogMarkdownService;

    @MockitoBean
    private MtPromotionAdDailyMapper mtPromotionAdDailyMapper;

    @MockitoBean
    private MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;

    @MockitoBean
    private MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;

    @MockitoBean
    private MtOrderMapper mtOrderMapper;

    @BeforeEach
    void setUpMocks() {
        reset(mtPromotionAdDailyMapper, mtPromotionAdHourlyDetailMapper, mtPromotionAdRealtimeSnapshotService, mtOrderMapper);
        mockSharedBusinessData();
    }

    @Test
    void shouldGenerateReadableMarkdownFromLiveModel() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("LLM_TEST_ENABLE_LIVE_API")),
                "跳过真实日志模型测试，设置 LLM_TEST_ENABLE_LIVE_API=true 后再执行。");
        requireEnv("LLM_TEST_MODEL");

        List<TestCase> cases = List.of(
                new TestCase("success_keep", buildSuccessKeepRecord()),
                new TestCase("success_decrease", buildSuccessDecreaseRecord()),
                new TestCase("skipped_budget_exhausted", buildSkippedBudgetRecord()),
                new TestCase("failed_remote_error", buildFailedRecord())
        );

        System.out.println("caseName|fallback|tokens|failureSummary|markdown");
        for (TestCase testCase : cases) {
            PromotionLogMarkdownResult result = promotionLogMarkdownService.generate(testCase.record);
            assertNotNull(result, "日志模型结果不能为空");
            assertTrue(result.getMarkdownMessage() != null && !result.getMarkdownMessage().isBlank(),
                    () -> "日志 Markdown 不能为空, case=" + testCase.name);
            assertTrue(!result.isFallback(),
                    () -> "日志模型回退到了兜底文案, case=" + testCase.name
                            + ", failureSummary=" + result.getFailureSummary()
                            + ", rawResponse=" + result.getRawResponseSummary());

            System.out.printf("%s|%s|%s|%s|%s%n",
                    testCase.name,
                    result.isFallback(),
                    result.getTotalTokens(),
                    safe(result.getFailureSummary()),
                    sanitize(result.getMarkdownMessage()));
        }
    }

    private MtPromotionBidRecord buildSuccessKeepRecord() {
        MtPromotionBidRecord record = baseRecord("SUCCESS", "KEEP", "CLICK_DETECTED_KEEP_PREVIOUS_BID");
        record.setPreviousBidAmount(new BigDecimal("0.40"));
        record.setNextBidAmount(new BigDecimal("0.40"));
        record.setTotalCost(new BigDecimal("3.30"));
        record.setTotalIncome(new BigDecimal("66.00"));
        record.setTotalCostRatio(new BigDecimal("0.050000"));
        record.setCostDelta(new BigDecimal("0.30"));

        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode("rule");
        detail.setDecisionSource("rule");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildSuccessDecreaseRecord() {
        MtPromotionBidRecord record = baseRecord("SUCCESS", "DECREASE", "TOTAL_COST_RATIO_ABOVE_MAX");
        record.setPreviousBidAmount(new BigDecimal("0.40"));
        record.setNextBidAmount(new BigDecimal("0.30"));
        record.setTotalCost(new BigDecimal("5.40"));
        record.setTotalIncome(new BigDecimal("53.47"));
        record.setTotalCostRatio(new BigDecimal("0.100991"));
        record.setCostDelta(new BigDecimal("0.30"));

        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setDecisionMode("rule");
        detail.setDecisionSource("rule");
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildSkippedBudgetRecord() {
        MtPromotionBidRecord record = baseRecord("SKIPPED", "KEEP", "PROMOTION_DAILY_BUDGET_EXHAUSTED");
        record.setUpdatePayloadJson("{\"statusCheck\":{\"status\":0}}");
        return record;
    }

    private MtPromotionBidRecord buildFailedRecord() {
        MtPromotionBidRecord record = baseRecord("FAILED", "KEEP", "REMOTE_API_ERROR");
        record.setErrorMsg("调用美团更新出价失败：请求超时");
        record.setPreviousBidAmount(new BigDecimal("0.40"));
        record.setNextBidAmount(new BigDecimal("0.40"));
        record.setTotalCost(new BigDecimal("2.50"));
        record.setTotalIncome(new BigDecimal("50.00"));
        record.setTotalCostRatio(new BigDecimal("0.050000"));
        record.setCostDelta(new BigDecimal("0.20"));
        return record;
    }

    private MtPromotionBidRecord baseRecord(String executeStatus, String decisionAction, String decisionReason) {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setShopId(SHOP_ID);
        record.setSlotTime(SLOT_TIME);
        record.setWindowStartAt(WINDOW_START_AT);
        record.setExecuteStatus(executeStatus);
        record.setDecisionAction(decisionAction);
        record.setDecisionReason(decisionReason);
        return record;
    }

    private void mockSharedBusinessData() {
        MtPromotionAdDaily sevenDaySummary = new MtPromotionAdDaily();
        sevenDaySummary.setShowCount(1386);
        sevenDaySummary.setClickCount(164);
        sevenDaySummary.setDebitCents(9860L);
        when(mtPromotionAdDailyMapper.sumByShopIdAndBizDateRangeAndChannel(
                eq(SHOP_ID),
                eq(LocalDate.of(2026, 4, 1)),
                eq(LocalDate.of(2026, 4, 7)),
                eq(1)
        )).thenReturn(sevenDaySummary);

        MtPromotionAdHourlyDetail yesterdayHour = new MtPromotionAdHourlyDetail();
        yesterdayHour.setShowCount(36);
        yesterdayHour.setClickCount(5);
        yesterdayHour.setDebitCents(280L);
        MtPromotionAdHourlyDetail lastWeekHour = new MtPromotionAdHourlyDetail();
        lastWeekHour.setShowCount(31);
        lastWeekHour.setClickCount(4);
        lastWeekHour.setDebitCents(240L);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(
                eq(SHOP_ID), eq(LocalDate.of(2026, 4, 7)), eq(1), eq(CURRENT_HOUR_RANGE)
        )).thenReturn(yesterdayHour);
        when(mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(
                eq(SHOP_ID), eq(LocalDate.of(2026, 4, 1)), eq(1), eq(CURRENT_HOUR_RANGE)
        )).thenReturn(lastWeekHour);

        MtPromotionAdRealtimeSnapshot currentSnapshot = new MtPromotionAdRealtimeSnapshot();
        currentSnapshot.setShopId(SHOP_ID);
        currentSnapshot.setBizDate(SLOT_TIME.toLocalDate());
        currentSnapshot.setSlotTime(SLOT_TIME);
        currentSnapshot.setCurrentHourRange(CURRENT_HOUR_RANGE);
        currentSnapshot.setHourShowCount(28);
        currentSnapshot.setHourClickCount(4);
        currentSnapshot.setHourDebitCents(220L);
        currentSnapshot.setDayShowCount(356);
        currentSnapshot.setDayClickCount(41);
        currentSnapshot.setDayDebitCents(5520L);
        MtPromotionAdRealtimeSnapshot baselineSnapshot = new MtPromotionAdRealtimeSnapshot();
        baselineSnapshot.setShopId(SHOP_ID);
        baselineSnapshot.setBizDate(WINDOW_START_AT.toLocalDate());
        baselineSnapshot.setSlotTime(WINDOW_START_AT);
        baselineSnapshot.setDayShowCount(210);
        baselineSnapshot.setDayClickCount(24);
        baselineSnapshot.setDayDebitCents(3300L);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(SHOP_ID), eq(SLOT_TIME.toLocalDate()), eq(SLOT_TIME)
        )).thenReturn(currentSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(SHOP_ID), eq(WINDOW_START_AT.toLocalDate()), eq(WINDOW_START_AT)
        )).thenReturn(baselineSnapshot);
        when(mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(any(), any(), any()))
                .thenReturn(null);

        when(mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
                eq(SHOP_ID), eq(MtOrderStatusEnum.CANCELED.getCode()), any(), any()
        )).thenReturn(7).thenReturn(2).thenReturn(7).thenReturn(2);
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : sanitize(value);
    }

    private String sanitize(String value) {
        return value.replace("\r", " ").replace("\n", "\\n");
    }

    private static class TestCase {

        private final String name;
        private final MtPromotionBidRecord record;

        private TestCase(String name, MtPromotionBidRecord record) {
            this.name = name;
            this.record = record;
        }
    }
}
