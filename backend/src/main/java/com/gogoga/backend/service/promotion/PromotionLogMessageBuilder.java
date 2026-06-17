package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.util.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 推广日志文案构造器
 */
@Component
public class PromotionLogMessageBuilder {

    private static final Set<String> REMINDER_DECISION_REASONS = Set.of(
            "POWER_BELOW_WARNING_THRESHOLD_REMINDER",
            "PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER"
    );
    private static final Pattern INTERNAL_ENUM_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");
    private static final Pattern UNRESOLVED_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^{}]+}}|\\$\\{[^{}]+}");

    public String buildPlainText(MtPromotionBidRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(resolveStatusText(record));

        if (StringUtils.isNotBlank(record.getDecisionReason())) {
            builder.append(" | 原因：").append(resolveDecisionReasonText(record.getDecisionReason()));
        }
        String llmFailureSummaryText = resolveRecordLlmFailureSummaryText(record);
        if (StringUtils.isNotBlank(llmFailureSummaryText)) {
            builder.append(" | 补充：").append(llmFailureSummaryText);
        }
        String planStatusOperationText = resolvePlanStatusOperationText(record.getUpdatePayloadJson());
        if (planStatusOperationText != null) {
            builder.append(" | 操作：").append(planStatusOperationText);
        }
        if (record.getNextBidAmount() != null) {
            builder.append(" | 最终出价").append(stripTrailingZeros(record.getNextBidAmount())).append("元");
        }
        if (StringUtils.isNotBlank(record.getErrorMsg())) {
            builder.append(" | 错误：").append(resolveErrorText(record.getErrorMsg()));
        }
        return builder.toString();
    }

    public String buildFallbackMarkdown(MtPromotionBidRecord record) {
        List<String> lines = new ArrayList<>();
        lines.add("**" + resolveStatusText(record) + "**");

        if (StringUtils.isNotBlank(record.getDecisionReason())) {
            lines.add("- 原因：" + resolveDecisionReasonText(record.getDecisionReason()));
        }
        String llmFailureSummaryText = resolveRecordLlmFailureSummaryText(record);
        if (StringUtils.isNotBlank(llmFailureSummaryText)) {
            lines.add("- 补充：" + llmFailureSummaryText);
        }
        String planStatusOperationText = resolvePlanStatusOperationText(record.getUpdatePayloadJson());
        if (planStatusOperationText != null) {
            lines.add("- 操作：" + planStatusOperationText);
        }
        if (record.getPreviousBidAmount() != null || record.getNextBidAmount() != null) {
            List<String> bidParts = new ArrayList<>();
            if (record.getPreviousBidAmount() != null) {
                bidParts.add("上次出价 " + stripTrailingZeros(record.getPreviousBidAmount()) + " 元");
            }
            if (record.getNextBidAmount() != null) {
                bidParts.add("本次出价 " + stripTrailingZeros(record.getNextBidAmount()) + " 元");
            }
            lines.add("- 出价：" + String.join("，", bidParts));
        }
        if (StringUtils.isNotBlank(record.getErrorMsg())) {
            lines.add("- 错误：" + resolveErrorText(record.getErrorMsg()));
        }
        return String.join("\n", lines);
    }

    public String resolveExecuteStatusText(String executeStatus) {
        if ("SUCCESS".equals(executeStatus)) {
            return "执行成功";
        }
        if ("SKIPPED".equals(executeStatus)) {
            return "未执行";
        }
        if ("FAILED".equals(executeStatus)) {
            return "执行失败";
        }
        if (StringUtils.isBlank(executeStatus) || containsInternalToken(executeStatus)) {
            return "状态待确认";
        }
        return executeStatus;
    }

    public String resolveStatusText(MtPromotionBidRecord record) {
        if (record == null) {
            return "自动调价已记录";
        }
        if ("MANUAL".equals(record.getRecordSource())) {
            return "手动修改出价成功";
        }
        if (isReminderRecord(record)) {
            return "推广提醒";
        }
        if ("PROMOTION_RIGHT_EXPIRED_AUTO_DISABLED".equals(record.getDecisionReason())) {
            return "AI推广已自动关闭";
        }
        if ("SUCCESS".equals(record.getExecuteStatus())) {
            return "自动调价执行成功";
        }
        if ("SKIPPED".equals(record.getExecuteStatus())) {
            return "当前时段未执行自动调价";
        }
        if ("FAILED".equals(record.getExecuteStatus())) {
            return "自动调价执行失败";
        }
        return "自动调价已记录";
    }

    public String resolveDecisionReasonText(String decisionReason) {
        if (StringUtils.isBlank(decisionReason)) {
            return "";
        }
        switch (decisionReason) {
            case "FIRST_SUCCESS_OF_WINDOW":
                return "当前窗口首次执行，已确定初始出价";
            case "TOTAL_COST_RATIO_ABOVE_MAX":
                return "当前窗口累计费用率超过策略上限，本次不再参考曝光表现，直接降低出价";
            case "RATE_PRIORITY_FAST_CORRECTION":
                return "当前费用率远超过最高费率，本轮按快速降低出价";
            case "NO_CLICK_COST_DELTA_ZERO":
                return "综合分析当前推广情况，判定出价偏低，增加出价";
            case "CLICK_DETECTED_KEEP_PREVIOUS_BID":
                return "综合分析当前推广情况，保持当前出价";
            case "EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID":
                return "最近两次调价后的曝光增长明显，保持当前出价";
            case "EXPOSURE_GROWTH_WEAK_INCREASE_BID":
                return "最近两次调价后的曝光增长未达到目标，增加出价";
            case "EXPOSURE_WEAK_GROWTH_ROLLBACK_BID":
                return "连续多轮加价后曝光增长仍未达标，回退出价";
            case "RATE_EXPOSURE_CONFLICT_KEEP_BID":
                return "费率信号与曝光信号相互抵消，保持当前出价";
            case "MAX_BID_REACHED_KEEP":
                return "当前出价已经比较高，保持当前出价，继续观察";
            case "WINDOW_INCOME_NOT_READY_KEEP_INITIAL_BID":
                return "当前窗口还没有产生收入，继续保持当前初始出价";
            case "LOW_PEAK_BASELINE_BID":
                return "当前处于营业低峰期，按时段节奏控制出价";
            case "PRE_PEAK_RAMP_UP":
                return "当前临近高峰时段，按时段节奏小幅调整出价";
            case "POST_PEAK_RAMP_DOWN":
                return "当前已过高峰时段，按时段节奏小幅调整出价";
            case "LOW_BASELINE_HOLD":
                return "当前处于营业低峰期，按时段节奏保持出价";
            case "OUT_OF_PEAK_WINDOW_KEEP_INITIAL_BID":
                return "当前不在高峰时段，按时段节奏控制出价";
            case "AUTO_TIME_MODE":
                return "自动时段模式允许执行";
            case "CUSTOM_SLOT_MATCHED":
                return "命中自定义时段";
            case "BUSINESS_TIME_MATCHED":
                return "命中营业时段";
            case "OUT_OF_CUSTOM_WINDOW":
                return "当前不在自定义时段内";
            case "OUT_OF_BUSINESS_WINDOW":
                return "当前不在营业时段内";
            case "INVALID_SHIPPING_TIME":
                return "营业时间缺失或格式不正确";
            case "EMPTY_CUSTOM_SLOTS":
                return "未配置自定义时段";
            case "PROMOTION_DISABLED_DURING_TASK":
                return "任务执行前检测到推广开关已关闭";
            case "STRATEGY_CHANGED_DURING_TASK":
                return "任务执行前检测到推广策略已变更";
            case "REMOTE_PLAN_STATUS_CLOSED":
                return "检测到商家端推广已被外部关闭，已停止AI推广";
            case "PROMOTION_DAILY_BUDGET_EXHAUSTED":
                return "今日美团推广预算已用尽，本槽位已跳过自动调价";
            case "WINDOW_END_BID_RESET_TO_FLOOR":
                return "推广时段已结束，出价已回落到最低值";
            case "SETTINGS_SAVE_OUT_OF_WINDOW_BID_RESET":
                return "策略保存后当前不在推广时段内，出价已回落到最低值";
            case "UNTRUSTED_PROMOTION_METRICS":
                return "当前槽位推广指标不可信，已跳过自动调价";
            case "POWER_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED":
                return "当前算力不足以执行一次AI推广，已自动关闭AI推广，本槽位不再执行自动调价";
            case "POWER_BELOW_EXECUTION_THRESHOLD_AFTER_CONSUME_AUTO_DISABLED":
                return "本次执行扣减算力后余额不足以执行一次AI推广，已自动关闭AI推广";
            case "POWER_BELOW_WARNING_THRESHOLD_REMINDER":
                return "当前算力余额已低于20000，建议尽快充值，避免后续时段AI推广中断";
            case "PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED":
                return "当前美团广告余额低于1元，已自动关闭AI推广，本槽位不再执行自动调价";
            case "PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER":
                return "当前美团广告余额已低于5元，建议尽快充值，避免后续时段AI推广中断";
            case "PROMOTION_RIGHT_EXPIRED_AUTO_DISABLED":
                return "功能权益已过期，系统已关闭AI推广并停止订单推送";
            case "MANUAL_BID_UPDATE":
                return "商家已手动修改当前推广出价，后续自动调价将以该出价为基础继续执行";
            case "INVALID_STRATEGY_TIME_MODE":
                return "策略时段配置不正确";
            case "UNSUPPORTED_TIME_MODE":
                return "暂不支持的时段模式";
            default:
                if (StringUtils.startsWith(decisionReason, "UNTRUSTED_PROMOTION_METRICS:")) {
                    return "当前槽位推广指标不可信，已跳过自动调价";
                }
                if (containsInternalToken(decisionReason)) {
                    return "系统暂未返回可展示的原因说明";
                }
                return decisionReason;
        }
    }

    public String resolveErrorText(String errorMsg) {
        if (StringUtils.isBlank(errorMsg)) {
            return null;
        }
        if (containsInternalToken(errorMsg) || containsUnresolvedPlaceholder(errorMsg)) {
            return "系统执行异常，请稍后重试或联系客服处理";
        }
        return errorMsg;
    }

    public String resolveLlmFailureSummaryText(String llmFailureSummary) {
        if (StringUtils.isBlank(llmFailureSummary)) {
            return null;
        }
        switch (llmFailureSummary) {
            case "RATE_ABOVE_MAX_SKIP_LLM":
                return "当前窗口累计费用率已经超过上限，这次先按控成本方式自动降价";
            case "REFERENCE_HISTORY_INSUFFICIENT":
                return "当前窗口历史数据还不够，先按稳定规则执行";
            default:
                return llmFailureSummary;
        }
    }

    @SuppressWarnings("unchecked")
    public String resolvePlanStatusOperationText(String updatePayloadJson) {
        if (StringUtils.isBlank(updatePayloadJson)) {
            return null;
        }
        Map<String, Object> payload = JsonUtil.toMap(updatePayloadJson);
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        Map<String, Object> planStatus = null;
        Object planStatusObject = payload.get("planStatus");
        if (planStatusObject instanceof Map) {
            planStatus = (Map<String, Object>) planStatusObject;
        } else if (payload.get("status") != null) {
            planStatus = payload;
        }
        if (planStatus == null || planStatus.get("status") == null) {
            return null;
        }

        String status = String.valueOf(planStatus.get("status"));
        if ("0".equals(status)) {
            return "已开启商家端推广";
        }
        if ("1".equals(status)) {
            return "已关闭商家端推广";
        }
        return null;
    }

    private String resolveRecordLlmFailureSummaryText(MtPromotionBidRecord record) {
        if (record == null || StringUtils.isBlank(record.getDecisionDetailJson())) {
            return null;
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        if (detail == null) {
            return null;
        }
        return resolveLlmFailureSummaryText(detail.getLlmFailureSummary());
    }

    public boolean isReminderRecord(MtPromotionBidRecord record) {
        return record != null && isReminderDecisionReason(record.getDecisionReason());
    }

    public boolean isReminderDecisionReason(String decisionReason) {
        return decisionReason != null && REMINDER_DECISION_REASONS.contains(decisionReason);
    }

    private String stripTrailingZeros(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private boolean containsInternalToken(String value) {
        return StringUtils.isNotBlank(value) && INTERNAL_ENUM_PATTERN.matcher(value).find();
    }

    private boolean containsUnresolvedPlaceholder(String value) {
        return StringUtils.isNotBlank(value) && UNRESOLVED_PLACEHOLDER_PATTERN.matcher(value).find();
    }
}
