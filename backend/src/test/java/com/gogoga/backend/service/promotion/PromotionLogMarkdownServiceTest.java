package com.gogoga.backend.service.promotion;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.MtPromotionLlmProperties;
import com.gogoga.backend.config.OpenAiCompatibleProperties;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.service.MtPromotionAdRealtimeSnapshotService;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.util.JsonUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromotionLogMarkdownServiceTest {

    @Test
    void shouldGenerateMarkdownFromModelResponse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 指标：累计花费 3.3 元，累计实收 66 元\\n- 原因：当前窗口累计费用率未超上限，保持当前出价"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, true, true);
            PromotionLogMarkdownResult result = service.generate(buildRecord());

            assertTrue(!result.isFallback());
            assertEquals(20L, result.getPromptTokens());
            assertEquals(18L, result.getCompletionTokens());
            assertEquals(38L, result.getTotalTokens());
            assertTrue(result.getMarkdownMessage().contains("自动调价执行成功"));
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(requestBody.contains("近7天累计付费曝光量700次"));
            assertTrue(requestBody.contains("上周同小时时段付费曝光量18次"));
            assertTrue(requestBody.contains("当前推广窗口累计付费曝光120次"));
            assertTrue(requestBody.contains("当前小时截止当前付费曝光25次"));
            assertTrue(requestBody.contains("当前日期2026-04-08"));
            assertTrue(requestBody.contains("当前时间12:30"));
            assertTrue(requestBody.contains("当前星期星期三"));
            assertTrue(requestBody.contains("当前日型工作日"));
            assertTrue(requestBody.contains("门店营业时间09:00-22:00"));
            assertTrue(requestBody.contains("## 门店高峰期参考"));
            assertTrue(requestBody.contains("当前日型工作日，按工作日画像参考"));
            assertTrue(requestBody.contains("历史高峰时段11:00-13:00、17:00-19:00"));
            assertTrue(requestBody.contains("当前时段属于历史高峰时段（11:00-13:00）"));
            assertTrue(!requestBody.contains("{{window_exposure_count}}"));
        }
    }

    @Test
    void shouldFallbackWhenModelReturnsCodeFence() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"```markdown\\n**自动调价执行成功**\\n```"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":10,
                                "total_tokens":30
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, true, false);
            PromotionLogMarkdownResult result = service.generate(buildRecord());

            assertTrue(result.isFallback());
            assertEquals(20L, result.getPromptTokens());
            assertEquals(10L, result.getCompletionTokens());
            assertEquals(30L, result.getTotalTokens());
            assertTrue(result.getMarkdownMessage().contains("自动调价执行成功"));
            assertTrue(result.getFailureSummary().contains("代码块"));
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(!requestBody.contains("## 门店高峰期参考"));
        }
    }

    @Test
    void shouldOmitLastWeekHourPromptLineWhenHourlyReferenceMissing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 原因：参考昨天同小时数据，当前出价保持不变"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, false);
            PromotionLogMarkdownResult result = service.generate(buildRecord());

            assertTrue(!result.isFallback());
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(!requestBody.contains("上周同小时时段付费曝光量"));
        }
    }

    @Test
    void shouldUseInitialSuccessPromptForFirstSuccessOfWindow() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 当前属于首次自动调价，先按初始出价启动"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server);
            PromotionLogMarkdownResult result = service.generate(buildInitialRecord());

            assertTrue(!result.isFallback());
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(requestBody.contains("为什么当前营业窗口首次自动调价会先按初始出价执行"));
            assertTrue(!requestBody.contains("## 结合当前数据"));
            assertTrue(!requestBody.contains("当前推广窗口累计付费曝光"));
            assertTrue(!requestBody.contains("状态说明"));
            assertTrue(!requestBody.contains("原因说明"));
            assertTrue(!requestBody.contains("场景补充"));
            assertTrue(requestBody.contains("当前日期2026-04-08"));
            assertTrue(requestBody.contains("当前时间12:30"));
            assertTrue(requestBody.contains("当前星期星期三"));
            assertTrue(requestBody.contains("当前日型工作日"));
            assertTrue(requestBody.contains("门店营业时间09:00-22:00"));
            assertTrue(requestBody.contains("## 门店高峰期参考"));
        }
    }

    @Test
    void shouldIncludeAdjustedWorkdayContextInPrompt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 当前为调休工作日，按工作日画像参考"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(
                    server,
                    true,
                    true,
                    new ShopPeakWindowDayTypeResolver(new ChinaWorkdayCalendarService(null) {
                        @Override
                        public ChinaWorkdayCalendarDay resolveDay(java.time.LocalDate date) {
                            return java.time.LocalDate.of(2026, 5, 9).equals(date)
                                    ? new ChinaWorkdayCalendarDay(ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY, "劳动节补班")
                                    : null;
                        }
                    })
            );
            PromotionLogMarkdownResult result = service.generate(buildAdjustedWorkdayRecord());

            assertTrue(!result.isFallback());
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(requestBody.contains("当前星期星期六"));
            assertTrue(requestBody.contains("当前日型调休工作日（劳动节补班）"));
            assertTrue(requestBody.contains("当前日型调休工作日（劳动节补班），按工作日画像参考"));
        }
    }

    @Test
    void shouldIncludeLowPeakBaselineContextInSuccessPrompt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 当前按系统节奏调整出价"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, true, true);
            PromotionLogMarkdownResult result = service.generate(buildLowPeakRecord());

            assertTrue(!result.isFallback());
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(requestBody.contains("0.4元竞价"));
            assertTrue(!requestBody.contains("窗口低峰基准价"));
            assertTrue(!requestBody.contains("当前定价分支"));
        }
    }

    @Test
    void shouldSendVisibleChineseReasonToExceptionPrompt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**当前时段未执行自动调价**\\n- 当前槽位推广指标不可信，已跳过自动调价"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, true, true);
            PromotionLogMarkdownResult result = service.generate(buildSkippedRecord());

            assertTrue(!result.isFallback());
            String requestBody = server.takeRequest().getBody().readUtf8();
            assertTrue(requestBody.contains("执行状态未执行"));
            assertTrue(requestBody.contains("原因说明当前槽位推广指标不可信，已跳过自动调价"));
            assertTrue(!requestBody.contains("SKIPPED"));
            assertTrue(!requestBody.contains("PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING"));
            assertTrue(!requestBody.contains("{{"));
        }
    }

    @Test
    void shouldFallbackWhenModelReturnsUnresolvedPlaceholder() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "id":"chatcmpl-test",
                              "object":"chat.completion",
                              "created":1710000000,
                              "model":"test-model",
                              "choices":[{
                                "index":0,
                                "finish_reason":"stop",
                                "message":{
                                  "role":"assistant",
                                  "content":"**自动调价执行成功**\\n- 原因：{{decision_reason_text}}"
                                }
                              }],
                              "usage":{
                                "prompt_tokens":20,
                                "completion_tokens":18,
                                "total_tokens":38
                              }
                            }
                            """));

            PromotionLogMarkdownService service = buildService(server, true, false);
            PromotionLogMarkdownResult result = service.generate(buildRecord());

            assertTrue(result.isFallback());
            assertTrue(result.getFailureSummary().contains("未替换占位符"));
            assertTrue(!result.getMarkdownMessage().contains("{{decision_reason_text}}"));
        }
    }

    private PromotionLogMarkdownService buildService(MockWebServer server) {
        return buildService(server, true, true);
    }

    private PromotionLogMarkdownService buildService(MockWebServer server, boolean includeLastWeekHour) {
        return buildService(server, includeLastWeekHour, true);
    }

    private PromotionLogMarkdownService buildService(
            MockWebServer server,
            boolean includeLastWeekHour,
            boolean includePeakProfile
    ) {
        return buildService(server, includeLastWeekHour, includePeakProfile, new ShopPeakWindowDayTypeResolver());
    }

    private PromotionLogMarkdownService buildService(
            MockWebServer server,
            boolean includeLastWeekHour,
            boolean includePeakProfile,
            ShopPeakWindowDayTypeResolver dayTypeResolver
    ) {
        OpenAiCompatibleProperties openAiProperties = new OpenAiCompatibleProperties();
        openAiProperties.setDefaultBaseUrl(server.url("/").toString());
        openAiProperties.setDefaultApiKey("test-key");
        OpenAiCompatibleChatService chatService = new OpenAiCompatibleChatService(
                openAiProperties,
                new OpenAiCompatibleClientFactory()
        );

        MtPromotionLlmProperties llmProperties = new MtPromotionLlmProperties();
        llmProperties.setModel("test-model");
        llmProperties.setSuccessLogPromptResource("prompts/promotion-log-success-prompt.txt");
        llmProperties.setInitialSuccessLogPromptResource("prompts/promotion-log-initial-success-prompt.txt");
        llmProperties.setExceptionLogPromptResource("prompts/promotion-log-exception-prompt.txt");
        MtPromotionAdDailyMapper dailyMapper = mock(MtPromotionAdDailyMapper.class);
        MtPromotionAdDaily daily = new MtPromotionAdDaily();
        daily.setShowCount(700);
        daily.setClickCount(90);
        daily.setDebitCents(5600L);
        when(dailyMapper.sumByShopIdAndBizDateRangeAndChannel(any(), any(), any(), any())).thenReturn(daily);

        MtPromotionAdHourlyDetailMapper hourlyDetailMapper = mock(MtPromotionAdHourlyDetailMapper.class);
        MtPromotionAdHourlyDetail yesterday = new MtPromotionAdHourlyDetail();
        yesterday.setShowCount(20);
        yesterday.setClickCount(3);
        yesterday.setDebitCents(180L);
        if (includeLastWeekHour) {
            MtPromotionAdHourlyDetail lastWeek = new MtPromotionAdHourlyDetail();
            lastWeek.setShowCount(18);
            lastWeek.setClickCount(2);
            lastWeek.setDebitCents(150L);
            when(hourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(eq(1L), any(), any(), any()))
                    .thenReturn(yesterday)
                    .thenReturn(lastWeek);
        } else {
            when(hourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(eq(1L), any(), any(), any()))
                    .thenReturn(yesterday)
                    .thenReturn(null);
        }

        MtPromotionAdRealtimeSnapshotService realtimeSnapshotService = mock(MtPromotionAdRealtimeSnapshotService.class);
        MtPromotionAdRealtimeSnapshot current = new MtPromotionAdRealtimeSnapshot();
        current.setShopId(1L);
        current.setBizDate(buildRecord().getSlotTime().toLocalDate());
        current.setSlotTime(buildRecord().getSlotTime());
        current.setHourShowCount(25);
        current.setHourClickCount(4);
        current.setHourDebitCents(220L);
        current.setDayShowCount(320);
        current.setDayClickCount(36);
        current.setDayDebitCents(6600L);
        MtPromotionAdRealtimeSnapshot baseline = new MtPromotionAdRealtimeSnapshot();
        baseline.setShopId(1L);
        baseline.setBizDate(buildRecord().getSlotTime().toLocalDate());
        baseline.setSlotTime(buildRecord().getWindowStartAt());
        baseline.setDayShowCount(200);
        baseline.setDayClickCount(20);
        baseline.setDayDebitCents(3300L);
        when(realtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1L), eq(buildRecord().getSlotTime().toLocalDate()), eq(buildRecord().getSlotTime())))
                .thenReturn(current);
        when(realtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                eq(1L), eq(buildRecord().getWindowStartAt().toLocalDate()), eq(buildRecord().getWindowStartAt())))
                .thenReturn(baseline);

        MtOrderMapper orderMapper = mock(MtOrderMapper.class);
        when(orderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(1L), eq(MtOrderStatusEnum.CANCELED.getCode()), any(), any()))
                .thenReturn(6)
                .thenReturn(2);
        MtShopService mtShopService = mock(MtShopService.class);
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setShippingTime("09:00-22:00");
        when(mtShopService.findById(1L)).thenReturn(shop);
        MtShopPeakWindowProfileMapper shopPeakWindowProfileMapper = mock(MtShopPeakWindowProfileMapper.class);
        if (includePeakProfile) {
            MtShopPeakWindowProfile profile = new MtShopPeakWindowProfile();
            profile.setShopId(1L);
            profile.setDayType(ShopPeakDayType.WEEKDAY.name());
            profile.setProfileStatus(ShopPeakWindowProfileService.PROFILE_STATUS_USABLE);
            profile.setLookbackDays(21);
            profile.setSampleDayCount(8);
            profile.setSampleOrderCount(126);
            profile.setPeakWindowsJson("""
                    [
                      {"startHour":11,"endHour":13,"startTime":"11:00","endTime":"13:00","orderCount":72},
                      {"startHour":17,"endHour":19,"startTime":"17:00","endTime":"19:00","orderCount":54}
                    ]
                    """);
            when(shopPeakWindowProfileMapper.selectByShopIdAndDayType(eq(1L), eq(ShopPeakDayType.WEEKDAY.name())))
                    .thenReturn(profile);
        }
        return new PromotionLogMarkdownService(
                llmProperties,
                new PromptTemplateRenderer(),
                chatService,
                new PromotionLogMessageBuilder(),
                dailyMapper,
                hourlyDetailMapper,
                realtimeSnapshotService,
                orderMapper,
                mtShopService,
                shopPeakWindowProfileMapper,
                dayTypeResolver
        );
    }

    private MtPromotionBidRecord buildRecord() {
        MtPromotionBidRecord record = new MtPromotionBidRecord();
        record.setShopId(1L);
        record.setSlotTime(LocalDateTime.of(2026, 4, 8, 12, 30));
        record.setWindowStartAt(LocalDateTime.of(2026, 4, 8, 12, 0));
        record.setExecuteStatus("SUCCESS");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("CLICK_DETECTED_KEEP_PREVIOUS_BID");
        record.setPreviousBidAmount(new BigDecimal("0.40"));
        record.setNextBidAmount(new BigDecimal("0.40"));
        record.setTotalCost(new BigDecimal("3.30"));
        record.setTotalIncome(new BigDecimal("66.00"));
        record.setTotalCostRatio(new BigDecimal("0.050000"));
        record.setCostDelta(new BigDecimal("0.30"));
        return record;
    }

    private MtPromotionBidRecord buildInitialRecord() {
        MtPromotionBidRecord record = buildRecord();
        record.setDecisionAction("INIT");
        record.setDecisionReason("FIRST_SUCCESS_OF_WINDOW");
        record.setPreviousBidAmount(null);
        record.setNextBidAmount(new BigDecimal("0.20"));
        record.setTotalCost(BigDecimal.ZERO.setScale(2));
        record.setTotalIncome(BigDecimal.ZERO.setScale(2));
        record.setTotalCostRatio(BigDecimal.ZERO.setScale(6));
        record.setCostDelta(BigDecimal.ZERO.setScale(2));
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setMetricsBusinessWindowStartAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        detail.setMetricsEffectiveWindowStartAt(LocalDateTime.of(2026, 4, 8, 12, 30));
        detail.setTemporaryRecoveryWindow(true);
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildLowPeakRecord() {
        MtPromotionBidRecord record = buildRecord();
        record.setDecisionReason("LOW_PEAK_BASELINE_BID");
        record.setPreviousBidAmount(new BigDecimal("0.50"));
        record.setNextBidAmount(new BigDecimal("0.40"));
        PromotionDecisionDetail detail = new PromotionDecisionDetail();
        detail.setWindowBaselineBidAmount(new BigDecimal("0.40"));
        detail.setPricingBranch("LOW_PEAK_BASELINE");
        detail.setMetricsBusinessWindowStartAt(LocalDateTime.of(2026, 4, 8, 10, 0));
        detail.setMetricsEffectiveWindowStartAt(LocalDateTime.of(2026, 4, 8, 12, 0));
        record.setDecisionDetailJson(JsonUtil.toJson(detail));
        return record;
    }

    private MtPromotionBidRecord buildAdjustedWorkdayRecord() {
        MtPromotionBidRecord record = buildRecord();
        record.setSlotTime(LocalDateTime.of(2026, 5, 9, 12, 30));
        record.setWindowStartAt(LocalDateTime.of(2026, 5, 9, 12, 0));
        return record;
    }

    private MtPromotionBidRecord buildSkippedRecord() {
        MtPromotionBidRecord record = buildRecord();
        record.setExecuteStatus("SKIPPED");
        record.setDecisionAction("KEEP");
        record.setDecisionReason("UNTRUSTED_PROMOTION_METRICS:PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING");
        record.setNextBidAmount(null);
        return record;
    }
}
