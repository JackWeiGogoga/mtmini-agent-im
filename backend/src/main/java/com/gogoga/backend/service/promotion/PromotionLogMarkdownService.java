package com.gogoga.backend.service.promotion;

import com.gogoga.backend.config.MtPromotionLlmProperties;
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
import com.openai.models.completions.CompletionUsage;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 推广日志 Markdown 生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionLogMarkdownService {

    static final int MAX_MARKDOWN_LENGTH = 600;
    private static final int RAW_RESPONSE_SUMMARY_LIMIT = 500;
    private static final int CHANNEL_DIANJIN = 1;
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}|\\$\\{[^{}]+}");

    private final MtPromotionLlmProperties properties;
    private final PromptTemplateRenderer promptTemplateRenderer;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final PromotionLogMessageBuilder promotionLogMessageBuilder;
    private final MtPromotionAdDailyMapper mtPromotionAdDailyMapper;
    private final MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;
    private final MtPromotionAdRealtimeSnapshotService mtPromotionAdRealtimeSnapshotService;
    private final MtOrderMapper mtOrderMapper;
    private final MtShopService mtShopService;
    private final MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;
    private final ShopPeakWindowDayTypeResolver shopPeakWindowDayTypeResolver;

    public PromotionLogMarkdownResult generate(MtPromotionBidRecord record) {
        PromotionLogMarkdownResult result = new PromotionLogMarkdownResult();
        String fallbackMarkdown = promotionLogMessageBuilder.buildFallbackMarkdown(record);
        if (record == null) {
            result.setFallback(true);
            result.setMarkdownMessage(fallbackMarkdown);
            result.setFailureSummary("RECORD_MISSING");
            return result;
        }
        if (StringUtils.isBlank(properties.getModel())) {
            result.setFallback(true);
            result.setMarkdownMessage(fallbackMarkdown);
            result.setFailureSummary("LOG_MODEL_NOT_CONFIGURED");
            return result;
        }

        long startTime = System.currentTimeMillis();
        try {
            String prompt = buildPrompt(record);
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(properties.getModel())
                    .addUserMessage(prompt)
                    .build();
            ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(params);
            CompletionUsage usage = extractUsage(completion);
            result.setPromptTokens(usage.promptTokens());
            result.setCompletionTokens(usage.completionTokens());
            result.setTotalTokens(usage.totalTokens());
            String content = extractContent(completion);
            result.setRawResponseSummary(StringUtils.left(content, RAW_RESPONSE_SUMMARY_LIMIT));
            validateMarkdown(content);
            result.setMarkdownMessage(content.trim());
            result.setFallback(false);
            log.info("推广日志大模型生成完成，shopId={}, slotTime={}, executeStatus={}, model={}, durationMs={}, totalTokens={}",
                    record.getShopId(),
                    record.getSlotTime(),
                    record.getExecuteStatus(),
                    properties.getModel(),
                    System.currentTimeMillis() - startTime,
                    result.getTotalTokens());
            return result;
        } catch (Exception e) {
            result.setFallback(true);
            result.setMarkdownMessage(fallbackMarkdown);
            result.setFailureSummary(StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), "LOG_GENERATION_FAILED"), 300));
            log.warn("推广日志大模型生成失败，shopId={}, slotTime={}, executeStatus={}, model={}, durationMs={}, reason={}",
                    record.getShopId(),
                    record.getSlotTime(),
                    record.getExecuteStatus(),
                    properties.getModel(),
                    System.currentTimeMillis() - startTime,
                    result.getFailureSummary());
            return result;
        }
    }

    private String buildPrompt(MtPromotionBidRecord record) {
        return promptTemplateRenderer.render(resolvePromptResource(record), buildPromptValues(record));
    }

    private String resolvePromptResource(MtPromotionBidRecord record) {
        String executeStatus = record == null ? null : record.getExecuteStatus();
        if ("SUCCESS".equals(executeStatus)) {
            if (isInitialSuccessRecord(record)) {
                return properties.getInitialSuccessLogPromptResource();
            }
            return properties.getSuccessLogPromptResource();
        }
        return properties.getExceptionLogPromptResource();
    }

    private Map<String, String> buildPromptValues(MtPromotionBidRecord record) {
        Map<String, String> values = new LinkedHashMap<>();
        LocalDateTime slotTime = record.getSlotTime();
        PromotionDecisionDetail detail = resolveDecisionDetail(record);
        String currentHourRange = resolveCurrentHourRange(slotTime);
        HistoricalFeatures historicalFeatures = loadHistoricalFeatures(record.getShopId(), slotTime, currentHourRange);
        CurrentRealtimeFeatures currentRealtimeFeatures = loadCurrentRealtimeFeatures(record, currentHourRange);
        WindowRealtimeFeatures windowRealtimeFeatures = loadWindowRealtimeFeatures(record, currentRealtimeFeatures.snapshot());

        values.put("execute_status", StringUtils.defaultString(
                promotionLogMessageBuilder.resolveExecuteStatusText(record.getExecuteStatus())));
        values.put("status_text", StringUtils.defaultString(promotionLogMessageBuilder.resolveStatusText(record)));
        values.put("decision_reason_text", StringUtils.defaultString(
                promotionLogMessageBuilder.resolveDecisionReasonText(record.getDecisionReason())));
        values.put("error_msg", StringUtils.defaultIfBlank(promotionLogMessageBuilder.resolveErrorText(record.getErrorMsg()), "无"));
        values.put("current_date", formatDate(slotTime));
        values.put("current_time", formatTime(slotTime));
        values.put("current_weekday", resolveChineseWeekday(slotTime));
        values.put("current_day_type", resolveCurrentDayTypeText(slotTime));
        values.put("shop_shipping_time", loadShopShippingTime(record.getShopId()));
        values.put("peak_window_prompt_section", buildPeakWindowPromptSection(record));
        values.put("7d_exposure_count", String.valueOf(historicalFeatures.sevenDayExposureCount()));
        values.put("7d_click_count", String.valueOf(historicalFeatures.sevenDayClickCount()));
        values.put("7d_promotion_cost", formatMoney(yuanFromCents(historicalFeatures.sevenDayPromotionCostCents())));
        values.put("lastweek_hour_summary", buildLastWeekHourSummary(historicalFeatures));
        values.put("historical_compare_hint", historicalFeatures.hasLastWeekHourReference() ? "和上周同小时" : "");
        values.put("yesterday_hour_exposure_count", String.valueOf(historicalFeatures.yesterdayHourExposureCount()));
        values.put("yesterday_hour_click_count", String.valueOf(historicalFeatures.yesterdayHourClickCount()));
        values.put("yesterday_hour_promotion_cost", formatMoney(yuanFromCents(historicalFeatures.yesterdayHourPromotionCostCents())));
        values.put("window_exposure_count", String.valueOf(windowRealtimeFeatures.windowExposureCount()));
        values.put("window_click_count", String.valueOf(windowRealtimeFeatures.windowClickCount()));
        values.put("window_order_count", String.valueOf(countOrders(record.getShopId(), record.getWindowStartAt(), slotTime)));
        values.put("window_promotion_cost", formatMoney(defaultMoney(record.getTotalCost())));
        values.put("current_hour_exposure_count", String.valueOf(currentRealtimeFeatures.hourExposureCount()));
        values.put("current_hour_click_count", String.valueOf(currentRealtimeFeatures.hourClickCount()));
        values.put("current_hour_order_count", String.valueOf(countOrders(record.getShopId(), slotTime.withMinute(0).withSecond(0).withNano(0), slotTime)));
        values.put("current_hour_promotion_cost", formatMoney(yuanFromCents(currentRealtimeFeatures.hourPromotionCostCents())));
        values.put("bid", formatMoney(resolveBid(record)));
        values.put("business_window_start_at", formatDateTime(detail == null ? null : detail.getMetricsBusinessWindowStartAt()));
        values.put("effective_window_start_at", formatDateTime(detail == null ? null : detail.getMetricsEffectiveWindowStartAt()));
        values.put("is_temporary_recovery_window", resolveYesNo(detail != null && Boolean.TRUE.equals(detail.getTemporaryRecoveryWindow())));
        values.put("initial_scene_text", resolveInitialSceneText(detail));
        return values;
    }

    private String buildPeakWindowPromptSection(MtPromotionBidRecord record) {
        PeakWindowPromptFeatures features = loadPeakWindowPromptFeatures(record == null ? null : record.getShopId(),
                record == null ? null : record.getSlotTime());
        if (features == null) {
            return "";
        }
        return String.format(
                "## 门店高峰期参考%n当前日型%s，按%s画像参考，近%s天样本%s天、%s单，历史高峰时段%s%n当前时段%s%n",
                features.currentDayTypeText(),
                features.profileDayTypeText(),
                features.lookbackDays(),
                features.sampleDayCount(),
                features.sampleOrderCount(),
                features.windowRangesText(),
                features.currentSlotStatusText()
        );
    }

    private PeakWindowPromptFeatures loadPeakWindowPromptFeatures(Long shopId, LocalDateTime slotTime) {
        if (shopId == null || slotTime == null || mtShopPeakWindowProfileMapper == null || shopPeakWindowDayTypeResolver == null) {
            return null;
        }
        ShopPeakDayType currentDayType = shopPeakWindowDayTypeResolver.resolve(slotTime);
        String currentDayTypeText = resolveCurrentDayTypeText(slotTime);
        MtShopPeakWindowProfile profile = null;
        ShopPeakDayType profileDayType = null;
        for (ShopPeakDayType fallbackDayType : resolvePeakWindowFallbackChain(currentDayType)) {
            MtShopPeakWindowProfile currentProfile = mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(shopId, fallbackDayType.name());
            if (currentProfile == null || !ShopPeakWindowProfileLookupResult.STATUS_USABLE.equals(currentProfile.getProfileStatus())) {
                continue;
            }
            profile = currentProfile;
            profileDayType = fallbackDayType;
            break;
        }
        if (profile == null) {
            return null;
        }
        List<ShopPeakWindow> windows = JsonUtil.toList(profile.getPeakWindowsJson(), ShopPeakWindow.class);
        if (windows == null || windows.isEmpty()) {
            return null;
        }
        String matchedRange = windows.stream()
                .filter(window -> window != null && window.matches(slotTime.toLocalTime()))
                .map(ShopPeakWindow::toDisplayText)
                .findFirst()
                .orElse(null);
        String windowRangesText = windows.stream()
                .filter(window -> window != null && StringUtils.isNotBlank(window.toDisplayText()))
                .map(ShopPeakWindow::toDisplayText)
                .collect(Collectors.joining("、"));
        if (StringUtils.isBlank(windowRangesText)) {
            return null;
        }
        String currentSlotStatusText = StringUtils.isNotBlank(matchedRange)
                ? "属于历史高峰时段（" + matchedRange + "）"
                : "当前不在历史高峰时段内";
        return new PeakWindowPromptFeatures(
                currentDayTypeText,
                resolvePeakDayTypeText(profileDayType),
                profile.getLookbackDays() == null ? 0 : profile.getLookbackDays(),
                profile.getSampleDayCount() == null ? 0 : profile.getSampleDayCount(),
                profile.getSampleOrderCount() == null ? 0 : profile.getSampleOrderCount(),
                windowRangesText,
                currentSlotStatusText
        );
    }

    private List<ShopPeakDayType> resolvePeakWindowFallbackChain(ShopPeakDayType dayType) {
        if (dayType == ShopPeakDayType.HOLIDAY) {
            return Arrays.asList(ShopPeakDayType.HOLIDAY, ShopPeakDayType.WEEKEND, ShopPeakDayType.WEEKDAY);
        }
        if (dayType == ShopPeakDayType.WEEKEND) {
            return Arrays.asList(ShopPeakDayType.WEEKEND, ShopPeakDayType.WEEKDAY);
        }
        return List.of(ShopPeakDayType.WEEKDAY);
    }

    private String resolvePeakDayTypeText(ShopPeakDayType dayType) {
        if (dayType == null) {
            return "工作日";
        }
        return switch (dayType) {
            case HOLIDAY -> "节假日";
            case WEEKEND -> "周末";
            case WEEKDAY -> "工作日";
        };
    }

    private String resolveCurrentDayTypeText(LocalDateTime slotTime) {
        if (slotTime == null || shopPeakWindowDayTypeResolver == null) {
            return "工作日";
        }
        ChinaWorkdayCalendarService.ChinaWorkdayCalendarDay calendarDay =
                shopPeakWindowDayTypeResolver.resolveCalendarDay(slotTime.toLocalDate());
        ChinaWorkdayCalendarDayType calendarDayType = calendarDay == null ? null : calendarDay.dayType();
        String holidayName = calendarDay == null ? null : calendarDay.holidayName();
        if (calendarDayType == ChinaWorkdayCalendarDayType.HOLIDAY) {
            return StringUtils.isBlank(holidayName) ? "节假日" : "节假日（" + holidayName + "）";
        }
        if (calendarDayType == ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY) {
            return StringUtils.isBlank(holidayName) ? "调休工作日" : "调休工作日（" + holidayName + "）";
        }
        return resolvePeakDayTypeText(shopPeakWindowDayTypeResolver.resolve(slotTime));
    }

    private boolean isInitialSuccessRecord(MtPromotionBidRecord record) {
        return record != null
                && "SUCCESS".equals(record.getExecuteStatus())
                && "FIRST_SUCCESS_OF_WINDOW".equals(record.getDecisionReason());
    }

    private PromotionDecisionDetail resolveDecisionDetail(MtPromotionBidRecord record) {
        if (record == null || StringUtils.isBlank(record.getDecisionDetailJson())) {
            return null;
        }
        return com.gogoga.backend.util.JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
    }

    private String resolveInitialSceneText(PromotionDecisionDetail detail) {
        if (detail != null && Boolean.TRUE.equals(detail.getTemporaryRecoveryWindow())) {
            return "本次属于营业中途恢复后的首次自动调价，当前窗口统计从恢复时间重新开始，窗口累计数据为0或接近0属于正常现象。";
        }
        return "本次属于当前营业窗口首次自动调价，窗口刚开始执行时当前窗口累计数据为0或接近0属于正常现象。";
    }

    private String resolveYesNo(boolean value) {
        return value ? "是" : "否";
    }

    private String buildLastWeekHourSummary(HistoricalFeatures historicalFeatures) {
        if (historicalFeatures == null || !historicalFeatures.hasLastWeekHourReference()) {
            return "";
        }
        return String.format(
                "上周同小时时段付费曝光量%d次，点击量%d次，推广花费%s元%n",
                historicalFeatures.lastWeekHourExposureCount(),
                historicalFeatures.lastWeekHourClickCount(),
                formatMoney(yuanFromCents(historicalFeatures.lastWeekHourPromotionCostCents()))
        );
    }

    private HistoricalFeatures loadHistoricalFeatures(Long shopId, LocalDateTime slotTime, String currentHourRange) {
        if (shopId == null || slotTime == null) {
            return HistoricalFeatures.empty();
        }
        LocalDate currentDate = slotTime.toLocalDate();
        LocalDate historyStart = currentDate.minusDays(7);
        LocalDate historyEnd = currentDate.minusDays(1);
        MtPromotionAdDaily sevenDaySummary = historyEnd.isBefore(historyStart)
                ? null
                : mtPromotionAdDailyMapper.sumByShopIdAndBizDateRangeAndChannel(
                shopId, historyStart, historyEnd, CHANNEL_DIANJIN);
        MtPromotionAdHourlyDetail yesterdayHour = mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(
                shopId,
                currentDate.minusDays(1),
                CHANNEL_DIANJIN,
                currentHourRange
        );
        MtPromotionAdHourlyDetail lastWeekHour = mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannelAndTimeRange(
                shopId,
                currentDate.minusWeeks(1),
                CHANNEL_DIANJIN,
                currentHourRange
        );
        return new HistoricalFeatures(
                safeInt(sevenDaySummary == null ? null : sevenDaySummary.getShowCount()),
                safeInt(sevenDaySummary == null ? null : sevenDaySummary.getClickCount()),
                safeLong(sevenDaySummary == null ? null : sevenDaySummary.getDebitCents()),
                lastWeekHour != null,
                safeInt(lastWeekHour == null ? null : lastWeekHour.getShowCount()),
                safeInt(lastWeekHour == null ? null : lastWeekHour.getClickCount()),
                safeLong(lastWeekHour == null ? null : lastWeekHour.getDebitCents()),
                safeInt(yesterdayHour == null ? null : yesterdayHour.getShowCount()),
                safeInt(yesterdayHour == null ? null : yesterdayHour.getClickCount()),
                safeLong(yesterdayHour == null ? null : yesterdayHour.getDebitCents())
        );
    }

    private CurrentRealtimeFeatures loadCurrentRealtimeFeatures(MtPromotionBidRecord record, String currentHourRange) {
        if (record.getShopId() == null || record.getSlotTime() == null) {
            return new CurrentRealtimeFeatures(0, 0, 0L, null);
        }
        MtPromotionAdRealtimeSnapshot snapshot = mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                record.getShopId(),
                record.getSlotTime().toLocalDate(),
                record.getSlotTime()
        );
        if (snapshot == null) {
            return new CurrentRealtimeFeatures(0, 0, 0L, null);
        }
        return new CurrentRealtimeFeatures(
                safeInt(snapshot.getHourShowCount()),
                safeInt(snapshot.getHourClickCount()),
                safeLong(snapshot.getHourDebitCents()),
                snapshot
        );
    }

    private WindowRealtimeFeatures loadWindowRealtimeFeatures(MtPromotionBidRecord record, MtPromotionAdRealtimeSnapshot currentSnapshot) {
        if (record.getWindowStartAt() == null || record.getSlotTime() == null || currentSnapshot == null) {
            return WindowRealtimeFeatures.empty();
        }
        LocalDateTime windowStartAt = record.getWindowStartAt();
        LocalDateTime slotTime = record.getSlotTime();
        MtPromotionAdRealtimeSnapshot exactBaseline = mtPromotionAdRealtimeSnapshotService.selectByShopIdAndBizDateAndSlotTime(
                record.getShopId(),
                windowStartAt.toLocalDate(),
                windowStartAt
        );
        if (!windowStartAt.toLocalDate().isBefore(slotTime.toLocalDate())) {
            return subtractSameDayCounters(currentSnapshot, exactBaseline);
        }

        LocalDateTime currentDayStartAt = slotTime.toLocalDate().atStartOfDay();
        MtPromotionAdRealtimeSnapshot previousDayLastSnapshot =
                mtPromotionAdRealtimeSnapshotService.selectLatestByShopIdAndSlotTimeRange(
                        record.getShopId(),
                        windowStartAt,
                        currentDayStartAt
                );
        if (exactBaseline != null && previousDayLastSnapshot != null) {
            WindowRealtimeFeatures previousDayPart = subtractSameDayCounters(previousDayLastSnapshot, exactBaseline);
            return new WindowRealtimeFeatures(
                    previousDayPart.windowExposureCount() + safeInt(currentSnapshot.getDayShowCount()),
                    previousDayPart.windowClickCount() + safeInt(currentSnapshot.getDayClickCount())
            );
        }
        if (exactBaseline != null) {
            return new WindowRealtimeFeatures(
                    Math.max(safeInt(currentSnapshot.getDayShowCount()) - safeInt(exactBaseline.getDayShowCount()), 0),
                    Math.max(safeInt(currentSnapshot.getDayClickCount()) - safeInt(exactBaseline.getDayClickCount()), 0)
            );
        }
        if (previousDayLastSnapshot != null) {
            return new WindowRealtimeFeatures(
                    safeInt(currentSnapshot.getDayShowCount()),
                    safeInt(currentSnapshot.getDayClickCount())
            );
        }
        return WindowRealtimeFeatures.empty();
    }

    private WindowRealtimeFeatures subtractSameDayCounters(MtPromotionAdRealtimeSnapshot currentSnapshot,
                                                           MtPromotionAdRealtimeSnapshot baselineSnapshot) {
        if (currentSnapshot == null || baselineSnapshot == null) {
            return WindowRealtimeFeatures.empty();
        }
        return new WindowRealtimeFeatures(
                Math.max(safeInt(currentSnapshot.getDayShowCount()) - safeInt(baselineSnapshot.getDayShowCount()), 0),
                Math.max(safeInt(currentSnapshot.getDayClickCount()) - safeInt(baselineSnapshot.getDayClickCount()), 0)
        );
    }

    private int countOrders(Long shopId, LocalDateTime startAt, LocalDateTime endAt) {
        if (shopId == null || startAt == null || endAt == null) {
            return 0;
        }
        Integer count = mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                startAt.atZone(DEFAULT_ZONE_ID).toEpochSecond(),
                endAt.atZone(DEFAULT_ZONE_ID).toEpochSecond()
        );
        return count == null ? 0 : count;
    }

    private String resolveCurrentHourRange(LocalDateTime slotTime) {
        if (slotTime == null) {
            return null;
        }
        return String.format("%02d:00-%02d:00", slotTime.getHour(), (slotTime.getHour() + 1) % 24);
    }

    private BigDecimal resolveBid(MtPromotionBidRecord record) {
        if (record.getNextBidAmount() != null) {
            return record.getNextBidAmount();
        }
        if (record.getPreviousBidAmount() != null) {
            return record.getPreviousBidAmount();
        }
        return ZERO_MONEY;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? ZERO_MONEY : value;
    }

    private BigDecimal yuanFromCents(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return defaultMoney(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMATTER);
    }

    private String resolveChineseWeekday(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        DayOfWeek dayOfWeek = value.getDayOfWeek();
        return switch (dayOfWeek) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private String loadShopShippingTime(Long shopId) {
        if (shopId == null || mtShopService == null) {
            return "无";
        }
        MtShop shop = mtShopService.findById(shopId);
        return StringUtils.defaultIfBlank(shop == null ? null : shop.getShippingTime(), "无");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private CompletionUsage extractUsage(ChatCompletion completion) {
        if (completion == null || completion.usage().isEmpty()) {
            throw new IllegalStateException("日志大模型返回缺少 token 用量");
        }
        CompletionUsage usage = completion.usage().get();
        if (usage.promptTokens() < 0 || usage.completionTokens() < 0) {
            throw new IllegalStateException("日志大模型返回的 token 用量无效");
        }
        if (usage.totalTokens() <= 0) {
            throw new IllegalStateException("日志大模型返回的 token 用量无效");
        }
        return usage;
    }

    private String extractContent(ChatCompletion completion) {
        if (completion == null || completion.choices().isEmpty()) {
            throw new IllegalStateException("日志大模型未返回有效 choices");
        }
        String content = completion.choices().get(0).message().content().orElse(null);
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("日志大模型返回内容为空");
        }
        return content;
    }

    private void validateMarkdown(String content) {
        String normalized = content == null ? "" : content.trim();
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalStateException("日志 Markdown 为空");
        }
        if (normalized.length() > MAX_MARKDOWN_LENGTH) {
            throw new IllegalStateException("日志 Markdown 超出长度限制");
        }
        if (normalized.contains("```")) {
            throw new IllegalStateException("日志 Markdown 不允许代码块");
        }
        if (normalized.matches("(?s).*</?[a-zA-Z][^>]*>.*")) {
            throw new IllegalStateException("日志 Markdown 不允许原始 HTML");
        }
        if (UNRESOLVED_PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            throw new IllegalStateException("日志 Markdown 不允许未替换占位符");
        }
    }

    record HistoricalFeatures(
            int sevenDayExposureCount,
            int sevenDayClickCount,
            long sevenDayPromotionCostCents,
            boolean hasLastWeekHourReference,
            int lastWeekHourExposureCount,
            int lastWeekHourClickCount,
            long lastWeekHourPromotionCostCents,
            int yesterdayHourExposureCount,
            int yesterdayHourClickCount,
            long yesterdayHourPromotionCostCents
    ) {
        static HistoricalFeatures empty() {
            return new HistoricalFeatures(0, 0, 0L, false, 0, 0, 0L, 0, 0, 0L);
        }
    }

    record CurrentRealtimeFeatures(
            int hourExposureCount,
            int hourClickCount,
            long hourPromotionCostCents,
            MtPromotionAdRealtimeSnapshot snapshot
    ) {
    }

    record WindowRealtimeFeatures(int windowExposureCount, int windowClickCount) {
        static WindowRealtimeFeatures empty() {
            return new WindowRealtimeFeatures(0, 0);
        }
    }

    record PeakWindowPromptFeatures(
            String currentDayTypeText,
            String profileDayTypeText,
            int lookbackDays,
            int sampleDayCount,
            int sampleOrderCount,
            String windowRangesText,
            String currentSlotStatusText
    ) {
    }
}
