import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Collapse,
  DatePicker,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  TimePicker,
  Tooltip,
  Typography,
} from "antd";
import { EyeOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import { DownOutlined, UpOutlined } from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import MarkdownLogPreview from "@/components/MarkdownLogPreview";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { useAuth } from "@/contexts/AuthContext";
import { useResponsive } from "@/hooks";
import {
  getPricingAnalysisDetail,
  getPricingAnalysisRows,
} from "@/services/pricing-analysis-api";
import type { PricingAnalysisDetail, PricingAnalysisRow } from "@/types";
import { getResponsiveControlStyle } from "@/utils/responsive";

const { RangePicker } = DatePicker;
const { Text } = Typography;

const jsonBlockStyle: React.CSSProperties = {
  margin: 0,
  padding: 12,
  background: "#f6f8fa",
  border: "1px solid #e5e7eb",
  borderRadius: 6,
  maxHeight: 320,
  overflow: "auto",
  whiteSpace: "pre-wrap",
  wordBreak: "break-word",
};

const detailCellStyle: React.CSSProperties = {
  minWidth: 0,
  display: "grid",
  gridTemplateColumns: "132px minmax(0, 1fr)",
  border: "1px solid #f0f0f0",
  borderRadius: 6,
  overflow: "hidden",
  background: "#fff",
};

const detailLabelStyle: React.CSSProperties = {
  padding: "8px 10px",
  background: "#fafafa",
  color: "rgba(0, 0, 0, 0.65)",
  fontWeight: 500,
  borderRight: "1px solid #f0f0f0",
};

const detailValueStyle: React.CSSProperties = {
  minWidth: 0,
  padding: "8px 10px",
  wordBreak: "break-word",
};

type SearchFormValues = {
  keyword?: string;
  shopId?: number;
  poiId?: string;
  shopName?: string;
  executeStatus?: string;
  decisionAction?: string;
  decisionReason?: string;
  logMarkdownStatus?: string;
  slotRange?: [Dayjs | null, Dayjs | null];
  startDate?: Dayjs;
  startClock?: Dayjs;
  endDate?: Dayjs;
  endClock?: Dayjs;
};

function buildDefaultSearchValues(): SearchFormValues {
  const slotStart = dayjs().subtract(3, "day").startOf("day");
  return {
    slotRange: [slotStart, null],
    startDate: slotStart,
    startClock: slotStart,
    endDate: undefined,
    endClock: undefined,
  };
}

const executeStatusOptions = [
  { label: "成功", value: "SUCCESS" },
  { label: "跳过", value: "SKIPPED" },
  { label: "失败", value: "FAILED" },
];

const decisionActionOptions = [
  { label: "初始化", value: "INIT" },
  { label: "增加", value: "INCREASE" },
  { label: "减少", value: "DECREASE" },
  { label: "保持", value: "KEEP" },
];

const logStatusOptions = [
  { label: "生成中", value: "PENDING" },
  { label: "已生成", value: "READY" },
  { label: "兜底", value: "FALLBACK" },
];

function mergeDateAndClock(
  date?: Dayjs,
  clock?: Dayjs,
  mode: "start" | "end" = "start",
) {
  if (!date) {
    return undefined;
  }
  if (!clock) {
    return mode === "start" ? date.startOf("day") : date.endOf("day");
  }
  return date.hour(clock.hour()).minute(clock.minute()).second(clock.second());
}

const decisionReasonTextMap: Record<string, string> = {
  FIRST_SUCCESS_OF_WINDOW: "当前窗口首次执行，已确定初始出价",
  TOTAL_COST_RATIO_ABOVE_MAX: "当前窗口累计费用率超过策略上限，降低出价",
  RATE_PRIORITY_FAST_CORRECTION:
    "费率优先且费用率超过上限5个百分点以上，按快降策略降低出价",
  NO_CLICK_COST_DELTA_ZERO: "推广暂无点击且花费未增长，增加出价",
  CLICK_DETECTED_KEEP_PREVIOUS_BID: "推广已有点击，保持当前出价",
  EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID: "曝光增长明显，保持当前出价",
  EXPOSURE_GROWTH_WEAK_INCREASE_BID: "曝光增长不足，增加出价",
  EXPOSURE_WEAK_GROWTH_ROLLBACK_BID:
    "连续3轮曝光弱增长，回退到第1轮前置出价",
  RATE_EXPOSURE_CONFLICT_KEEP_BID: "费率与曝光信号抵消，保持当前出价",
  MAX_BID_REACHED_KEEP: "当前出价已经比较高，保持当前出价，继续观察",
  WINDOW_INCOME_NOT_READY_KEEP_INITIAL_BID: "当前窗口暂无收入，保持初始出价",
  LOW_PEAK_BASELINE_BID: "低峰期按基准出价执行",
  PRE_PEAK_RAMP_UP: "临近高峰，按节奏预热提价",
  POST_PEAK_RAMP_DOWN: "高峰结束，按节奏回落出价",
  LOW_BASELINE_HOLD: "低峰期保持基准出价",
  OUT_OF_PEAK_WINDOW_KEEP_INITIAL_BID: "不在高峰时段，按初始出价执行",
  AUTO_TIME_MODE: "自动时段模式允许执行",
  CUSTOM_SLOT_MATCHED: "命中自定义时段",
  BUSINESS_TIME_MATCHED: "命中营业时段",
  OUT_OF_CUSTOM_WINDOW: "不在自定义时段内",
  OUT_OF_BUSINESS_WINDOW: "不在营业时段内",
  INVALID_SHIPPING_TIME: "营业时间缺失或格式不正确",
  EMPTY_CUSTOM_SLOTS: "未配置自定义时段",
  PROMOTION_DISABLED_DURING_TASK: "任务执行前推广开关已关闭",
  STRATEGY_CHANGED_DURING_TASK: "任务执行前推广策略已变更",
  REMOTE_PLAN_STATUS_CLOSED: "外部关闭推广，已停止AI推广",
  PROMOTION_DAILY_BUDGET_EXHAUSTED: "今日美团推广预算已用尽",
  WINDOW_END_BID_RESET_TO_FLOOR: "推广时段结束，出价回落到最低值",
  UNTRUSTED_PROMOTION_METRICS: "推广指标不可信，跳过自动调价",
  POWER_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED:
    "算力低于执行阈值，已自动关闭AI推广",
  POWER_BELOW_EXECUTION_THRESHOLD_AFTER_CONSUME_AUTO_DISABLED:
    "扣减后算力低于阈值，已自动关闭AI推广",
  POWER_BELOW_WARNING_THRESHOLD_REMINDER: "算力余额偏低提醒",
  PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED:
    "美团广告余额低于执行阈值，已自动关闭AI推广",
  PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER: "美团广告余额偏低提醒",
  INVALID_STRATEGY_TIME_MODE: "策略时段配置不正确",
  UNSUPPORTED_TIME_MODE: "暂不支持的时段模式",
};

const decisionEnumTextMap: Record<string, string> = {
  rule: "规则决策",
  llm: "大模型决策",
  LOW_PEAK_BASELINE: "低峰基准",
  FEE_RATE_BRANCH: "费率分支",
  EXPOSURE_BRANCH: "曝光分支",
  RATE_ONLY: "仅按费率分支",
  EXPOSURE_ONLY: "仅按曝光分支",
  RATE_AND_EXPOSURE: "费率与曝光综合",
  KEEP: "保持",
  INCREASE: "增加",
  DECREASE: "减少",
  INIT: "初始化",
  NOT_APPLICABLE: "不适用",
  FIRST_SUCCESS_OF_WINDOW: "当前窗口首次执行，已确定初始出价",
  LOW_PEAK_BASELINE_BID: "低峰期按基准出价",
  WINDOW_INCOME_NOT_READY_KEEP_INITIAL_BID: "当前窗口暂无收入，保持初始出价",
  TOTAL_COST_RATIO_ABOVE_MAX: "当前窗口费用率超过策略上限",
  RATE_PRIORITY_FAST_CORRECTION: "费率优先快降",
  NO_CLICK_COST_DELTA_ZERO: "推广暂无点击且花费未增长，增加出价",
  CLICK_DETECTED_KEEP_PREVIOUS_BID: "推广已有点击，保持当前出价",
  EXPOSURE_GROWTH_EFFECTIVE_KEEP_BID: "曝光增长明显，保持当前出价",
  EXPOSURE_GROWTH_WEAK_INCREASE_BID: "曝光增长不足，增加出价",
  EXPOSURE_WEAK_GROWTH_ROLLBACK_BID:
    "连续3轮曝光弱增长，回退到第1轮前置出价",
  RATE_EXPOSURE_CONFLICT_KEEP_BID: "费率与曝光信号抵消，保持当前出价",
  DECISION_CONTEXT_NOT_DYNAMIC: "当前决策上下文不适用动态曝光分支",
  RATE_ABOVE_MAX_SKIP_EXPOSURE: "费用率已超过上限，跳过曝光分支",
  RATE_ABOVE_MAX_SKIP_LLM: "费用率已超过上限，跳过大模型决策",
  REFERENCE_HISTORY_INSUFFICIENT: "参考历史数据不足",
  EXPOSURE_GROWTH_BELOW_THRESHOLD: "曝光增长低于阈值",
  SKIPPED_BY_RATE_CEILING: "受费率上限约束跳过",
  APPLIED: "已应用",
  LOW_BASELINE_HOLD: "低峰期保持基准出价",
  SMOOTH_PEAK_RAMP: "平滑高峰轨道调价",
  FIXED_NEXT_PEAK_WARMUP: "固定下一高峰预热",
  PRE_PEAK_RAMP_UP: "高峰前预热提价",
  POST_PEAK_RAMP_DOWN: "高峰后回落出价",
  IN_PEAK_HOLD: "高峰期保持目标出价",
  PEAK: "高峰期",
  PEAK_MATCHED: "命中高峰窗口",
  PROFILE_UNAVAILABLE: "高峰画像不可用",
  UNAVAILABLE_PROFILE: "高峰画像不可用",
  LOW_BASELINE: "低峰基准",
  PEAK_RULE: "高峰规则",
  PEAK_RAMP: "高峰轨道",
  PEAK_ENTRY: "高峰入场",
  RATE_WITH_EXPOSURE: "费率结合曝光",
  EXPOSURE_KEEP: "曝光分支建议保持",
  EXPOSURE_INCREASE: "曝光分支建议增加",
  EXPOSURE_WEAK_GROWTH_ROLLBACK: "连续曝光弱增长回退",
  RATE_EXPOSURE_CONFLICT: "费率与曝光信号冲突",
  USABLE: "可用",
  UNAVAILABLE: "不可用",
  INSUFFICIENT: "样本不足",
  INHERITED: "继承上一轮",
  PEER_AVG: "附近商家平均出价",
  LOW_PEAK_ORDER_SHARE: "低峰订单占比基准",
  CURRENT_PLAN: "当前推广计划",
  DEFAULT: "默认值",
  DYNAMIC_INITIAL: "动态初始出价",
  CONFIGURED: "策略配置",
  PEER_AVERAGE: "附近商家均值",
  MANUAL: "人工配置",
  TEMPORARY_RECOVERY_REALTIME: "临时恢复实时口径",
  WINDOW_END_BID_RESET_TO_FLOOR: "推广时段结束，出价回落到最低值",
  OUT_OF_PEAK_WINDOW_KEEP_INITIAL_BID: "不在高峰时段，按初始出价执行",
  AUTO_TIME_MODE: "自动时段模式允许执行",
  CUSTOM_SLOT_MATCHED: "命中自定义时段",
  BUSINESS_TIME_MATCHED: "命中营业时段",
  OUT_OF_CUSTOM_WINDOW: "不在自定义时段内",
  OUT_OF_BUSINESS_WINDOW: "不在营业时段内",
  INVALID_SHIPPING_TIME: "营业时间缺失或格式不正确",
  EMPTY_CUSTOM_SLOTS: "未配置自定义时段",
  PROMOTION_DISABLED_DURING_TASK: "任务执行前推广开关已关闭",
  STRATEGY_CHANGED_DURING_TASK: "任务执行前推广策略已变更",
  REMOTE_PLAN_STATUS_CLOSED: "外部关闭推广，已停止AI推广",
  PROMOTION_DAILY_BUDGET_EXHAUSTED: "今日美团推广预算已用尽",
  UNTRUSTED_PROMOTION_METRICS: "推广指标不可信，跳过自动调价",
  POWER_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED: "算力低于执行阈值，已自动关闭AI推广",
  POWER_BELOW_EXECUTION_THRESHOLD_AFTER_CONSUME_AUTO_DISABLED: "扣减后算力低于阈值，已自动关闭AI推广",
  POWER_BELOW_WARNING_THRESHOLD_REMINDER: "算力余额偏低提醒",
  PROMOTION_BALANCE_BELOW_EXECUTION_THRESHOLD_AUTO_DISABLED: "美团广告余额低于执行阈值，已自动关闭AI推广",
  PROMOTION_BALANCE_BELOW_WARNING_THRESHOLD_REMINDER: "美团广告余额偏低提醒",
  PROMOTION_RIGHT_EXPIRED_AUTO_DISABLED: "功能权益已过期，已关闭AI推广",
  INVALID_STRATEGY_TIME_MODE: "策略时段配置不正确",
  UNSUPPORTED_TIME_MODE: "暂不支持的时段模式",
  EXPOSURE_GROWTH_ABOVE_THRESHOLD: "曝光增长高于阈值",
  SHOP_ID_MISSING: "门店ID缺失",
  SUCCESS_HISTORY_INSUFFICIENT: "成功调价历史不足",
  SUCCESS_HISTORY_STREAK_INSUFFICIENT: "连续曝光加价历史不足3轮",
  EXPOSURE_INCREASE_STREAK_INTERRUPTED: "连续曝光加价链路被打断",
  FOLLOWUP_EXPOSURE_UNAVAILABLE: "连续曝光加价后续曝光样本不完整",
  ROLLBACK_BID_UNAVAILABLE: "连续3轮第1轮前置出价不可用",
  PREVIOUS_SUCCESS_SLOT_TIME_MISSING: "上一成功记录槽位时间缺失",
  LATEST_SUCCESS_SLOT_TIME_MISSING: "最新成功记录槽位时间缺失",
  PREVIOUS_FOLLOWUP_CROSS_DAY: "上一记录后续观察跨天",
  LATEST_FOLLOWUP_CROSS_DAY: "最新记录后续观察跨天",
  PREVIOUS_FOLLOWUP_SNAPSHOT_MISSING: "上一记录后续曝光快照缺失",
  LATEST_FOLLOWUP_SNAPSHOT_MISSING: "最新记录后续曝光快照缺失",
  PREVIOUS_FOLLOWUP_EXPOSURE_ZERO: "上一记录后续曝光为0",
  PREVIOUS_FOLLOWUP_EXPOSURE_NEGATIVE: "上一记录后续曝光为负数",
  LATEST_FOLLOWUP_EXPOSURE_NEGATIVE: "最新记录后续曝光为负数",
  EXPOSURE_SERVICE_UNAVAILABLE: "曝光分析服务不可用",
  REALTIME: "实时口径",
  SNAPSHOT: "快照口径",
  HISTORY: "历史口径",
  SUCCESS: "成功",
  SKIPPED: "跳过",
  FAILED: "失败",
  PENDING: "生成中",
  READY: "已生成",
  FALLBACK: "兜底",
  AUTO: "自动调价",
  manual: "手动固定日志",
};

const decisionEnumFieldKeys = new Set([
  "decisionMode",
  "decisionSource",
  "pricingBranch",
  "feeRateBranchAction",
  "feeRateBranchReason",
  "exposureBranchStatus",
  "exposureBranchAction",
  "exposureBranchReason",
  "finalAggregationMode",
  "finalAggregationSource",
  "peakBidPhase",
  "peakWindowProfileStatus",
  "lowPeakBaselineSource",
  "peakEntryBidSource",
  "peakRampReason",
  "metricsBaselineType",
  "llmFailureSummary",
  "logLlmFailureSummary",
  "logMarkdownSource",
  "recordSource",
  "logPowerConsumeFailureSummary",
  "peakWindowUnavailableReason",
  "exposureWeakGrowthRollbackUnavailableReason",
]);

const detailLabelMap: Record<string, string> = {
  id: "记录ID",
  shopId: "门店ID",
  slotTime: "槽位时间",
  recordSource: "记录来源",
  attributedSlotTime: "归属槽位时间",
  operatedAt: "实际操作时间",
  windowKey: "窗口标识",
  windowStartAt: "窗口开始时间",
  windowEndAt: "窗口结束时间",
  executeStatus: "执行状态",
  decisionAction: "决策动作",
  decisionReason: "决策原因",
  previousBidAmount: "上一出价",
  nextBidAmount: "下一出价",
  totalCost: "窗口花费",
  totalIncome: "窗口实收",
  totalCostRatio: "窗口费用率",
  costDelta: "本槽位花费增量",
  costCounterValue: "花费累计计数",
  requestNo: "请求号",
  errorMsg: "错误信息",
  markdownMessage: "商家日志",
  logMarkdownStatus: "日志状态",
  logTaskId: "日志任务ID",
  createTime: "创建时间",
  poiId: "美团门店ID",
  epoiId: "系统门店标识",
  shopName: "门店名称",
  address: "门店地址",
  shippingTime: "营业时间",
  hasAccessToken: "是否有授权",
  tokenExpiresAt: "授权过期时间",
  tokenExpired: "授权是否过期",
  authStatus: "授权状态编码",
  authStatusText: "授权状态",
  reauthRequired: "是否需重新授权",
  authStatusChangedAt: "授权状态变更时间",
  lastAuthFailureAt: "最近鉴权失败时间",
  rights: "门店权益",
  metricSnapshot: "指标快照",
  realtimeSnapshot: "实时广告快照",
  peakProfileContext: "高峰期画像",
  snapshot: "执行时画像快照",
  profiles: "画像记录",
  dayType: "执行日型",
  profileDayType: "采用画像日型",
  profileStatus: "画像状态",
  calendarDayType: "日历类型",
  holidayName: "日历说明",
  peakMatched: "是否命中高峰",
  gatingEnabled: "是否启用高峰控制",
  matchedRange: "命中高峰窗口",
  sampleDayCount: "样本天数",
  sampleOrderCount: "样本订单数",
  unavailableReason: "不可用原因",
  generatedAt: "生成时间",
  peakWindows: "高峰窗口",
  lookbackDays: "回看天数",
  peakWindowsJson: "高峰窗口JSON",
  executionStrategyConfigHash: "执行时策略Hash",
  currentStrategyConfigHash: "当前策略Hash",
  executionStrategySnapshotMissing: "执行时策略缺失",
  currentPromotionEnabled: "当前推广开关",
  currentStrategyConfigured: "当前策略已配置",
  sameAsCurrent: "策略是否与当前一致",
  windowOrderCount: "窗口订单数",
  windowIncome: "窗口订单实收",
  currentHourOrderCount: "当前小时订单数",
  currentHourIncome: "当前小时实收",
  decisionMode: "决策模式",
  decisionSource: "决策来源",
  pricingBranch: "定价分支",
  feeRateBranchAction: "费率分支动作",
  feeRateBranchReason: "费率分支原因",
  exposureBranchStatus: "曝光分支状态",
  exposureBranchAction: "曝光分支动作",
  exposureBranchReason: "曝光分支原因",
  finalAggregationMode: "最终聚合模式",
  finalAggregationSource: "最终聚合来源",
  peakBidPhase: "高峰轨道阶段",
  peakWindowKey: "当前高峰窗口",
  nextPeakWindowKey: "下一高峰窗口",
  peakWindowDayType: "执行日型",
  peakWindowProfileDayType: "采用画像日型",
  peakWindowMatched: "是否命中高峰",
  peakWindowGatingEnabled: "是否启用高峰控制",
  peakWindowMatchedRange: "命中高峰范围",
  peakWindowProfileStatus: "高峰画像状态",
  peakWindowSampleDayCount: "高峰样本天数",
  peakWindowSampleOrderCount: "高峰样本订单数",
  peakWindowUnavailableReason: "高峰画像不可用原因",
  statusCheckContext: "执行前远端推广状态检查",
  status: "推广状态",
  bid: "远端出价",
  budget: "推广预算",
  budgetUsed: "已用预算",
  availableBudget: "剩余预算",
  responseCode: "响应码",
  responseMsg: "响应信息",
  responseDataErrMsg: "业务错误信息",
  bidConstraintContext: "出价上限",
  globalMaxBidAmount: "全局出价上限",
  shopMaxBidAmount: "门店出价上限",
  effectiveMaxBidAmount: "当前有效出价上限",
  userBidMinAmount: "用户最小出价",
  userBidMaxAmount: "用户最大出价",
  bidBeforeUserRangeClamp: "用户范围夹取前出价",
  bidAfterUserRangeClamp: "用户范围夹取后出价",
  active: "是否生效",
  source: "来源",
  rawMessage: "来源信息",
  observedAt: "观测时间",
  expireAt: "过期时间",
  lowPeakBaselineBidAmount: "低峰基准出价",
  lowPeakBaselineSource: "低峰基准来源",
  peakEntryBidAmount: "高峰入场出价",
  peakEntryBidSource: "高峰入场来源",
  slotsUntilPeakStart: "距高峰开始槽位数",
  slotsSincePeakEnd: "距高峰结束槽位数",
  peakRampTargetBidAmount: "高峰轨道目标出价",
  peakRampReason: "高峰轨道原因",
  peerAverageBidAmount: "附近商家平均出价",
  metricsBaselineType: "指标基线类型",
  metricsEffectiveWindowStartAt: "指标有效窗口开始",
  metricsBusinessWindowStartAt: "指标营业窗口开始",
  temporaryRecoveryWindow: "是否临时恢复窗口",
  recentOrderStatisticBizDate: "最近订单统计日期",
  recentOrderLookbackStartDate: "订单回看开始日",
  recentOrderLookbackEndDate: "订单回看结束日",
  recentOrderSampleDates: "订单样本日期",
  recentOrderStatisticTrusted: "最近订单统计可信",
  recentOrderStatisticUntrustedReason: "订单统计不可信原因",
  recentOrderTotalOrderCount: "最近样本订单数",
  recentOrderAverageDailyOrderCount: "最近日均单量",
  highVolumeRampDownEligible: "高单量快降生效条件",
  peakRampDecreaseStepAmount: "高峰后降价步长",
  lowPeakOrderCount: "低峰订单数",
  lowPeakOrderShare: "低峰订单占比",
  lowPeakShareBaselineBidAmount: "低峰占比基准出价",
  latestHighPeakBidAmount: "最近高峰出价",
  latestHighPeakBidSource: "最近高峰出价来源",
  lowPeakShareBaselineApplied: "是否采用低峰占比基准",
  ratePriorityFastCorrectionApplied: "费率优先快降已触发",
  ratePriorityCurrentRate: "当前费用率",
  ratePriorityMaxRate: "最高费用率",
  ratePriorityFastThreshold: "快降触发阈值",
  ratePriorityFastDecreaseStepAmount: "快降步长",
  ratePriorityRecoveredToMainAlgorithm: "已回到主算法条件",
  exposureWeakGrowthRollbackApplied: "连续弱增长保护命中",
  exposureWeakGrowthConsecutiveCount: "连续曝光加价轮数",
  exposureWeakGrowthRollbackUnavailableReason: "连续弱增长保护不可用原因",
  exposureWeakGrowthBestSlotTime: "回退参考槽位",
  exposureWeakGrowthBestFollowupExposure: "回退参考后续曝光",
  exposureWeakGrowthBestPreviousBidAmount: "回退参考前置出价",
  exposureWeakGrowthRollbackBidAmount: "连续弱增长回退出价",
  autoBidTask: "自动出价任务",
  logTask: "日志生成任务",
  powerConsumes: "算力消费记录",
  metricsPayloadJson: "指标原始JSON",
  metricSnapshotPayloadJson: "指标快照原始JSON",
  realtimeSnapshotPayloadJson: "实时广告快照原始JSON",
  updatePayloadJson: "更新原始JSON",
  decisionDetailJson: "决策详情JSON",
};

function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  const date = dayjs(value);
  return date.isValid() ? date.format("YYYY-MM-DD HH:mm:ss") : value;
}

function formatMoney(value?: number) {
  return value == null ? "-" : `¥${Number(value).toFixed(2)}`;
}

function formatStrategyBid(value: unknown, fallback: number) {
  const numeric = Number(value);
  return formatMoney(Number.isFinite(numeric) ? numeric : fallback);
}

function formatRatio(value?: number) {
  return value == null ? "-" : `${(Number(value) * 100).toFixed(2)}%`;
}

function formatCents(value?: unknown) {
  if (value == null || value === "") {
    return "-";
  }
  return `¥${(Number(value) / 100).toFixed(2)}`;
}

function renderStatus(status?: string) {
  if (status === "SUCCESS") return <Tag color="green">成功</Tag>;
  if (status === "FAILED") return <Tag color="red">失败</Tag>;
  if (status === "SKIPPED") return <Tag color="orange">跳过</Tag>;
  return <Text type="secondary">{status || "-"}</Text>;
}

function renderAction(action?: string) {
  if (action === "INCREASE") return <Tag color="blue">增加</Tag>;
  if (action === "DECREASE") return <Tag color="red">减少</Tag>;
  if (action === "KEEP") return <Tag color="default">保持</Tag>;
  if (action === "INIT") return <Tag color="purple">初始化</Tag>;
  return <Text type="secondary">{action || "-"}</Text>;
}

function translateRemotePromotionStatus(value?: unknown) {
  if (value === 0 || value === "0") return "开启";
  if (value === 1 || value === "1") return "关闭";
  return value == null || value === "" ? "-" : String(value);
}

function getDecisionReasonText(reason?: unknown) {
  if (typeof reason !== "string" || reason.length === 0) {
    return "-";
  }
  if (reason.startsWith("UNTRUSTED_PROMOTION_METRICS:")) {
    const detail = reason.slice("UNTRUSTED_PROMOTION_METRICS:".length);
    return `推广指标不可信，跳过自动调价（${detail}）`;
  }
  return decisionReasonTextMap[reason] || reason;
}

function renderDecisionReason(reason?: unknown, reasonText?: unknown) {
  if (typeof reason !== "string" || reason.length === 0) {
    return "-";
  }
  const displayText =
    typeof reasonText === "string" && reasonText.length > 0
      ? reasonText
      : getDecisionReasonText(reason);
  return (
    <Tooltip title={reason}>
      <span>{displayText}</span>
    </Tooltip>
  );
}

function renderDecisionEnum(value?: unknown) {
  if (typeof value !== "string" || value.length === 0) {
    return value == null || value === "" ? "-" : String(value);
  }
  const text = decisionEnumTextMap[value];
  if (!text || text === value) {
    return value;
  }
  return (
    <Tooltip title={value}>
      <span>{text}</span>
    </Tooltip>
  );
}

function translateMetricsTrusted(value?: unknown) {
  if (value === true) return "可信";
  if (value === false) return "不可信";
  return "-";
}

function translateUntrustedReason(value?: unknown) {
  if (value === "COST_COUNTER_UNREADABLE") return "花费累计计数无法读取";
  if (value === "EXECUTION_WINDOW_REALTIME_BASELINE_MISSING")
    return "执行窗口实时基线缺失";
  if (value === "EXECUTION_WINDOW_REALTIME_COUNTER_MISSING")
    return "执行窗口实时花费计数缺失";
  if (value === "EXECUTION_WINDOW_REALTIME_COUNTER_ROLLED_BACK")
    return "执行窗口实时花费计数回退";
  if (value === "PREVIOUS_SLOT_REALTIME_SNAPSHOT_MISSING")
    return "上一槽位实时快照缺失";
  if (value === "PREVIOUS_SLOT_REALTIME_COUNTER_MISSING")
    return "上一槽位实时花费计数缺失";
  if (value === "CURRENT_SLOT_REALTIME_COUNTER_ROLLED_BACK")
    return "当前槽位实时花费计数回退";
  if (value === "EXECUTION_WINDOW_BASELINE_MISSING")
    return "执行窗口指标基线缺失";
  if (value === "EXECUTION_WINDOW_BASELINE_COUNTER_MISSING")
    return "执行窗口指标花费计数缺失";
  if (value === "EXECUTION_WINDOW_BASELINE_COUNTER_ROLLED_BACK")
    return "执行窗口指标花费计数回退";
  if (value === "PREVIOUS_SLOT_SNAPSHOT_MISSING") return "上一槽位指标快照缺失";
  if (value === "PREVIOUS_SLOT_COUNTER_MISSING")
    return "上一槽位指标花费计数缺失";
  if (value === "CURRENT_SLOT_COST_DELTA_COUNTER_ROLLED_BACK")
    return "当前槽位花费增量计数回退";
  return value == null || value === "" ? "-" : String(value);
}

function translateRealtimeChannel(value?: unknown) {
  if (value === 1) return "点金推广";
  return value == null || value === "" ? "-" : String(value);
}

function compactObject(value?: Record<string, unknown>) {
  if (!value) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(value).filter(
      ([, item]) => item !== null && item !== undefined && item !== "",
    ),
  );
}

function omitObjectKeys(
  value: Record<string, unknown> | undefined,
  keys: string[],
) {
  if (!value) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(value).filter(([key]) => !keys.includes(key)),
  );
}

function stringifyJson(value?: unknown) {
  if (value == null || value === "") {
    return "-";
  }
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function isStructuredValue(value: unknown) {
  return typeof value === "object" && value !== null;
}

function renderJsonBlock(value?: unknown) {
  return <pre style={jsonBlockStyle}>{stringifyJson(value)}</pre>;
}

function renderFieldValue(
  key: string,
  value: unknown,
  container?: Record<string, unknown>,
) {
  if (Array.isArray(value) && key === "recentOrderSampleDates") {
    return value.length > 0 ? value.join("、") : "-";
  }
  if (isStructuredValue(value)) {
    return renderJsonBlock(value);
  }
  if (key === "decisionReason") {
    return renderDecisionReason(value, container?.decisionReasonText);
  }
  if (key === "recordSource") {
    if (value === "AUTO") return "自动调价";
    if (value === "MANUAL") return "手动调整";
  }
  if (key === "peakWindowDayType" || key === "peakWindowProfileDayType") {
    return translateDayType(value);
  }
  if (key === "peakWindowCalendarDayType" || key === "calendarDayType") {
    return translateCalendarDayType(value);
  }
  if (
    [
      "lowPeakBaselineBidAmount",
      "peakEntryBidAmount",
      "peakRampTargetBidAmount",
      "peerAverageBidAmount",
      "peakRampDecreaseStepAmount",
      "lowPeakShareBaselineBidAmount",
      "latestHighPeakBidAmount",
      "ratePriorityFastDecreaseStepAmount",
      "exposureWeakGrowthBestPreviousBidAmount",
      "exposureWeakGrowthRollbackBidAmount",
      "userBidMinAmount",
      "userBidMaxAmount",
      "effectiveMaxBidAmount",
      "bidBeforeUserRangeClamp",
      "bidAfterUserRangeClamp",
    ].includes(key)
  ) {
    return formatMoney(value as number | undefined);
  }
  if (
    [
      "lowPeakOrderShare",
      "ratePriorityCurrentRate",
      "ratePriorityMaxRate",
      "ratePriorityFastThreshold",
    ].includes(key)
  ) {
    return formatRatio(value as number | undefined);
  }
  if (
    decisionEnumFieldKeys.has(key) ||
    (typeof value === "string" && decisionEnumTextMap[value])
  ) {
    return renderDecisionEnum(value);
  }
  if (typeof value === "boolean") {
    return value ? "是" : "否";
  }
  return value == null || value === "" ? "-" : String(value);
}

function labelOf(key: string) {
  return detailLabelMap[key] || key;
}

function parseJsonObject(value?: unknown) {
  if (!value) {
    return null;
  }
  if (typeof value === "object") {
    return value as Record<string, unknown>;
  }
  if (typeof value !== "string") {
    return null;
  }
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === "object"
      ? (parsed as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

function translateStrategyValue(key: string, value: unknown) {
  if (value == null || value === "") {
    return "-";
  }
  if (key === "priority") {
    if (value === "rate") return "费率优先";
    if (value === "exposure") return "曝光优先";
  }
  if (key === "timeMode") {
    if (value === "auto") return "让AI自动管理";
    if (value === "business") return "按营业时长";
    if (value === "custom") return "自定义时段";
  }
  if (key === "rateMode" && value === "control") {
    return "控制费率";
  }
  if (key === "decisionMode") {
    if (value === "rule") return "规则";
    if (value === "llm") return "大模型";
  }
  if (key === "bidMin" || key === "bidMax") {
    return formatStrategyBid(value, key === "bidMin" ? 0.1 : 4);
  }
  return String(value);
}

function translateDayType(value?: unknown) {
  if (value === "WEEKDAY") return "工作日";
  if (value === "WEEKEND") return "周末";
  if (value === "HOLIDAY") return "节假日";
  return value == null || value === "" ? "-" : String(value);
}

function translateCalendarDayType(value?: unknown) {
  if (value === "HOLIDAY") return "节假日";
  if (value === "ADJUSTED_WORKDAY") return "调休工作日";
  return value == null || value === "" ? "-" : String(value);
}

function translateProfileStatus(value?: unknown) {
  if (value === "USABLE") return "可用";
  if (value === "UNAVAILABLE") return "不可用";
  if (value === "INSUFFICIENT") return "样本不足";
  return value == null || value === "" ? "-" : String(value);
}

function translateUnavailableReason(value?: unknown) {
  if (value === "PROFILE_MISSING") return "画像缺失";
  if (value === "SAMPLE_INSUFFICIENT") return "样本不足";
  return value == null || value === "" ? "-" : String(value);
}

function translateChangeSource(value?: unknown) {
  if (value === "H5") return "商户端";
  if (value === "ADMIN") return "管理后台";
  if (value === "SYSTEM") return "系统";
  return value == null || value === "" ? "-" : String(value);
}

function translateBidConstraintSource(value?: unknown) {
  if (value === "MEITUAN_ERROR") return "美团接口返回";
  return value == null || value === "" ? "-" : String(value);
}

function renderBooleanText(
  value: unknown,
  trueText: string,
  falseText: string,
) {
  if (value === true) return trueText;
  if (value === false) return falseText;
  return "-";
}

function renderTextWithTip(value?: unknown) {
  if (value == null || value === "") {
    return "-";
  }
  const text = String(value);
  return (
    <Tooltip title={text}>
      <span>{text}</span>
    </Tooltip>
  );
}

function renderHistoryStrategySummary(record: Record<string, unknown>) {
  const config = parseJsonObject(record.strategyConfigJson);
  if (!config) {
    return "-";
  }
  const summary = [
    translateStrategyValue("priority", config.priority),
    translateStrategyValue("timeMode", config.timeMode),
    config.rateMin != null || config.rateMax != null
      ? `${config.rateMin ?? "-"}-${config.rateMax ?? "-"}`
      : undefined,
    config.bidMin != null || config.bidMax != null
      ? `出价${formatStrategyBid(config.bidMin, 0.1)}-${formatStrategyBid(config.bidMax, 4)}`
      : "出价¥0.10-¥4.00",
  ].filter((item) => item && item !== "-");

  return summary.length > 0 ? summary.join(" / ") : "-";
}

function parsePeakWindows(value?: unknown) {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value as Record<string, unknown>[];
  }
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? (parsed as Record<string, unknown>[]) : [];
    } catch {
      return [];
    }
  }
  return [];
}

const PricingAnalysis: React.FC = () => {
  const { currentUser } = useAuth();
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const initialSearchValues = useMemo(() => buildDefaultSearchValues(), []);
  const [form] = Form.useForm<SearchFormValues>();
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [rows, setRows] = useState<PricingAnalysisRow[]>([]);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [queryValues, setQueryValues] =
    useState<SearchFormValues>(initialSearchValues);
  const [detailOpen, setDetailOpen] = useState(false);
  const [activeDetail, setActiveDetail] =
    useState<PricingAnalysisDetail | null>(null);
  const [advancedOpen, setAdvancedOpen] = useState(false);

  const canUsePage =
    currentUser?.activePortal === "ADMIN" &&
    (currentUser.permissions.includes("system:pricing-analysis:list") ||
      currentUser.roles.includes("super_admin"));

  const buildQueryParams = useCallback(
    (values: SearchFormValues) => {
      const slotStartTime = isCompact
        ? mergeDateAndClock(values.startDate, values.startClock)
        : values.slotRange?.[0];
      const slotEndTime = isCompact
        ? mergeDateAndClock(values.endDate, values.endClock, "end")
        : values.slotRange?.[1];
      return {
        keyword: values.keyword?.trim() || undefined,
        shopId: values.shopId,
        poiId: values.poiId?.trim() || undefined,
        shopName: values.shopName?.trim() || undefined,
        executeStatus: values.executeStatus,
        decisionAction: values.decisionAction,
        decisionReason: values.decisionReason?.trim() || undefined,
        logMarkdownStatus: values.logMarkdownStatus,
        slotStartTime: slotStartTime?.format("YYYY-MM-DD HH:mm:ss"),
        slotEndTime: slotEndTime?.format("YYYY-MM-DD HH:mm:ss"),
        pageNum,
        pageSize,
      };
    },
    [isCompact, pageNum, pageSize],
  );

  const loadRows = useCallback(async () => {
    if (!canUsePage) return;
    setLoading(true);
    try {
      const response = await getPricingAnalysisRows(
        buildQueryParams(queryValues),
      );
      setRows(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load pricing analysis rows:", error);
      message.error("加载定价分析失败");
    } finally {
      setLoading(false);
    }
  }, [buildQueryParams, canUsePage, queryValues]);

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  const handleSearch = async () => {
    if (isCompact) {
      const startDate = form.getFieldValue("startDate");
      const startClock = form.getFieldValue("startClock");
      const endDate = form.getFieldValue("endDate");
      const endClock = form.getFieldValue("endClock");
      if (startClock && !startDate) {
        message.warning("请先选择开始日期，再选择开始时间");
        return;
      }
      if (endClock && !endDate) {
        message.warning("请先选择结束日期，再选择结束时间");
        return;
      }
    }
    const values = await form.validateFields();
    setQueryValues(values);
    setPageNum(1);
  };

  const getPickerPopupContainer = useCallback(
    (triggerNode: HTMLElement) => triggerNode.parentElement ?? document.body,
    [],
  );

  const handleReset = () => {
    const nextValues = buildDefaultSearchValues();
    form.resetFields();
    form.setFieldsValue(nextValues);
    setQueryValues(nextValues);
    setPageNum(1);
    setPageSize(10);
  };

  const openDetail = useCallback(async (row: PricingAnalysisRow) => {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      setActiveDetail(await getPricingAnalysisDetail(row.id));
    } catch (error) {
      console.error("Failed to load pricing analysis detail:", error);
      message.error("加载定价详情失败");
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const renderMerchantLog = (markdown?: unknown, fallbackText?: unknown) => (
    <MarkdownLogPreview
      markdown={typeof markdown === "string" ? markdown : undefined}
      fallbackText={typeof fallbackText === "string" ? fallbackText : undefined}
    />
  );

  const columns = useMemo<ColumnsType<PricingAnalysisRow>>(
    () => [
      {
        title: "槽位时间",
        dataIndex: "slotTime",
        width: 170,
        render: formatDateTime,
      },
      {
        title: "门店",
        dataIndex: "shopName",
        width: 220,
        render: (_, row) => (
          <Space direction="vertical" size={0}>
            <span>{row.shopName || "-"}</span>
            <Text type="secondary">{row.poiId || row.shopId || "-"}</Text>
          </Space>
        ),
      },
      {
        title: "状态",
        dataIndex: "executeStatus",
        width: 90,
        render: renderStatus,
      },
      {
        title: "动作",
        dataIndex: "decisionAction",
        width: 90,
        render: renderAction,
      },
      {
        title: "原因",
        dataIndex: "decisionReason",
        width: 240,
        ellipsis: true,
        render: (_, row) =>
          renderDecisionReason(row.decisionReason, row.decisionReasonText),
      },
      {
        title: "出价",
        width: 130,
        render: (_, row) =>
          `${formatMoney(row.previousBidAmount)} -> ${formatMoney(row.nextBidAmount)}`,
      },
      {
        title: "花费",
        dataIndex: "totalCost",
        width: 100,
        render: formatMoney,
      },
      {
        title: "实收",
        dataIndex: "totalIncome",
        width: 100,
        render: formatMoney,
      },
      {
        title: "费用率",
        dataIndex: "totalCostRatio",
        width: 100,
        render: formatRatio,
      },
      {
        title: "日志状态",
        dataIndex: "logMarkdownStatus",
        width: 100,
        render: renderDecisionEnum,
      },
      {
        title: "操作",
        fixed: "right",
        width: 90,
        render: (_, row) => (
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => void openDetail(row)}
          >
            分析
          </Button>
        ),
      },
    ],
    [openDetail],
  );

  const renderObjectDescriptions = (data?: Record<string, unknown>) => {
    const entries = Object.entries(compactObject(data));
    if (entries.length === 0) {
      return <Text type="secondary">暂无数据</Text>;
    }
    return (
      <div
        style={{
          display: "grid",
          gridTemplateColumns: isCompact ? "1fr" : "repeat(2, minmax(0, 1fr))",
          gap: 8,
          alignItems: "stretch",
        }}
      >
        {entries.map(([key, value]) => (
          <div
            key={key}
            style={{
              ...detailCellStyle,
              gridColumn:
                !isCompact && isStructuredValue(value) ? "1 / -1" : undefined,
            }}
          >
            <div style={detailLabelStyle}>{labelOf(key)}</div>
            <div style={detailValueStyle}>
              {renderFieldValue(key, value, data)}
            </div>
          </div>
        ))}
      </div>
    );
  };

  const hasObjectDescriptionData = (data?: Record<string, unknown>) =>
    Object.keys(compactObject(data)).length > 0;

  const renderExecutionMetricsModule = (
    metricsContext?: Record<string, unknown>,
  ) => {
    const metricSnapshot = parseJsonObject(metricsContext?.metricSnapshot);
    const realtimeSnapshot = parseJsonObject(metricsContext?.realtimeSnapshot);

    return (
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        {metricSnapshot ? (
          <div>
            <div style={{ marginTop: 8 }}>
              {renderObjectDescriptions({
                指标日期: metricSnapshot.bizDate,
                槽位时间: formatDateTime(
                  metricSnapshot.slotTime as string | undefined,
                ),
                主账户余额: formatMoney(
                  metricSnapshot.primaryBalance as number | undefined,
                ),
                通用余额: formatMoney(
                  metricSnapshot.commonBalance as number | undefined,
                ),
                推广余额: formatMoney(
                  metricSnapshot.promotionBalance as number | undefined,
                ),
                花费累计计数: formatMoney(
                  metricSnapshot.costCounterValue as number | undefined,
                ),
                指标状态: translateMetricsTrusted(
                  metricSnapshot.metricsTrusted,
                ),
                不可信原因: translateUntrustedReason(
                  metricSnapshot.untrustedReason,
                ),
                快照时间: formatDateTime(
                  metricSnapshot.createTime as string | undefined,
                ),
              })}
            </div>
          </div>
        ) : (
          <Text type="secondary">暂无指标快照</Text>
        )}

        {realtimeSnapshot ? (
          <div>
            <Text strong>执行关联实时广告快照</Text>
            <div style={{ marginTop: 8 }}>
              {renderObjectDescriptions({
                广告渠道: translateRealtimeChannel(realtimeSnapshot.channel),
                指标日期: realtimeSnapshot.bizDate,
                槽位时间: formatDateTime(
                  realtimeSnapshot.slotTime as string | undefined,
                ),
                当前小时: realtimeSnapshot.currentHourRange,
                当前小时曝光: realtimeSnapshot.hourShowCount,
                当前小时点击: realtimeSnapshot.hourClickCount,
                当前小时消耗: formatCents(realtimeSnapshot.hourDebitCents),
                当日曝光: realtimeSnapshot.dayShowCount,
                当日点击: realtimeSnapshot.dayClickCount,
                当日消耗: formatCents(realtimeSnapshot.dayDebitCents),
                快照时间: formatDateTime(
                  realtimeSnapshot.createTime as string | undefined,
                ),
              })}
            </div>
          </div>
        ) : (
          <Text type="secondary">暂无实时广告快照</Text>
        )}
      </Space>
    );
  };

  const renderStatusCheckModule = (
    statusCheckContext?: Record<string, unknown>,
  ) => {
    const context = statusCheckContext || {};
    if (!hasObjectDescriptionData(context)) {
      return <Text type="secondary">暂无执行前远端推广状态检查</Text>;
    }
    return renderObjectDescriptions({
      推广状态: translateRemotePromotionStatus(context.status),
      远端出价: formatCents(context.bid),
      推广预算: formatMoney(context.budget as number | undefined),
      已用预算: formatMoney(context.budgetUsed as number | undefined),
      剩余预算: formatMoney(
        context.availableBudget as number | undefined,
      ),
      响应码: context.responseCode,
      响应信息: context.responseMsg,
      业务错误信息: context.responseDataErrMsg,
    });
  };

  const renderBidConstraintModule = (
    bidConstraintContext?: Record<string, unknown>,
  ) => {
    const context = bidConstraintContext || {};
    return renderObjectDescriptions({
      是否生效: context.active,
      全局出价上限: formatMoney(context.globalMaxBidAmount as number | undefined),
      门店出价上限: formatMoney(context.shopMaxBidAmount as number | undefined),
      当前有效出价上限: formatMoney(
        context.effectiveMaxBidAmount as number | undefined,
      ),
      来源: translateBidConstraintSource(context.source),
      观测时间: formatDateTime(context.observedAt as string | undefined),
      过期时间: formatDateTime(context.expireAt as string | undefined),
      来源信息: context.rawMessage,
    });
  };

  const renderExposureWeakGrowthRollbackModule = (
    decisionContext?: Record<string, unknown>,
  ) => {
    const context = decisionContext || {};
    const hasRollbackData = [
      "exposureWeakGrowthRollbackApplied",
      "exposureWeakGrowthConsecutiveCount",
      "exposureWeakGrowthRollbackUnavailableReason",
      "exposureWeakGrowthBestSlotTime",
      "exposureWeakGrowthBestFollowupExposure",
      "exposureWeakGrowthBestPreviousBidAmount",
      "exposureWeakGrowthRollbackBidAmount",
    ].some((key) => context[key] !== null && context[key] !== undefined && context[key] !== "");

    if (!hasRollbackData) {
      return <Text type="secondary">暂无连续弱增长保护数据</Text>;
    }

    const bestPreviousBidAmount = context.exposureWeakGrowthBestPreviousBidAmount;
    const rollbackBidAmount = context.exposureWeakGrowthRollbackBidAmount;

    return renderObjectDescriptions({
      连续弱增长保护命中: context.exposureWeakGrowthRollbackApplied,
      连续曝光加价轮数: context.exposureWeakGrowthConsecutiveCount,
      连续弱增长保护不可用原因:
        context.exposureWeakGrowthRollbackUnavailableReason == null ||
        context.exposureWeakGrowthRollbackUnavailableReason === ""
          ? undefined
          : decisionEnumTextMap[
              String(context.exposureWeakGrowthRollbackUnavailableReason)
            ] || String(context.exposureWeakGrowthRollbackUnavailableReason),
      回退参考槽位:
        context.exposureWeakGrowthBestSlotTime == null ||
        context.exposureWeakGrowthBestSlotTime === ""
          ? undefined
          : formatDateTime(context.exposureWeakGrowthBestSlotTime as string),
      回退参考后续曝光: context.exposureWeakGrowthBestFollowupExposure,
      回退参考前置出价:
        bestPreviousBidAmount == null || bestPreviousBidAmount === ""
          ? undefined
          : formatMoney(bestPreviousBidAmount as number),
      连续弱增长回退出价:
        rollbackBidAmount == null || rollbackBidAmount === ""
          ? undefined
          : formatMoney(rollbackBidAmount as number),
    });
  };

  const renderCurrentOrderRecalculationModule = (
    metricsContext?: Record<string, unknown>,
  ) => {
    const recalculation = omitObjectKeys(metricsContext, [
      "metricSnapshot",
      "realtimeSnapshot",
    ]);
    return (
      <Space direction="vertical" style={{ width: "100%" }} size="small">
        <Text type="secondary">
          以下订单指标是在打开详情时按当前订单表重新聚合的辅助核对数据，不代表当次定价任务写入的决策快照。
        </Text>
        {hasObjectDescriptionData(recalculation) ? (
          renderObjectDescriptions(recalculation)
        ) : (
          <Text type="secondary">暂无当前订单复算数据</Text>
        )}
      </Space>
    );
  };

  const renderRightsTable = (rights?: unknown) => {
    const rows = Array.isArray(rights)
      ? (rights as Record<string, unknown>[])
      : [];
    if (rows.length === 0) {
      return <Text type="secondary">暂无权益数据</Text>;
    }
    return (
      <Table<Record<string, unknown>>
        rowKey={(record, index) =>
          String(record.id ?? `${record.serviceCode ?? "right"}-${index}`)
        }
        size="small"
        pagination={false}
        dataSource={rows}
        scroll={{ x: 760 }}
        columns={[
          {
            title: "权益编码",
            dataIndex: "serviceCode",
            width: 150,
            render: (value) => value || "-",
          },
          {
            title: "到期时间",
            dataIndex: "expireAt",
            width: 170,
            render: formatDateTime,
          },
          {
            title: "来源版本",
            dataIndex: "sourceVersion",
            width: 130,
            render: (value) => value || "-",
          },
          {
            title: "最近订单",
            dataIndex: "lastOrderId",
            width: 160,
            ellipsis: true,
            render: (value) => value || "-",
          },
        ]}
      />
    );
  };

  const renderStrategyHistoryTable = (histories?: unknown) => {
    const rows = Array.isArray(histories)
      ? (histories as Record<string, unknown>[])
      : [];
    if (rows.length === 0) {
      return <Text type="secondary">暂无配置历史</Text>;
    }
    return (
      <Table<Record<string, unknown>>
        rowKey={(record, index) =>
          String(record.id ?? record.versionNo ?? `history-${index}`)
        }
        size="small"
        pagination={false}
        dataSource={rows}
        scroll={{ x: 1120 }}
        columns={[
          {
            title: "版本",
            dataIndex: "versionNo",
            width: 90,
            render: (value) => value ?? "-",
          },
          {
            title: "来源",
            dataIndex: "changeSource",
            width: 110,
            render: translateChangeSource,
          },
          {
            title: "推广开关",
            dataIndex: "promotionEnabled",
            width: 100,
            render: (value) => renderBooleanText(value, "开启", "关闭"),
          },
          {
            title: "配置状态",
            dataIndex: "strategyConfigured",
            width: 100,
            render: (value) => renderBooleanText(value, "已配置", "未配置"),
          },
          {
            title: "策略摘要",
            width: 320,
            ellipsis: true,
            render: (_, record) => renderHistoryStrategySummary(record),
          },
          {
            title: "策略Hash",
            dataIndex: "strategyConfigHash",
            width: 180,
            ellipsis: true,
            render: renderTextWithTip,
          },
          {
            title: "操作人",
            width: 140,
            ellipsis: true,
            render: (_, record) =>
              record.operatorDisplayName ||
              record.operatorName ||
              record.operatorUserId ||
              record.operatorOpenId ||
              "-",
          },
          {
            title: "变更时间",
            dataIndex: "createTime",
            width: 170,
            render: formatDateTime,
          },
        ]}
      />
    );
  };

  const renderPowerConsumeTable = (powerConsumes?: unknown) => {
    const rows = Array.isArray(powerConsumes)
      ? (powerConsumes as Record<string, unknown>[])
      : [];
    if (rows.length === 0) {
      return <Text type="secondary">暂无算力消费记录</Text>;
    }
    return (
      <Table<Record<string, unknown>>
        rowKey={(record, index) =>
          String(record.id ?? record.consumeNo ?? `power-${index}`)
        }
        size="small"
        pagination={false}
        dataSource={rows}
        scroll={{ x: 980 }}
        columns={[
          {
            title: "消费流水",
            dataIndex: "consumeNo",
            width: 180,
            ellipsis: true,
            render: renderTextWithTip,
          },
          {
            title: "来源类型",
            dataIndex: "sourceType",
            width: 130,
            render: (value) => value || "-",
          },
          {
            title: "来源ID",
            dataIndex: "sourceId",
            width: 170,
            ellipsis: true,
            render: renderTextWithTip,
          },
          {
            title: "请求号",
            dataIndex: "requestNo",
            width: 170,
            ellipsis: true,
            render: renderTextWithTip,
          },
          {
            title: "扣减算力",
            dataIndex: "powerAmount",
            width: 100,
            render: (value) => value ?? "-",
          },
          {
            title: "扣减前",
            dataIndex: "balanceBefore",
            width: 100,
            render: (value) => value ?? "-",
          },
          {
            title: "扣减后",
            dataIndex: "balanceAfter",
            width: 100,
            render: (value) => value ?? "-",
          },
          {
            title: "备注",
            dataIndex: "remark",
            width: 180,
            ellipsis: true,
            render: renderTextWithTip,
          },
          {
            title: "消费时间",
            dataIndex: "createTime",
            width: 170,
            render: formatDateTime,
          },
        ]}
      />
    );
  };

  const renderAsyncTaskDescriptions = (
    executionContext?: Record<string, unknown>,
  ) => {
    const taskData = omitObjectKeys(executionContext, ["powerConsumes"]);
    if (!hasObjectDescriptionData(taskData)) {
      return <Text type="secondary">暂无异步任务</Text>;
    }
    return renderObjectDescriptions(taskData);
  };

  const renderStrategyConfigTable = (
    title: string,
    strategyConfigJson?: unknown,
  ) => {
    const config = parseJsonObject(strategyConfigJson);
    if (!config) {
      return (
        <div>
          <Text strong>{title}</Text>
          <div style={{ marginTop: 8 }}>
            <Text type="secondary">暂无策略配置</Text>
          </div>
        </div>
      );
    }
    const rows = [
      "priority",
      "timeMode",
      "rateMode",
      "rateMin",
      "rateMax",
      "bidMin",
      "bidMax",
      "decisionMode",
    ]
      .filter(
        (key) =>
          config[key] !== undefined &&
          config[key] !== null &&
          config[key] !== "",
      )
      .map((key) => ({
        key,
        label:
          detailLabelMap[key] ||
          {
            priority: "推广策略",
            timeMode: "推广时段",
            rateMode: "费率模式",
            rateMin: "最低费率",
            rateMax: "最高费率",
            bidMin: "最小出价",
            bidMax: "最大出价",
            decisionMode: "决策模式",
          }[key],
        value: translateStrategyValue(key, config[key]),
      }));
    const customSlots = Array.isArray(config.customSlots)
      ? (config.customSlots as Record<string, unknown>[])
      : [];

    return (
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        <Text strong>{title}</Text>
        <Table
          rowKey="key"
          size="small"
          pagination={false}
          dataSource={rows}
          columns={[
            { title: "配置项", dataIndex: "label", width: 160 },
            { title: "配置值", dataIndex: "value" },
          ]}
        />
        {customSlots.length > 0 ? (
          <Table<Record<string, unknown>>
            rowKey={(_, index) => `slot-${index}`}
            size="small"
            pagination={false}
            dataSource={customSlots}
            columns={[
              {
                title: "自定义时段",
                render: (_, __, index) => index + 1,
                width: 110,
              },
              {
                title: "开始",
                dataIndex: "start",
                width: 140,
                render: (value) => value || "-",
              },
              {
                title: "结束",
                dataIndex: "end",
                width: 140,
                render: (value) => value || "-",
              },
            ]}
          />
        ) : null}
      </Space>
    );
  };

  const renderStrategyConfigDescriptions = (strategyConfigJson?: unknown) => {
    const config = parseJsonObject(strategyConfigJson);
    if (!config) {
      return <Text type="secondary">暂无策略配置</Text>;
    }
    return renderObjectDescriptions({
      推广策略: translateStrategyValue("priority", config.priority),
      推广时段: translateStrategyValue("timeMode", config.timeMode),
      费率模式: translateStrategyValue("rateMode", config.rateMode),
      最低费率: config.rateMin,
      最高费率: config.rateMax,
      最小出价: formatStrategyBid(config.bidMin, 0.1),
      最大出价: formatStrategyBid(config.bidMax, 4),
    });
  };

  const renderPeakSnapshotModule = (
    peakProfileContext?: Record<string, unknown>,
  ) => {
    const snapshot = parseJsonObject(peakProfileContext?.snapshot);
    const snapshotData = {
      执行日型: translateDayType(snapshot?.dayType),
      采用画像日型: translateDayType(snapshot?.profileDayType),
      日历类型: translateCalendarDayType(snapshot?.calendarDayType),
      日历说明: snapshot?.holidayName,
      画像状态: translateProfileStatus(snapshot?.profileStatus),
      是否启用高峰控制: snapshot?.gatingEnabled,
      是否命中高峰: snapshot?.peakMatched,
      命中高峰窗口: snapshot?.matchedRange,
      样本天数: snapshot?.sampleDayCount,
      样本订单数: snapshot?.sampleOrderCount,
      不可用原因: translateUnavailableReason(snapshot?.unavailableReason),
      高峰轨道阶段: snapshot?.peakBidPhase,
    };

    if (!hasObjectDescriptionData(snapshotData)) {
      return <Text type="secondary">暂无执行时高峰判断快照</Text>;
    }

    return renderObjectDescriptions(snapshotData);
  };

  const renderCurrentPeakProfileModule = (
    peakProfileContext?: Record<string, unknown>,
  ) => {
    const profiles = Array.isArray(peakProfileContext?.profiles)
      ? (peakProfileContext.profiles as Record<string, unknown>[])
      : [];
    const profileWindows = profiles.flatMap((profile) =>
      parsePeakWindows(profile.peakWindowsJson).map((window, index) => ({
        key: `${profile.dayType ?? "UNKNOWN"}-${index}`,
        dayType: profile.dayType,
        profileStatus: profile.profileStatus,
        startTime: window.startTime,
        endTime: window.endTime,
        orderCount: window.orderCount,
      })),
    );

    return (
      <Space direction="vertical" style={{ width: "100%" }} size="middle">
        <Text type="secondary">
          当前画像记录和高峰窗口来自当前画像表，仅用于辅助核对，不代表当次执行时画像表完整快照。
        </Text>
        <Collapse
          size="small"
          items={[
            {
              key: "peak-profile-records",
              label: "画像记录",
              children: (
                <Table<Record<string, unknown>>
                  rowKey={(record, index) =>
                    String(
                      record.id ?? `${record.dayType ?? "profile"}-${index}`,
                    )
                  }
                  size="small"
                  pagination={false}
                  dataSource={profiles}
                  scroll={{ x: 760 }}
                  columns={[
                    {
                      title: "画像日型",
                      dataIndex: "dayType",
                      width: 110,
                      render: translateDayType,
                    },
                    {
                      title: "状态",
                      dataIndex: "profileStatus",
                      width: 100,
                      render: translateProfileStatus,
                    },
                    {
                      title: "回看天数",
                      dataIndex: "lookbackDays",
                      width: 90,
                      render: (value) => value ?? "-",
                    },
                    {
                      title: "样本天数",
                      dataIndex: "sampleDayCount",
                      width: 90,
                      render: (value) => value ?? "-",
                    },
                    {
                      title: "样本订单数",
                      dataIndex: "sampleOrderCount",
                      width: 110,
                      render: (value) => value ?? "-",
                    },
                    {
                      title: "生成时间",
                      dataIndex: "generatedAt",
                      width: 170,
                      render: formatDateTime,
                    },
                    {
                      title: "不可用原因",
                      dataIndex: "unavailableReason",
                      width: 130,
                      render: translateUnavailableReason,
                    },
                  ]}
                />
              ),
            },
            {
              key: "peak-profile-windows",
              label: "高峰窗口",
              children: (
                <Table<Record<string, unknown>>
                  rowKey="key"
                  size="small"
                  pagination={false}
                  dataSource={profileWindows}
                  columns={[
                    {
                      title: "画像日型",
                      dataIndex: "dayType",
                      width: 110,
                      render: translateDayType,
                    },
                    {
                      title: "画像状态",
                      dataIndex: "profileStatus",
                      width: 100,
                      render: translateProfileStatus,
                    },
                    {
                      title: "开始",
                      dataIndex: "startTime",
                      width: 120,
                      render: (value) => value || "-",
                    },
                    {
                      title: "结束",
                      dataIndex: "endTime",
                      width: 120,
                      render: (value) => value || "-",
                    },
                    {
                      title: "样本订单数",
                      dataIndex: "orderCount",
                      width: 120,
                      render: (value) => value ?? "-",
                    },
                  ]}
                />
              ),
            },
          ]}
        />
      </Space>
    );
  };

  if (!canUsePage) {
    return (
      <PageContainer title="定价分析">
        <Text type="secondary">当前工作台无权访问定价分析。</Text>
      </PageContainer>
    );
  }

  return (
    <PageContainer title="定价分析">
      <div className={pageStyles.toolbar}>
        <Form<SearchFormValues>
          form={form}
          initialValues={initialSearchValues}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={() => void handleSearch()}
        >
          <Form.Item name="keyword">
            <Input
              placeholder="门店/poiId/shopId"
              allowClear
              style={getResponsiveControlStyle(isCompact, 180)}
            />
          </Form.Item>
          {!isCompact || advancedOpen ? (
            <>
              <Form.Item name="executeStatus">
                <Select
                  allowClear
                  placeholder="执行状态"
                  options={executeStatusOptions}
                  style={getResponsiveControlStyle(isCompact, 120)}
                />
              </Form.Item>
              <Form.Item name="decisionAction">
                <Select
                  allowClear
                  placeholder="决策动作"
                  options={decisionActionOptions}
                  style={getResponsiveControlStyle(isCompact, 120)}
                />
              </Form.Item>
              <Form.Item name="decisionReason">
                <Input
                  allowClear
                  placeholder="决策原因"
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              <Form.Item name="logMarkdownStatus">
                <Select
                  allowClear
                  placeholder="日志状态"
                  options={logStatusOptions}
                  style={getResponsiveControlStyle(isCompact, 120)}
                />
              </Form.Item>
              {isCompact ? (
                <>
                  <Form.Item
                    label="开始时间"
                    className={pageStyles.compactFull}
                    style={{ marginBottom: 0 }}
                  >
                    <Space style={{ width: "100%" }} size={8}>
                      <Form.Item name="startDate" noStyle>
                        <DatePicker
                          allowClear
                          format="YYYY-MM-DD"
                          style={{ width: "100%" }}
                          getPopupContainer={getPickerPopupContainer}
                          onChange={(value) => {
                            if (!value) {
                              form.setFieldValue("startClock", undefined);
                            }
                          }}
                        />
                      </Form.Item>
                      <Form.Item noStyle shouldUpdate>
                        {({ getFieldValue }) => (
                          <Form.Item name="startClock" noStyle>
                            <TimePicker
                              allowClear
                              format="HH:mm:ss"
                              style={{ width: 120, flexShrink: 0 }}
                              getPopupContainer={getPickerPopupContainer}
                              disabled={!getFieldValue("startDate")}
                              placeholder="时间"
                            />
                          </Form.Item>
                        )}
                      </Form.Item>
                    </Space>
                  </Form.Item>
                  <Form.Item
                    label="结束时间"
                    className={pageStyles.compactFull}
                    style={{ marginBottom: 0 }}
                  >
                    <Space style={{ width: "100%" }} size={8}>
                      <Form.Item name="endDate" noStyle>
                        <DatePicker
                          allowClear
                          format="YYYY-MM-DD"
                          style={{ width: "100%" }}
                          getPopupContainer={getPickerPopupContainer}
                          onChange={(value) => {
                            if (!value) {
                              form.setFieldValue("endClock", undefined);
                            }
                          }}
                        />
                      </Form.Item>
                      <Form.Item noStyle shouldUpdate>
                        {({ getFieldValue }) => (
                          <Form.Item name="endClock" noStyle>
                            <TimePicker
                              allowClear
                              format="HH:mm:ss"
                              style={{ width: 120, flexShrink: 0 }}
                              getPopupContainer={getPickerPopupContainer}
                              disabled={!getFieldValue("endDate")}
                              placeholder="时间"
                            />
                          </Form.Item>
                        )}
                      </Form.Item>
                    </Space>
                  </Form.Item>
                </>
              ) : (
                <Form.Item name="slotRange">
                  <RangePicker
                    showTime
                    style={getResponsiveControlStyle(isCompact, 320)}
                    getPopupContainer={getPickerPopupContainer}
                  />
                </Form.Item>
              )}
            </>
          ) : null}
          <Space className={pageStyles.searchActions}>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => void loadRows()}>
              刷新
            </Button>
            {isCompact ? (
              <Button
                onClick={() => setAdvancedOpen((open) => !open)}
                icon={advancedOpen ? <UpOutlined /> : <DownOutlined />}
              >
                {advancedOpen ? "收起筛选" : "更多筛选"}
              </Button>
            ) : null}
          </Space>
        </Form>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1300 }}
        pagination={{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (nextPage, nextSize) => {
            setPageNum(nextPage);
            setPageSize(nextSize);
          },
        }}
        style={{ marginTop: 16 }}
      />

      <Drawer
        title="单次定价分析"
        open={detailOpen}
        width={isCompact ? "100%" : 920}
        onClose={() => setDetailOpen(false)}
        loading={detailLoading}
        destroyOnClose
      >
        {activeDetail && (
          <Space direction="vertical" size="large" style={{ width: "100%" }}>
            <Collapse
              defaultActiveKey={["execution-snapshot"]}
              items={[
                {
                  key: "execution-snapshot",
                  label: "执行时快照",
                  children: (
                    <Space
                      direction="vertical"
                      size="large"
                      style={{ width: "100%" }}
                    >
                      <section>
                        <Typography.Title level={5}>执行结论</Typography.Title>
                        {renderObjectDescriptions(
                          omitObjectKeys(activeDetail.recordSummary, [
                            "id",
                            "shopId",
                            "windowKey",
                            "requestNo",
                            "logTaskId",
                            "markdownMessage",
                            "decisionReasonText",
                          ]),
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          执行时策略
                        </Typography.Title>
                        <Space
                          direction="vertical"
                          style={{ width: "100%" }}
                          size="middle"
                        >
                          {renderStrategyConfigDescriptions(
                            activeDetail.strategyContext
                              ?.executionStrategyConfigJson,
                          )}
                          {renderObjectDescriptions({
                            策略是否与当前一致: activeDetail.strategyContext
                              ?.sameAsCurrent
                              ? "一致"
                              : "不一致或无法判断",
                          })}
                        </Space>
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          执行关联指标快照
                        </Typography.Title>
                        {renderExecutionMetricsModule(
                          activeDetail.metricsContext,
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          执行时高峰判断
                        </Typography.Title>
                        {renderPeakSnapshotModule(
                          activeDetail.peakProfileContext,
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          执行前远端推广状态检查
                        </Typography.Title>
                        {renderStatusCheckModule(
                          activeDetail.statusCheckContext,
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>决策链路</Typography.Title>
                        {hasObjectDescriptionData(
                          activeDetail.decisionContext,
                        ) ? (
                          <Space
                            direction="vertical"
                            style={{ width: "100%" }}
                            size="middle"
                          >
                            <div>
                              <Text strong>连续弱增长保护</Text>
                              <div style={{ marginTop: 8 }}>
                                {renderExposureWeakGrowthRollbackModule(
                                  activeDetail.decisionContext,
                                )}
                              </div>
                            </div>
                            <div>
                              <Text strong>AI 出价范围</Text>
                              <div style={{ marginTop: 8 }}>
                                {renderObjectDescriptions({
                                  userBidMinAmount:
                                    activeDetail.decisionContext
                                      ?.userBidMinAmount,
                                  userBidMaxAmount:
                                    activeDetail.decisionContext
                                      ?.userBidMaxAmount,
                                  effectiveMaxBidAmount:
                                    activeDetail.decisionContext
                                      ?.effectiveMaxBidAmount,
                                  bidBeforeUserRangeClamp:
                                    activeDetail.decisionContext
                                      ?.bidBeforeUserRangeClamp,
                                  bidAfterUserRangeClamp:
                                    activeDetail.decisionContext
                                      ?.bidAfterUserRangeClamp,
                                })}
                              </div>
                            </div>
                            <div>
                              <Text strong>完整决策字段</Text>
                              <div style={{ marginTop: 8 }}>
                                {renderObjectDescriptions(
                                  omitObjectKeys(activeDetail.decisionContext, [
                                    "decisionMode",
                                    "peakWindowKey",
                                    "nextPeakWindowKey",
                                    "userBidMinAmount",
                                    "userBidMaxAmount",
                                    "effectiveMaxBidAmount",
                                    "bidBeforeUserRangeClamp",
                                    "bidAfterUserRangeClamp",
                                    "exposureWeakGrowthRollbackApplied",
                                    "exposureWeakGrowthConsecutiveCount",
                                    "exposureWeakGrowthRollbackUnavailableReason",
                                    "exposureWeakGrowthBestSlotTime",
                                    "exposureWeakGrowthBestFollowupExposure",
                                    "exposureWeakGrowthBestPreviousBidAmount",
                                    "exposureWeakGrowthRollbackBidAmount",
                                  ]),
                                )}
                              </div>
                            </div>
                          </Space>
                        ) : (
                          <Text type="secondary">暂无决策链路快照</Text>
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>商家日志</Typography.Title>
                        {renderMerchantLog(
                          activeDetail.recordSummary?.markdownMessage,
                          activeDetail.recordSummary?.errorMsg,
                        )}
                      </section>
                    </Space>
                  ),
                },
                {
                  key: "current-supporting-data",
                  label: "当前辅助数据",
                  children: (
                    <Space
                      direction="vertical"
                      size="large"
                      style={{ width: "100%" }}
                    >
                      <Text type="secondary">
                        以下内容来自当前本地数据库查询或按当前订单表复算，用于辅助核对和排障，不代表当次执行时快照。
                      </Text>
                      <section>
                        <Typography.Title level={5}>
                          当前门店与授权
                        </Typography.Title>
                        {renderObjectDescriptions(
                          omitObjectKeys(activeDetail.shopContext, ["rights"]),
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>当前出价上限</Typography.Title>
                        {renderBidConstraintModule(
                          activeDetail.bidConstraintContext,
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>当前权益</Typography.Title>
                        {renderRightsTable(activeDetail.shopContext?.rights)}
                      </section>
                      <section>
                        <Typography.Title level={5}>当前策略</Typography.Title>
                        <Space
                          direction="vertical"
                          style={{ width: "100%" }}
                          size="middle"
                        >
                          {renderObjectDescriptions({
                            当前推广开关:
                              activeDetail.strategyContext
                                ?.currentPromotionEnabled,
                            当前策略已配置:
                              activeDetail.strategyContext
                                ?.currentStrategyConfigured,
                            当前策略Hash:
                              activeDetail.strategyContext
                                ?.currentStrategyConfigHash,
                            执行时策略Hash:
                              activeDetail.strategyContext
                                ?.executionStrategyConfigHash,
                            策略是否与当前一致: activeDetail.strategyContext
                              ?.sameAsCurrent
                              ? "一致"
                              : "不一致或无法判断",
                          })}
                          {renderStrategyConfigTable(
                            "当前策略",
                            activeDetail.strategyContext
                              ?.currentStrategyConfigJson,
                          )}
                        </Space>
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          当前画像记录与高峰窗口
                        </Typography.Title>
                        {renderCurrentPeakProfileModule(
                          activeDetail.peakProfileContext,
                        )}
                      </section>
                      <section>
                        <Typography.Title level={5}>
                          当前订单复算
                        </Typography.Title>
                        {renderCurrentOrderRecalculationModule(
                          activeDetail.metricsContext,
                        )}
                      </section>
                      <Collapse
                        size="small"
                        items={[
                          {
                            key: "async-tasks",
                            label: "异步任务",
                            children: renderAsyncTaskDescriptions(
                              activeDetail.executionContext,
                            ),
                          },
                          {
                            key: "power-consumes",
                            label: "算力消费记录",
                            children: renderPowerConsumeTable(
                              activeDetail.executionContext?.powerConsumes,
                            ),
                          },
                          {
                            key: "history",
                            label: "配置历史",
                            children: renderStrategyHistoryTable(
                              activeDetail.strategyContext?.histories,
                            ),
                          },
                          {
                            key: "raw",
                            label: "完整原始 JSON",
                            children: (
                              <Space
                                direction="vertical"
                                style={{ width: "100%" }}
                              >
                                {Object.entries(
                                  activeDetail.rawPayloads || {},
                                ).map(([key, value]) => (
                                  <div key={key}>
                                    <Text strong>{labelOf(key)}</Text>
                                    <div style={{ marginTop: 8 }}>
                                      {renderJsonBlock(value)}
                                    </div>
                                  </div>
                                ))}
                              </Space>
                            ),
                          },
                        ]}
                      />
                    </Space>
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Drawer>
    </PageContainer>
  );
};

export default PricingAnalysis;
