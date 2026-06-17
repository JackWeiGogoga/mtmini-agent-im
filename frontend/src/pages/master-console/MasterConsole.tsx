import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Segmented,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import {
  DownloadOutlined,
  EditOutlined,
  LockOutlined,
  ReloadOutlined,
  SettingOutlined,
  ShopOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { createStyles } from "antd-style";
import dayjs from "dayjs";
import { useNavigate } from "react-router-dom";
import { message, modal } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import MarkdownLogPreview from "@/components/MarkdownLogPreview";
import { useAuth } from "@/contexts/AuthContext";
import { useResponsive } from "@/hooks";
import { usePermission } from "@/hooks/usePermission";
import {
  exportMasterConsoleShops,
  getMasterConsolePromotionLogs,
  getMasterConsolePromotionLogSummary,
  getMasterConsolePromotionSettings,
  getMasterConsoleShops,
  getMasterConsoleVisibleUsers,
  updateMasterConsoleManualPromotionBid,
  updateMasterConsolePromotionSettings,
} from "@/services/master-console-api";
import { reportAdminBehaviorEvent } from "@/services/user-behavior-analytics-api";
import { startImpersonation, stopImpersonation } from "@/services/auth-api";
import type {
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  PromotionLogRecord,
  PromotionLogSummary,
  PromotionSettings,
  PromotionStatus,
  PromotionStrategyConfig,
  ShopBindingDomain,
  ShopBindingStatus,
  VisibleUserOption,
  DashboardStatsRange,
} from "@/types";
import {
  getResponsiveControlStyle,
  getResponsiveDrawerWidth,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";
import { formatPowerWithUnit } from "@/utils/power-format";

const { Text, Title } = Typography;

type StrategyFormValues = {
  priority: "rate" | "exposure";
  timeMode: "auto" | "business" | "custom";
  rateMin: number;
  rateMax: number;
  bidRange: [number, number];
  customSlots?: {
    start?: string;
    end?: string;
    startHour?: string;
    startMinute?: string;
    endHour?: string;
    endMinute?: string;
  }[];
};

type ManualBidFormValues = {
  bidAmount: number;
};

const DEFAULT_RATE_MIN = 5;
const DEFAULT_RATE_MAX = 10;
const EXPOSURE_RATE_MIN = 1;
const EXPOSURE_RATE_MAX = 20;
const DEFAULT_BID_MIN = 0.1;
const DEFAULT_BID_MAX = 4;
const BID_STEP = 0.1;
const USER_SEARCH_DEBOUNCE_MS = 300;
const ADMIN_BINDING_DOMAIN_OPTIONS: {
  label: string;
  value: ShopBindingDomain;
}[] = [
  { label: "全部", value: "ALL" },
  { label: "普通商户", value: "ORDINARY" },
  { label: "网销用户", value: "SALES" },
];
const BINDING_STATUS_OPTIONS: { label: string; value: ShopBindingStatus }[] = [
  { label: "全部状态", value: "ALL" },
  { label: "未绑定", value: "UNBOUND" },
  { label: "已绑定", value: "BOUND" },
  { label: "双绑定", value: "BOTH" },
];
const PROMOTION_STATUS_OPTIONS: { label: string; value: PromotionStatus }[] = [
  { label: "全部开关", value: "ALL" },
  { label: "推广开启", value: "ENABLED" },
  { label: "推广关闭", value: "DISABLED" },
];
const MINUTES_PER_DAY = 24 * 60;
const MANUAL_BID_MIN_AMOUNT = 0.1;
const MANUAL_BID_STEP = 0.1;
const DASHBOARD_STATS_RANGE_OPTIONS: {
  label: string;
  value: DashboardStatsRange;
  compareLabel: string;
}[] = [
  { label: "今日", value: "TODAY", compareLabel: "较昨日同期" },
  { label: "近7天", value: "LAST_7_DAYS", compareLabel: "较前7天同期" },
  { label: "近30天", value: "LAST_30_DAYS", compareLabel: "较前30天同期" },
];
const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => {
  const value = String(hour).padStart(2, "0");
  return { label: value, value };
});
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) => {
  const value = String(minute).padStart(2, "0");
  return { label: value, value };
});
const STATUS_TOOLTIP_STYLES = {
  body: {
    width: 420,
    maxWidth: 480,
  },
} as const;

const useStyles = createStyles(({ token }) => ({
  statsRangeSegmented: {
    background: "rgba(5, 23, 41, 0.08)",
    padding: 4,
    borderRadius: 10,
    "& .ant-segmented-item": {
      minWidth: 72,
      color: token.colorTextSecondary,
      fontWeight: 500,
      transition: "all 0.2s ease",
    },
    "& .ant-segmented-item-selected": {
      background: "#ffffff",
      color: token.colorText,
      fontWeight: 700,
      boxShadow:
        "0 0 0 1px rgba(5, 23, 41, 0.08), 0 4px 10px rgba(5, 23, 41, 0.10)",
    },
  },
}));

function toMinute(value?: string) {
  if (!value) {
    return Number.NaN;
  }

  const [hourText, minuteText] = value.split(":");
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (
    Number.isNaN(hour) ||
    Number.isNaN(minute) ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59
  ) {
    return Number.NaN;
  }

  return hour * 60 + minute;
}

function formatMinute(minute: number) {
  const normalizedMinute =
    ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  const hour = Math.floor(normalizedMinute / 60);
  const minutePart = normalizedMinute % 60;
  return `${String(hour).padStart(2, "0")}:${String(minutePart).padStart(2, "0")}`;
}

function splitTimeValue(value?: string) {
  const minute = toMinute(value);
  if (Number.isNaN(minute)) {
    return { hour: "10", minute: "00" };
  }
  return {
    hour: String(Math.floor(minute / 60)).padStart(2, "0"),
    minute: String(minute % 60).padStart(2, "0"),
  };
}

function joinTimeValue(hour?: string, minute?: string) {
  if (hour === undefined || minute === undefined) {
    return "";
  }
  const value = `${hour}:${minute}`;
  return Number.isNaN(toMinute(value)) ? "" : value;
}

function normalizeCustomSlotFormValues(
  slots: NonNullable<StrategyFormValues["customSlots"]>,
) {
  return slots.map((slot) => ({
    start:
      slot.start ||
      joinTimeValue(slot.startHour, slot.startMinute),
    end:
      slot.end ||
      joinTimeValue(slot.endHour, slot.endMinute),
  }));
}

function buildNormalizedCustomSlots(slots: { start?: string; end?: string }[]) {
  if (!Array.isArray(slots) || slots.length === 0) {
    return {
      error: "自定义时段不能为空",
      slots: [] as { start: string; end: string }[],
    };
  }

  const segments: { startMinute: number; endMinute: number }[] = [];
  for (const slot of slots) {
    if (!slot?.start || !slot?.end) {
      return {
        error: "自定义时段不完整",
        slots: [] as { start: string; end: string }[],
      };
    }

    const startMinute = toMinute(slot.start);
    const endMinute = toMinute(slot.end);
    if (Number.isNaN(startMinute) || Number.isNaN(endMinute)) {
      return {
        error: "自定义时段格式不正确",
        slots: [] as { start: string; end: string }[],
      };
    }
    if (startMinute === endMinute) {
      return {
        error: "自定义时段范围不正确",
        slots: [] as { start: string; end: string }[],
      };
    }

    if (startMinute < endMinute) {
      segments.push({ startMinute, endMinute });
    } else {
      segments.push({ startMinute, endMinute: MINUTES_PER_DAY });
      segments.push({ startMinute: 0, endMinute });
    }
  }

  const sortedSegments = [...segments].sort((left, right) => {
    if (left.startMinute !== right.startMinute) {
      return left.startMinute - right.startMinute;
    }
    return left.endMinute - right.endMinute;
  });

  const mergedSegments: { startMinute: number; endMinute: number }[] = [];
  for (const segment of sortedSegments) {
    const lastSegment = mergedSegments[mergedSegments.length - 1];
    if (!lastSegment) {
      mergedSegments.push({ ...segment });
      continue;
    }

    if (segment.startMinute < lastSegment.endMinute) {
      return {
        error: "自定义时段不能重叠",
        slots: [] as { start: string; end: string }[],
      };
    }
    if (segment.startMinute === lastSegment.endMinute) {
      lastSegment.endMinute = segment.endMinute;
      continue;
    }
    mergedSegments.push({ ...segment });
  }

  if (
    mergedSegments.length === 1 &&
    mergedSegments[0].startMinute === 0 &&
    mergedSegments[0].endMinute === MINUTES_PER_DAY
  ) {
    return {
      error: "自定义时段不能覆盖全天，请使用让AI自动管理",
      slots: [] as { start: string; end: string }[],
    };
  }

  let leadingSegment: { startMinute: number; endMinute: number } | null = null;
  let trailingSegment: { startMinute: number; endMinute: number } | null = null;
  if (
    mergedSegments.length >= 2 &&
    mergedSegments[0].startMinute === 0 &&
    mergedSegments[mergedSegments.length - 1].endMinute === MINUTES_PER_DAY
  ) {
    leadingSegment = mergedSegments[0];
    trailingSegment = mergedSegments[mergedSegments.length - 1];
  }

  const normalizedSlots: { start: string; end: string }[] = [];
  for (const segment of mergedSegments) {
    if (segment === leadingSegment || segment === trailingSegment) {
      continue;
    }
    normalizedSlots.push({
      start: formatMinute(segment.startMinute),
      end: formatMinute(segment.endMinute),
    });
  }

  if (leadingSegment && trailingSegment) {
    normalizedSlots.push({
      start: formatMinute(trailingSegment.startMinute),
      end: formatMinute(leadingSegment.endMinute),
    });
  }

  normalizedSlots.sort(
    (left, right) => toMinute(left.start) - toMinute(right.start),
  );
  return { error: "", slots: normalizedSlots };
}

function formatMoney(value?: number | null) {
  if (value == null) {
    return "-";
  }
  return `¥${Number(value).toFixed(2)}`;
}

function formatBidAmount(value?: number | null) {
  if (value == null || Number.isNaN(Number(value))) {
    return "--";
  }
  return Number(value)
    .toFixed(2)
    .replace(/\.?0+$/, "");
}

function normalizeBidAmount(value: unknown, fallback: number) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  const stepped = Math.round(numeric / BID_STEP) * BID_STEP;
  return Number(
    Math.min(DEFAULT_BID_MAX, Math.max(DEFAULT_BID_MIN, stepped)).toFixed(1),
  );
}

function normalizeBidRange(
  bidMin?: number | string | null,
  bidMax?: number | string | null,
): [number, number] {
  const min = normalizeBidAmount(bidMin, DEFAULT_BID_MIN);
  const max = normalizeBidAmount(bidMax, DEFAULT_BID_MAX);
  return min <= max ? [min, max] : [max, max];
}

function resolveCurrentBidDisplayText(record: MasterConsoleShopRow) {
  const currentBid = record.currentBid;
  if (!currentBid) {
    return record.latestPromotionBid == null
      ? "-"
      : `${formatBidAmount(record.latestPromotionBid)}元/次`;
  }
  if (!currentBid.inPromotionWindow) {
    return "非推广时段";
  }
  if (currentBid.displayStatusText) {
    return currentBid.displayStatusText;
  }
  if (
    currentBid.currentBidAmount === null ||
    currentBid.currentBidAmount === undefined
  ) {
    return "--元/次";
  }
  return `${formatBidAmount(currentBid.currentBidAmount)}元/次`;
}

function resolveCurrentBidTextType(record: MasterConsoleShopRow) {
  const status = record.currentBid?.latestExecuteStatus;
  if (status === "FAILED") {
    return "danger" as const;
  }
  if (status === "SKIPPED" || !record.currentBid?.inPromotionWindow) {
    return "secondary" as const;
  }
  return undefined;
}

function isReauthRequired(record?: MasterConsoleShopRow | null) {
  return Boolean(record?.reauthRequired) || Number(record?.authStatus) === 2;
}

function formatDateTimeText(value?: string | null) {
  if (!value) {
    return "-";
  }
  const parsed = dayjs(value);
  return parsed.isValid()
    ? parsed.format("YYYY-MM-DD HH:mm:ss")
    : String(value);
}

function renderAuthorizationStatus(record: MasterConsoleShopRow) {
  if (!isReauthRequired(record)) {
    return null;
  }
  const title = [
    record.authStatusText || "需重新授权",
    record.lastAuthFailureAt
      ? `最近失败时间：${formatDateTimeText(record.lastAuthFailureAt)}`
      : null,
  ]
    .filter(Boolean)
    .join("\n");
  return (
    <Tooltip title={<span style={{ whiteSpace: "pre-line" }}>{title}</span>}>
      <Tag color="red">需重新授权</Tag>
    </Tooltip>
  );
}

function formatPercent(value?: number | null) {
  if (value == null) {
    return "-";
  }
  return `${(Number(value) * 100).toFixed(2)}%`;
}

function getStatsRangeCompareLabel(statsRange: DashboardStatsRange) {
  return (
    DASHBOARD_STATS_RANGE_OPTIONS.find((item) => item.value === statsRange)
      ?.compareLabel || "较昨日同期"
  );
}

function formatTrend(
  current?: number | null,
  previous?: number | null,
  compareLabel = "较昨日同期",
) {
  const normalizedCurrent = Number(current ?? 0);
  const normalizedPrevious = Number(previous ?? 0);

  if (normalizedCurrent === 0 && normalizedPrevious === 0) {
    return {
      text: `${compareLabel} 持平`,
      color: undefined as "success" | "danger" | undefined,
    };
  }

  if (normalizedPrevious === 0) {
    return {
      text: `${compareLabel} --`,
      color: normalizedCurrent > 0 ? ("danger" as const) : ("success" as const),
    };
  }

  const ratio =
    ((normalizedCurrent - normalizedPrevious) / normalizedPrevious) * 100;
  if (ratio === 0) {
    return {
      text: `${compareLabel} 持平`,
      color: undefined as "success" | "danger" | undefined,
    };
  }

  const sign = ratio > 0 ? "+" : "";
  return {
    text: `${compareLabel} ${sign}${ratio.toFixed(2)}%`,
    color: ratio > 0 ? ("danger" as const) : ("success" as const),
  };
}

function renderMetricCard(
  value: React.ReactNode,
  trendText: string,
  trendColor?: "success" | "danger",
) {
  return (
    <Space orientation="vertical" size={2}>
      <Text strong>{value}</Text>
      <Text
        type={
          trendColor === "danger"
            ? "danger"
            : trendColor === "success"
              ? "success"
              : "secondary"
        }
      >
        {trendText}
      </Text>
    </Space>
  );
}

function renderMerchantBindingStatus(record: MasterConsoleShopRow) {
  if (record.bindingStatus === "UNBOUND") {
    return <Tag>未绑定</Tag>;
  }
  if (record.bindingStatus === "BOTH") {
    return <Tag color="blue">双绑定</Tag>;
  }
  if (record.salesBindingId && !record.ordinaryBindingId) {
    return <Tag color="purple">网销用户</Tag>;
  }
  if (record.ordinaryBindingId && !record.salesBindingId) {
    return <Tag color="green">普通商户</Tag>;
  }
  if (record.bindingDomain === "SALES") {
    return <Tag color="purple">{record.bindingDomainName || "网销用户"}</Tag>;
  }
  if (record.bindingDomain === "ORDINARY") {
    return <Tag color="green">{record.bindingDomainName || "普通商户"}</Tag>;
  }
  return (
    <Tag>{record.bindingStatusName || record.bindingDomainName || "未知"}</Tag>
  );
}

function renderMerchantBoundUsers(record: MasterConsoleShopRow) {
  const items = [
    record.ordinaryBoundUserName || record.ordinaryBoundUserId
      ? {
          label: "普通",
          value: record.ordinaryBoundUserName || record.ordinaryBoundUserId,
        }
      : null,
    record.salesBoundUserName || record.salesBoundUserId
      ? {
          label: "网销",
          value: record.salesBoundUserName || record.salesBoundUserId,
        }
      : null,
  ].filter((item): item is { label: string; value: string } => Boolean(item));

  if (items.length > 0) {
    return (
      <Space orientation="vertical" size={2}>
        {items.map((item) => (
          <Text key={item.label}>
            {item.label}：{item.value}
          </Text>
        ))}
      </Space>
    );
  }
  return record.boundUserName || record.boundUserId || "-";
}

function renderExpireAt(value?: string) {
  if (!value) {
    return <Text type="secondary">-</Text>;
  }

  const expireAt = dayjs(value);
  if (!expireAt.isValid()) {
    return value;
  }

  const daysLeft = expireAt.endOf("day").diff(dayjs(), "day");
  if (expireAt.isBefore(dayjs())) {
    return (
      <Space orientation="vertical" size={2}>
        <Text>{expireAt.format("YYYY-MM-DD HH:mm:ss")}</Text>
        <Tag color="red">已过期</Tag>
      </Space>
    );
  }

  const tagColor = daysLeft <= 7 ? "orange" : "default";
  return (
    <Space orientation="vertical" size={2}>
      <Text>{expireAt.format("YYYY-MM-DD HH:mm:ss")}</Text>
      <Tag color={tagColor}>剩余 {Math.max(daysLeft, 0)} 天</Tag>
    </Space>
  );
}

function renderBusinessStatusTag(summary?: PromotionLogSummary | null) {
  const businessStatus = summary?.businessStatus;
  if (businessStatus?.open === true) {
    return <Tag color="success">营业中</Tag>;
  }
  if (businessStatus?.open === false) {
    return <Tag color="default">休息中</Tag>;
  }
  return <Tag>营业待确认</Tag>;
}

function renderPeakStatusTag(summary?: PromotionLogSummary | null) {
  const peakStatus = summary?.peakStatus;
  if (peakStatus?.trusted !== true) {
    return <Tag color="warning">高峰待确认</Tag>;
  }
  if (peakStatus?.peakMatched) {
    return <Tag color="error">高峰期</Tag>;
  }
  return <Tag color="processing">非高峰期</Tag>;
}

function renderStatusTooltip(
  title: string,
  rows: { label: string; value: React.ReactNode }[],
) {
  return (
    <div
      style={{
        minWidth: 340,
        maxWidth: 460,
        display: "flex",
        flexDirection: "column",
        gap: 8,
        color: "#ffffff",
      }}
    >
      <div
        style={{
          fontSize: 13,
          fontWeight: 700,
          lineHeight: 1.4,
          color: "#ffffff",
        }}
      >
        {title}
      </div>
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 6,
        }}
      >
        {rows.map((row) => (
          <div
            key={row.label}
            style={{
              display: "grid",
              gridTemplateColumns: "92px minmax(0, 1fr)",
              gap: 12,
              alignItems: "start",
              fontSize: 12,
              lineHeight: 1.5,
            }}
          >
            <span style={{ color: "rgba(255, 255, 255, 0.72)" }}>
              {row.label}
            </span>
            <span
              style={{
                color: "#ffffff",
                wordBreak: "break-word",
                whiteSpace: "pre-wrap",
              }}
            >
              {row.value}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function formatPeakDayType(value?: string | null) {
  switch (value) {
    case "WEEKDAY":
      return "工作日";
    case "WEEKEND":
      return "周末";
    case "HOLIDAY":
      return "节假日";
    default:
      return "-";
  }
}

function formatPeakWindowsForTooltip(windows?: string[] | null) {
  if (!windows?.length) {
    return "正常营业 5 天后解锁";
  }
  return windows
    .flatMap((window) =>
      window
        .split(/[，,]/)
        .map((item) => item.trim())
        .filter(Boolean),
    )
    .join("\n");
}

function formatCommaSeparatedLines(value?: string | null) {
  if (!value) {
    return "无";
  }
  return value
    .split(/[，,]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .join("\n");
}

function renderBusinessStatusTooltip(summary?: PromotionLogSummary | null) {
  const businessStatus = summary?.businessStatus;
  if (!summary) {
    return renderStatusTooltip("营业状态", [
      { label: "当前状态", value: "查询中..." },
    ]);
  }
  return renderStatusTooltip("营业状态", [
    {
      label: "营业时间",
      value: formatCommaSeparatedLines(businessStatus?.shippingTime),
    },
    {
      label: "当前状态",
      value:
        businessStatus?.open === true
          ? "营业中"
          : businessStatus?.open === false
            ? "休息中"
            : "待确认",
    },
  ]);
}

function renderPeakStatusTooltip(summary?: PromotionLogSummary | null) {
  const peakStatus = summary?.peakStatus;
  if (!summary) {
    return renderStatusTooltip("高峰状态", [
      { label: "当前状态", value: "查询中..." },
    ]);
  }
  return renderStatusTooltip("高峰状态", [
    {
      label: "当前状态",
      value:
        peakStatus?.trusted !== true
          ? "高峰待确认"
          : peakStatus?.peakMatched
            ? "高峰期"
            : "非高峰期",
    },
    { label: "当前日型", value: formatPeakDayType(peakStatus?.currentDayType) },
    {
      label: peakStatus?.trusted ? "高峰时段" : "解锁条件",
      value: peakStatus?.trusted
        ? formatPeakWindowsForTooltip(peakStatus?.configuredWindows)
        : "正常营业 5 天后解锁",
    },
  ]);
}

function parseStrategyConfig(
  settings?: PromotionSettings | null,
): PromotionStrategyConfig {
  const defaultTimeMode =
    settings?.autoTimeModeAvailable === false ? "business" : "auto";
  if (!settings?.strategyConfigJson) {
    return {
      priority: "rate",
      timeMode: defaultTimeMode,
      rateMode: "control",
      rateMin: `${DEFAULT_RATE_MIN}%`,
      rateMax: `${DEFAULT_RATE_MAX}%`,
      bidMin: DEFAULT_BID_MIN,
      bidMax: DEFAULT_BID_MAX,
      decisionMode: "rule",
      customSlots: [],
    };
  }

  try {
    const parsed = JSON.parse(
      settings.strategyConfigJson,
    ) as PromotionStrategyConfig;
    const priority = parsed.priority || "rate";
    const parsedTimeMode =
      parsed.timeMode === "auto" && settings?.autoTimeModeAvailable === false
        ? "business"
        : parsed.timeMode || defaultTimeMode;
    return {
      priority,
      timeMode: parsedTimeMode,
      rateMode: "control",
      rateMin:
        priority === "exposure"
          ? `${EXPOSURE_RATE_MIN}%`
          : parsed.rateMin || `${DEFAULT_RATE_MIN}%`,
      rateMax:
        priority === "exposure"
          ? `${EXPOSURE_RATE_MAX}%`
          : parsed.rateMax || `${DEFAULT_RATE_MAX}%`,
      bidMin: normalizeBidRange(parsed.bidMin, parsed.bidMax)[0],
      bidMax: normalizeBidRange(parsed.bidMin, parsed.bidMax)[1],
      decisionMode: parsed.decisionMode || "rule",
      customSlots: parsed.customSlots || [],
    };
  } catch (error) {
    console.error("Failed to parse strategy config:", error);
    return {
      priority: "rate",
      timeMode: defaultTimeMode,
      rateMode: "control",
      rateMin: `${DEFAULT_RATE_MIN}%`,
      rateMax: `${DEFAULT_RATE_MAX}%`,
      bidMin: DEFAULT_BID_MIN,
      bidMax: DEFAULT_BID_MAX,
      decisionMode: "rule",
      customSlots: [],
    };
  }
}

const MasterConsole: React.FC = () => {
  const navigate = useNavigate();
  const { styles: pageStyles } = usePageStyles();
  const { styles } = useStyles();
  const { isCompact } = useResponsive();
  const { hasPermission, isSuperAdmin } = usePermission();
  const { currentUser, refreshUser } = useAuth();
  const isAdminPortal = currentUser?.activePortal === "ADMIN";
  const canUseBindingDomain = isSuperAdmin && isAdminPortal;
  const isYouzanPortal = currentUser?.activePortal === "YOUZAN";
  const isImpersonated = Boolean(currentUser?.impersonated);
  const canEditConfig =
    hasPermission("shop:master-console:config") ||
    hasPermission("youzan:master-console:config");
  const [searchForm] = Form.useForm<MasterConsoleQueryParam>();
  const [configForm] = Form.useForm<StrategyFormValues>();
  const [manualBidForm] = Form.useForm<ManualBidFormValues>();
  const bidRangeAxisRef = useRef<HTMLDivElement | null>(null);
  const [activeBidRangeHandle, setActiveBidRangeHandle] = useState<
    "bidMin" | "bidMax" | null
  >(null);

  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [savingSwitchShopId, setSavingSwitchShopId] = useState<number | null>(
    null,
  );
  const [configLoading, setConfigLoading] = useState(false);
  const [configSaving, setConfigSaving] = useState(false);
  const [logLoading, setLogLoading] = useState(false);
  const [lastRateSelection, setLastRateSelection] = useState({
    rateMin: DEFAULT_RATE_MIN,
    rateMax: DEFAULT_RATE_MAX,
  });

  const [records, setRecords] = useState<MasterConsoleShopRow[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [statsRange, setStatsRange] = useState<DashboardStatsRange>("TODAY");
  const [keyword, setKeyword] = useState("");
  const [targetUserId, setTargetUserId] = useState<string>();
  const [bindingDomain, setBindingDomain] = useState<ShopBindingDomain>("ALL");
  const [bindingStatus, setBindingStatus] = useState<ShopBindingStatus>("ALL");
  const [promotionStatus, setPromotionStatus] =
    useState<PromotionStatus>("ALL");
  const [powerBalanceSortOrder, setPowerBalanceSortOrder] =
    useState<SortOrder>(null);
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [visibleUserLoading, setVisibleUserLoading] = useState(false);
  const [hasExpandedScope, setHasExpandedScope] = useState(false);
  const visibleUserSearchTimerRef = useRef<number | null>(null);

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [manualBidModalOpen, setManualBidModalOpen] = useState(false);
  const [manualBidSaving, setManualBidSaving] = useState(false);
  const [currentShop, setCurrentShop] = useState<MasterConsoleShopRow | null>(
    null,
  );
  const [currentSettings, setCurrentSettings] =
    useState<PromotionSettings | null>(null);

  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [logSummaryLoading, setLogSummaryLoading] = useState(false);
  const [logRecords, setLogRecords] = useState<PromotionLogRecord[]>([]);
  const [logSummary, setLogSummary] = useState<PromotionLogSummary | null>(
    null,
  );
  const [logTotal, setLogTotal] = useState(0);
  const [logPageNum, setLogPageNum] = useState(1);
  const [logPageSize, setLogPageSize] = useState(20);

  const buildQueryParams = useCallback(
    (pageNum?: number, queryPageSize?: number): MasterConsoleQueryParam => ({
      keyword: keyword.trim() || undefined,
      targetUserId,
      bindingDomain: canUseBindingDomain ? bindingDomain : undefined,
      bindingStatus: canUseBindingDomain ? bindingStatus : undefined,
      promotionStatus,
      statsRange,
      sortField: powerBalanceSortOrder ? "powerBalance" : undefined,
      sortOrder: powerBalanceSortOrder || undefined,
      pageNum,
      pageSize: queryPageSize,
    }),
    [
      bindingDomain,
      bindingStatus,
      canUseBindingDomain,
      keyword,
      powerBalanceSortOrder,
      promotionStatus,
      statsRange,
      targetUserId,
    ],
  );

  const buildExportQueryParams = useCallback((): MasterConsoleQueryParam => {
    const params = buildQueryParams();
    return {
      keyword: params.keyword,
      targetUserId: params.targetUserId,
      bindingDomain: params.bindingDomain,
      bindingStatus: params.bindingStatus,
      promotionStatus: params.promotionStatus,
      sortField: params.sortField,
      sortOrder: params.sortOrder,
    };
  }, [buildQueryParams]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getMasterConsoleShops(
        buildQueryParams(current, pageSize),
      );
      setRecords(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load master console rows:", error);
    } finally {
      setLoading(false);
    }
  }, [buildQueryParams, current, pageSize]);

  const handleExport = useCallback(async () => {
    setExporting(true);
    try {
      await exportMasterConsoleShops(buildExportQueryParams());
      message.success("导出已开始");
    } catch (error) {
      console.error("Failed to export master console rows:", error);
    } finally {
      setExporting(false);
    }
  }, [buildExportQueryParams]);

  const handleStartSalesImpersonation = useCallback(async () => {
    if (!targetUserId) {
      message.warning("请先选择要模拟的网销用户");
      return;
    }
    try {
      await startImpersonation(targetUserId, "SALES");
      searchForm.resetFields();
      setKeyword("");
      setTargetUserId(undefined);
      setBindingDomain("ALL");
      setBindingStatus("ALL");
      setPromotionStatus("ALL");
      setCurrent(1);
      await refreshUser();
      message.success("已切换到网销用户视角");
    } catch (error) {
      console.error("Failed to start impersonation:", error);
    }
  }, [refreshUser, searchForm, targetUserId]);

  const handleStopImpersonation = useCallback(async () => {
    try {
      await stopImpersonation();
      searchForm.resetFields();
      setKeyword("");
      setTargetUserId(undefined);
      setBindingDomain("ALL");
      setBindingStatus("ALL");
      setPromotionStatus("ALL");
      setCurrent(1);
      await refreshUser();
      message.success("已退出模拟视角");
    } catch (error) {
      console.error("Failed to stop impersonation:", error);
    }
  }, [refreshUser, searchForm]);

  const loadVisibleUsers = useCallback(
    async (keyword?: string) => {
      setVisibleUserLoading(true);
      try {
        const response = await getMasterConsoleVisibleUsers(
          keyword,
          canUseBindingDomain ? bindingDomain : undefined,
        );
        setVisibleUsers(response);
        if (!keyword) {
          setHasExpandedScope(response.length > 1);
        }
      } catch (error) {
        console.error("Failed to load master console visible users:", error);
      } finally {
        setVisibleUserLoading(false);
      }
    },
    [bindingDomain, canUseBindingDomain],
  );

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    void loadVisibleUsers();
  }, [loadVisibleUsers]);

  useEffect(
    () => () => {
      if (visibleUserSearchTimerRef.current != null) {
        window.clearTimeout(visibleUserSearchTimerRef.current);
      }
    },
    [],
  );

  const handleVisibleUserSearch = useCallback(
    (value: string) => {
      if (visibleUserSearchTimerRef.current != null) {
        window.clearTimeout(visibleUserSearchTimerRef.current);
      }
      visibleUserSearchTimerRef.current = window.setTimeout(() => {
        void loadVisibleUsers(value.trim() || undefined);
      }, USER_SEARCH_DEBOUNCE_MS);
    },
    [loadVisibleUsers],
  );

  const loadLogs = useCallback(
    async (
      shop: MasterConsoleShopRow,
      nextPageNum = logPageNum,
      nextPageSize = logPageSize,
    ) => {
      setLogLoading(true);
      try {
        const response = await getMasterConsolePromotionLogs(
          shop.shopId,
          shop.bindingDomain,
          nextPageNum,
          nextPageSize,
        );
        setLogRecords(response.list);
        setLogTotal(response.total);
        setLogPageNum(response.pageNum);
        setLogPageSize(response.pageSize);
      } catch (error) {
        console.error("Failed to load promotion logs:", error);
      } finally {
        setLogLoading(false);
      }
    },
    [logPageNum, logPageSize],
  );

  const loadLogSummary = useCallback(async (shop: MasterConsoleShopRow) => {
    setLogSummaryLoading(true);
    try {
      const response = await getMasterConsolePromotionLogSummary(
        shop.shopId,
        shop.bindingDomain,
      );
      setLogSummary(response);
    } catch (error) {
      console.error("Failed to load promotion log summary:", error);
      setLogSummary(null);
    } finally {
      setLogSummaryLoading(false);
    }
  }, []);

  const openConfigModal = async (shop: MasterConsoleShopRow) => {
    setCurrentShop(shop);
    setConfigModalOpen(true);
    setConfigLoading(true);
    try {
      const settings = await getMasterConsolePromotionSettings(
        shop.shopId,
        shop.bindingDomain,
      );
      setCurrentSettings(settings);
      const strategyConfig = parseStrategyConfig(settings);
      const bidRange = normalizeBidRange(
        strategyConfig.bidMin,
        strategyConfig.bidMax,
      );
      configForm.setFieldsValue({
        priority: strategyConfig.priority,
        timeMode: strategyConfig.timeMode,
        rateMin: Number(String(strategyConfig.rateMin).replace("%", "")),
        rateMax: Number(String(strategyConfig.rateMax).replace("%", "")),
        bidRange,
        customSlots:
          strategyConfig.customSlots?.map((slot) => {
            const start = splitTimeValue(slot.start);
            const end = splitTimeValue(slot.end);
            return {
              startHour: start.hour,
              startMinute: start.minute,
              endHour: end.hour,
              endMinute: end.minute,
            };
          }) || [],
      });
      if (strategyConfig.priority === "rate") {
        setLastRateSelection({
          rateMin: Number(String(strategyConfig.rateMin).replace("%", "")),
          rateMax: Number(String(strategyConfig.rateMax).replace("%", "")),
        });
      }
    } catch (error) {
      console.error("Failed to load promotion settings:", error);
      setConfigModalOpen(false);
      setCurrentShop(null);
    } finally {
      setConfigLoading(false);
    }
  };

  const handleTogglePromotion = async (
    checked: boolean,
    record: MasterConsoleShopRow,
  ) => {
    if (checked && isReauthRequired(record)) {
      message.warning("美团授权失败，请商家重新授权后再开启推广");
      return;
    }
    if (!checked && record.promotionEnabled) {
      const confirmed = await new Promise<boolean>((resolve) => {
        modal.confirm({
          title: "确认关闭",
          content: "关闭后将同步关闭商家端推广，请谨慎操作！",
          okText: "确认关闭",
          cancelText: "取消",
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        });
      });
      if (!confirmed) {
        return;
      }
    }

    setSavingSwitchShopId(record.shopId);
    reportAdminBehaviorEvent("ADMIN_PROMOTION_SWITCH_UPDATE", record.shopId, {
      promotionEnabled: checked,
    });
    try {
      await updateMasterConsolePromotionSettings(
        record.shopId,
        {
          promotionEnabled: checked,
        },
        record.bindingDomain,
      );
      message.success(checked ? "推广已开启" : "推广已关闭");
      await loadData();
    } catch (error) {
      console.error("Failed to update promotion switch:", error);
    } finally {
      setSavingSwitchShopId(null);
    }
  };

  const handleSaveConfig = async () => {
    if (!currentShop) {
      return;
    }
    reportAdminBehaviorEvent("ADMIN_PROMOTION_CONFIG_SAVE", currentShop.shopId);

    try {
      const values = await configForm.validateFields();
      values.bidRange = normalizeBidRange(
        configForm.getFieldValue("bidRange")?.[0],
        configForm.getFieldValue("bidRange")?.[1],
      );
      const customSlots = normalizeCustomSlotFormValues(
        values.customSlots || [],
      );
      const normalizedCustomSlotResult =
        values.timeMode === "custom"
          ? buildNormalizedCustomSlots(customSlots)
          : { error: "", slots: [] as { start: string; end: string }[] };

      if (normalizedCustomSlotResult.error) {
        message.warning(normalizedCustomSlotResult.error);
        return;
      }
      const [bidMin, bidMax] = normalizeBidRange(
        values.bidRange?.[0],
        values.bidRange?.[1],
      );

      const strategyConfig: PromotionStrategyConfig = {
        priority: values.priority,
        timeMode: values.timeMode,
        rateMode: "control",
        rateMin: `${values.rateMin}%`,
        rateMax: `${values.rateMax}%`,
        bidMin,
        bidMax,
        decisionMode: "rule",
        customSlots:
          values.timeMode === "custom" ? normalizedCustomSlotResult.slots : [],
      };
      let shouldEnablePromotion = false;
      if (currentSettings?.promotionEnabled === false) {
        shouldEnablePromotion = await new Promise<boolean>((resolve) => {
          modal.confirm({
            title: "同步开启 AI 推广开关",
            content: "当前推广开关为关闭状态，确认后将保存配置并同步开启 AI 推广。",
            okText: "确认",
            cancelText: "取消",
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
      }

      setConfigSaving(true);
      await updateMasterConsolePromotionSettings(
        currentShop.shopId,
        {
          balanceReminderEnabled: currentSettings?.balanceReminderEnabled,
          promotionEnabled: shouldEnablePromotion ? true : undefined,
          strategyConfigured: true,
          strategyConfigJson: JSON.stringify(strategyConfig),
        },
        currentShop.bindingDomain,
      );
      message.success("推广策略已保存");
      setConfigModalOpen(false);
      setCurrentShop(null);
      setCurrentSettings(null);
      configForm.resetFields();
      await loadData();
    } catch (error) {
      if (error instanceof Error) {
        console.error("Failed to save strategy config:", error);
      }
    } finally {
      setConfigSaving(false);
    }
  };

  const openManualBidModal = (shop: MasterConsoleShopRow) => {
    if (!shop.currentBid?.editable) {
      message.info(shop.currentBid?.displayStatusText || "非推广时段");
      return;
    }
    const currentBidAmount =
      shop.currentBid.currentBidAmount === null ||
      shop.currentBid.currentBidAmount === undefined
        ? shop.latestPromotionBid
        : shop.currentBid.currentBidAmount;
    setCurrentShop(shop);
    manualBidForm.setFieldsValue({
      bidAmount:
        currentBidAmount === null || currentBidAmount === undefined
          ? undefined
          : Number(currentBidAmount),
    });
    setManualBidModalOpen(true);
  };

  const handleSaveManualBid = async () => {
    if (!currentShop) {
      return;
    }

    try {
      const values = await manualBidForm.validateFields();
      setManualBidSaving(true);
      await updateMasterConsoleManualPromotionBid(
        currentShop.shopId,
        values.bidAmount,
        currentShop.bindingDomain,
      );
      message.success("当前出价已修改");
      setManualBidModalOpen(false);
      setCurrentShop(null);
      manualBidForm.resetFields();
      await loadData();
    } catch (error) {
      if (error instanceof Error) {
        console.error("Failed to update manual promotion bid:", error);
      }
    } finally {
      setManualBidSaving(false);
    }
  };

  const openLogDrawer = async (shop: MasterConsoleShopRow) => {
    reportAdminBehaviorEvent("ADMIN_PROMOTION_LOG_VIEW", shop.shopId);
    setCurrentShop(shop);
    setLogDrawerOpen(true);
    setLogPageNum(1);
    setLogPageSize(20);
    setLogSummary(null);
    await Promise.all([loadLogs(shop, 1, 20), loadLogSummary(shop)]);
  };

  const canWriteRecord = (record?: MasterConsoleShopRow | null) =>
    Boolean(
      canEditConfig &&
      (canUseBindingDomain || record?.bindingDomain !== "SALES"),
    );

  const columns: ColumnsType<MasterConsoleShopRow> = [
    {
      title: "门店",
      dataIndex: "shopName",
      key: "shopName",
      width: 220,
      fixed: isCompact ? undefined : "left",
      render: (_, record) => (
        <Space orientation="vertical" size={2}>
          <Space size={6} wrap>
            <Text strong>{record.shopName || "未命名门店"}</Text>
            {renderAuthorizationStatus(record)}
          </Space>
          <Text type="secondary">{record.poiId}</Text>
        </Space>
      ),
    },
    ...(isSuperAdmin
      ? [
          {
            title: "绑定类型",
            dataIndex: "bindingDomainName",
            key: "bindingDomainName",
            width: 120,
            render: (_: string | undefined, record: MasterConsoleShopRow) =>
              renderMerchantBindingStatus(record),
          },
        ]
      : []),
    ...(isSuperAdmin && hasExpandedScope
      ? [
          {
            title: "所属用户",
            dataIndex: "boundUserName",
            key: "boundUserName",
            width: 180,
            render: (_: string | undefined, record: MasterConsoleShopRow) =>
              renderMerchantBoundUsers(record),
          },
        ]
      : []),
    {
      title: "营收/元",
      key: "income",
      width: 150,
      render: (_, record) => {
        const trend = formatTrend(
          record.currentCompletedIncome,
          record.previousCompletedIncome,
          getStatsRangeCompareLabel(statsRange),
        );
        return renderMetricCard(
          formatMoney(record.currentCompletedIncome),
          trend.text,
          trend.color,
        );
      },
    },
    {
      title: "订单量/单",
      key: "orderCount",
      width: 150,
      render: (_, record) => {
        const trend = formatTrend(
          record.currentCompletedOrderCount,
          record.previousCompletedOrderCount,
          getStatsRangeCompareLabel(statsRange),
        );
        return renderMetricCard(
          record.currentCompletedOrderCount,
          trend.text,
          trend.color,
        );
      },
    },
    {
      title: "付费曝光/次",
      key: "paidExposure",
      width: 150,
      render: (_, record) => {
        const trend = formatTrend(
          record.currentPaidExposure,
          record.previousPaidExposure,
          getStatsRangeCompareLabel(statsRange),
        );
        return renderMetricCard(
          record.currentPaidExposure,
          trend.text,
          trend.color,
        );
      },
    },
    {
      title: "最新推广定价/元",
      dataIndex: "latestPromotionBid",
      key: "latestPromotionBid",
      width: 190,
      render: (_, record) => (
        <Space orientation="vertical" size={2}>
          <Space size={6}>
            <Text strong type={resolveCurrentBidTextType(record)}>
              {resolveCurrentBidDisplayText(record)}
            </Text>
            {canWriteRecord(record) && record.currentBid?.editable ? (
              <Tooltip title="修改当前出价">
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  onClick={() => openManualBidModal(record)}
                />
              </Tooltip>
            ) : null}
          </Space>
          {record.currentBid?.previousBidAmount !== null &&
          record.currentBid?.previousBidAmount !== undefined ? (
            <Text type="secondary">
              上轮出价{formatBidAmount(record.currentBid.previousBidAmount)}元
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: "推广余额/元",
      dataIndex: "promotionBalance",
      key: "promotionBalance",
      width: 130,
      render: (value?: number | null) =>
        value == null ? (
          <Text type="secondary">-</Text>
        ) : (
          <Text>{formatMoney(value)}</Text>
        ),
    },
    {
      title: "推广花费/元",
      key: "promotionCost",
      width: 150,
      render: (_, record) => {
        const trend = formatTrend(
          record.currentPromotionCost,
          record.previousPromotionCost,
          getStatsRangeCompareLabel(statsRange),
        );
        return renderMetricCard(
          formatMoney(record.currentPromotionCost),
          trend.text,
          trend.color,
        );
      },
    },
    {
      title: "费用率",
      key: "costRatio",
      width: 150,
      render: (_, record) => {
        const trend = formatTrend(
          record.currentCostRatio,
          record.previousCostRatio,
          getStatsRangeCompareLabel(statsRange),
        );
        return renderMetricCard(
          formatPercent(record.currentCostRatio),
          trend.text,
          trend.color,
        );
      },
    },
    ...(isYouzanPortal
      ? []
      : [
          {
            title: "算力余额",
            dataIndex: "powerBalance",
            key: "powerBalance",
            width: 150,
            sorter: true,
            sortOrder: powerBalanceSortOrder,
            render: (value?: number | null) => formatPowerWithUnit(value),
          },
          {
            title: "算力消耗",
            key: "powerConsume",
            width: 150,
            render: (_: unknown, record: MasterConsoleShopRow) => {
              const trend = formatTrend(
                record.currentConsumedPower,
                record.previousConsumedPower,
                getStatsRangeCompareLabel(statsRange),
              );
              return renderMetricCard(
                formatPowerWithUnit(record.currentConsumedPower),
                trend.text,
                trend.color,
              );
            },
          },
        ]),
    {
      title: " 推广开关",
      key: "switch",
      width: 90,
      render: (_, record) =>
        canWriteRecord(record) ? (
          <Tooltip
            title={
              isReauthRequired(record) && !record.promotionEnabled
                ? "美团授权失败，请商家重新授权后再开启推广"
                : undefined
            }
          >
            <span>
              <Switch
                checked={record.promotionEnabled}
                disabled={isReauthRequired(record) && !record.promotionEnabled}
                loading={savingSwitchShopId === record.shopId}
                onChange={(checked) =>
                  void handleTogglePromotion(checked, record)
                }
              />
            </span>
          </Tooltip>
        ) : (
          <Text type="secondary">
            {record.promotionEnabled ? "已开启" : "已关闭"}
          </Text>
        ),
    },
    {
      title: "配置",
      key: "config",
      width: 130,
      render: (_, record) => (
        <PermissionControl
          anyPermissions={[
            "shop:master-console:config",
            "sales:master-console:view",
            "youzan:master-console:config",
          ]}
        >
          <Button
            type="primary"
            ghost
            icon={<SettingOutlined />}
            onClick={() => void openConfigModal(record)}
          >
            {canWriteRecord(record)
              ? record.strategyConfigured
                ? "查看配置"
                : "+配置"
              : "查看配置"}
          </Button>
        </PermissionControl>
      ),
    },
    {
      title: "日志",
      key: "logs",
      width: 120,
      render: (_, record) => (
        <PermissionControl
          anyPermissions={[
            "shop:master-console:log",
            "sales:master-console:log",
            "youzan:master-console:log",
          ]}
        >
          <Button
            type="primary"
            ghost
            icon={<UnorderedListOutlined />}
            onClick={() => void openLogDrawer(record)}
          >
            查看
          </Button>
        </PermissionControl>
      ),
    },
    {
      title: "到期",
      dataIndex: "rightExpireAt",
      key: "rightExpireAt",
      width: 250,
      render: (value?: string) => renderExpireAt(value),
    },
  ];

  const logColumns: ColumnsType<PromotionLogRecord> = [
    {
      title: "时段",
      dataIndex: "slotTime",
      key: "slotTime",
      width: 180,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (value: string) => {
        const color =
          value === "SUCCESS"
            ? "green"
            : value === "FAILED"
              ? "red"
              : "default";
        return <Tag color={color}>{value}</Tag>;
      },
    },
    {
      title: "类型",
      dataIndex: "type",
      key: "type",
      width: 100,
    },
    {
      title: "结果",
      dataIndex: "price",
      key: "price",
      width: 120,
    },
    {
      title: "说明",
      key: "message",
      render: (_, record) => (
        <MarkdownLogPreview
          markdown={record.markdownMessage}
          fallbackText={record.message}
        />
      ),
    },
  ];

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setCurrent(1);
    setKeyword((values.keyword ?? "").trim());
    setTargetUserId(values.targetUserId || undefined);
    setBindingDomain(values.bindingDomain || "ALL");
    setBindingStatus(values.bindingStatus || "ALL");
    setPromotionStatus(values.promotionStatus || "ALL");
  };

  const handleReset = () => {
    searchForm.resetFields();
    setCurrent(1);
    setKeyword("");
    setTargetUserId(undefined);
    setBindingDomain("ALL");
    setBindingStatus("ALL");
    setPromotionStatus("ALL");
  };

  const currentShopCanWrite = canWriteRecord(currentShop);

  const handleConfigValuesChange = (
    changedValues: Partial<StrategyFormValues>,
    allValues: StrategyFormValues,
  ) => {
    if (
      changedValues.timeMode === "auto" &&
      currentSettings?.autoTimeModeAvailable === false
    ) {
      configForm.setFieldsValue({ timeMode: "business" });
      return;
    }

    if (changedValues.priority) {
      if (changedValues.priority === "exposure") {
        setLastRateSelection({
          rateMin: allValues.rateMin,
          rateMax: allValues.rateMax,
        });
        configForm.setFieldsValue({ priority: "rate" });
        modal.confirm({
          title: "请谨慎开启！",
          content: "该策略将大幅提升曝光量同时增加花费。",
          okText: "确认开启",
          cancelText: "取消",
          onOk: () => {
            configForm.setFieldsValue({
              priority: "exposure",
              rateMin: EXPOSURE_RATE_MIN,
              rateMax: EXPOSURE_RATE_MAX,
            });
          },
        });
        return;
      }

      configForm.setFieldsValue({
        rateMin: lastRateSelection.rateMin,
        rateMax: lastRateSelection.rateMax,
      });
      return;
    }

    if (
      allValues.priority === "rate" &&
      (changedValues.rateMin !== undefined ||
        changedValues.rateMax !== undefined)
    ) {
      setLastRateSelection({
        rateMin: allValues.rateMin,
        rateMax: allValues.rateMax,
      });
    }

    if (changedValues.bidRange) {
      configForm.setFieldsValue({
        bidRange: normalizeBidRange(
          changedValues.bidRange[0],
          changedValues.bidRange[1],
        ),
      });
    }
  };

  const resolveBidRangePointerValue = useCallback((clientX: number) => {
    const axis = bidRangeAxisRef.current;
    if (!axis) {
      return DEFAULT_BID_MIN;
    }
    const rect = axis.getBoundingClientRect();
    if (rect.width <= 0) {
      return DEFAULT_BID_MIN;
    }
    const percent = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    return normalizeBidAmount(
      DEFAULT_BID_MIN + percent * (DEFAULT_BID_MAX - DEFAULT_BID_MIN),
      DEFAULT_BID_MIN,
    );
  }, []);

  const applyBidRangePointerValue = useCallback(
    (field: "bidMin" | "bidMax", value: number) => {
      const [bidMin, bidMax] = normalizeBidRange(
        configForm.getFieldValue("bidRange")?.[0],
        configForm.getFieldValue("bidRange")?.[1],
      );
      if (field === "bidMin") {
        configForm.setFieldValue("bidRange", [Math.min(value, bidMax), bidMax]);
        return;
      }
      configForm.setFieldValue("bidRange", [bidMin, Math.max(value, bidMin)]);
    },
    [configForm],
  );

  const handleBidRangePointerDown = useCallback(
    (
      field: "bidMin" | "bidMax" | null,
      event: React.PointerEvent<HTMLDivElement>,
    ) => {
      if (!currentShopCanWrite) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      const value = resolveBidRangePointerValue(event.clientX);
      const [bidMin, bidMax] = normalizeBidRange(
        configForm.getFieldValue("bidRange")?.[0],
        configForm.getFieldValue("bidRange")?.[1],
      );
      const nextField =
        field ||
        (Math.abs(value - bidMin) <= Math.abs(value - bidMax)
          ? "bidMin"
          : "bidMax");
      setActiveBidRangeHandle(nextField);
      bidRangeAxisRef.current?.setPointerCapture(event.pointerId);
      applyBidRangePointerValue(nextField, value);
    },
    [
      applyBidRangePointerValue,
      configForm,
      currentShopCanWrite,
      resolveBidRangePointerValue,
    ],
  );

  const handleBidRangePointerMove = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (!activeBidRangeHandle || !currentShopCanWrite) {
        return;
      }
      event.preventDefault();
      applyBidRangePointerValue(
        activeBidRangeHandle,
        resolveBidRangePointerValue(event.clientX),
      );
    },
    [
      activeBidRangeHandle,
      applyBidRangePointerValue,
      currentShopCanWrite,
      resolveBidRangePointerValue,
    ],
  );

  const handleBidRangePointerEnd = useCallback(
    (event: React.PointerEvent<HTMLDivElement>) => {
      if (bidRangeAxisRef.current?.hasPointerCapture(event.pointerId)) {
        bidRangeAxisRef.current.releasePointerCapture(event.pointerId);
      }
      setActiveBidRangeHandle(null);
    },
    [],
  );

  return (
    <PageContainer>
      {isImpersonated ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`正在以 ${currentUser?.nickname || currentUser?.username || currentUser?.userId} 的网销视角查看`}
          action={
            <Button size="small" onClick={() => void handleStopImpersonation()}>
              退出模拟
            </Button>
          }
        />
      ) : null}
      <div className={pageStyles.toolbar}>
        <Form
          form={searchForm}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={handleSearch}
        >
          <Form.Item name="keyword">
            <Input
              allowClear
              style={getResponsiveControlStyle(isCompact, 320)}
              prefix={<ShopOutlined />}
              placeholder="搜索门店名称或店铺 ID"
            />
          </Form.Item>
          {canUseBindingDomain ? (
            <Form.Item name="bindingDomain" initialValue="ALL">
              <Select
                style={getResponsiveControlStyle(isCompact, 160)}
                options={ADMIN_BINDING_DOMAIN_OPTIONS}
                onChange={(value) => {
                  searchForm.setFieldValue("targetUserId", undefined);
                  setBindingDomain(value);
                  setTargetUserId(undefined);
                }}
              />
            </Form.Item>
          ) : null}
          {canUseBindingDomain ? (
            <Form.Item name="bindingStatus" initialValue="ALL">
              <Select
                style={getResponsiveControlStyle(isCompact, 160)}
                options={BINDING_STATUS_OPTIONS}
              />
            </Form.Item>
          ) : null}
          <Form.Item name="promotionStatus" initialValue="ALL">
            <Select
              style={getResponsiveControlStyle(isCompact, 150)}
              options={PROMOTION_STATUS_OPTIONS}
            />
          </Form.Item>
          {hasExpandedScope ? (
            <Form.Item name="targetUserId">
              <Select
                allowClear
                showSearch
                filterOption={false}
                placeholder="所属用户"
                style={getResponsiveControlStyle(isCompact, 220)}
                loading={visibleUserLoading}
                onSearch={handleVisibleUserSearch}
                onFocus={() => void loadVisibleUsers()}
                onChange={(value) => setTargetUserId(value)}
                options={visibleUsers.map((item) => ({
                  label: item.displayName,
                  value: item.userId,
                }))}
              />
            </Form.Item>
          ) : null}
          <Space className={pageStyles.searchActions}>
            <Button type="primary" htmlType="submit">
              搜索
            </Button>
            <Button onClick={handleReset}>重置</Button>
            <Button
              icon={<ReloadOutlined />}
              loading={loading}
              onClick={() => void loadData()}
            >
              刷新
            </Button>
            {canUseBindingDomain ? (
              <Button
                disabled={!targetUserId || bindingDomain !== "SALES"}
                onClick={() => void handleStartSalesImpersonation()}
              >
                模拟网销视角
              </Button>
            ) : null}
            <PermissionControl
              anyPermissions={[
                "shop:master-console:list",
                "sales:master-console:list",
                "youzan:master-console:list",
              ]}
            >
              <Button
                icon={<DownloadOutlined />}
                loading={exporting}
                onClick={() => void handleExport()}
              >
                导出
              </Button>
            </PermissionControl>
          </Space>
        </Form>
        <Segmented<DashboardStatsRange>
          className={styles.statsRangeSegmented}
          value={statsRange}
          options={DASHBOARD_STATS_RANGE_OPTIONS.map((item) => ({
            label: item.label,
            value: item.value,
          }))}
          onChange={(value) => {
            setCurrent(1);
            setStatsRange(value);
          }}
        />
      </div>

      <Table<MasterConsoleShopRow>
        rowKey={(record) =>
          canUseBindingDomain
            ? `shop-${record.shopId}`
            : `${record.bindingDomain || "ORDINARY"}-${record.bindingId}`
        }
        loading={loading}
        columns={columns}
        dataSource={records}
        size={isCompact ? "small" : "middle"}
        scroll={{
          x: isYouzanPortal ? 1600 : canUseBindingDomain ? 2020 : 1900,
        }}
        pagination={{
          current,
          pageSize,
          total,
          showQuickJumper: !isCompact,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (page, size) => {
            setCurrent(page);
            setPageSize(size);
          },
        }}
        onChange={(_, __, sorter, extra) => {
          if (extra.action !== "sort") {
            return;
          }
          const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
          const nextSortOrder =
            activeSorter?.columnKey === "powerBalance"
              ? activeSorter.order || null
              : null;
          setCurrent(1);
          setPowerBalanceSortOrder(nextSortOrder);
        }}
        locale={{
          emptyText: (
            <Space orientation="vertical" size={12}>
              <Text type="secondary">
                {hasExpandedScope && targetUserId
                  ? "该用户当前还没有可见门店。"
                  : "当前还没有绑定门店，请先完成绑定后再进入总控台。"}
              </Text>
              <PermissionControl
                anyPermissions={[
                  "shop:binding:list",
                  "sales:binding:list",
                  "youzan:binding:list",
                ]}
              >
                <Button type="primary" onClick={() => void navigate("/shops")}>
                  去绑定门店
                </Button>
              </PermissionControl>
            </Space>
          ),
        }}
      />

      <Modal
        title={
          currentShop
            ? `${currentShop.shopName || currentShop.poiId} 修改当前出价`
            : "修改当前出价"
        }
        open={manualBidModalOpen}
        onCancel={() => {
          if (manualBidSaving) {
            return;
          }
          setManualBidModalOpen(false);
          setCurrentShop(null);
          manualBidForm.resetFields();
        }}
        onOk={() => void handleSaveManualBid()}
        confirmLoading={manualBidSaving}
        okText="确认"
        cancelText="取消"
        width={getResponsiveOverlayWidth(isCompact, 420, 420)}
        destroyOnHidden
      >
        <Form form={manualBidForm} layout="vertical">
          <Form.Item label="当前出价" required>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Form.Item
                name="bidAmount"
                noStyle
                rules={[
                  { required: true, message: "请输入出价" },
                  {
                    type: "number",
                    min: MANUAL_BID_MIN_AMOUNT,
                    max: Number(currentShop?.currentBid?.maxBidAmount ?? 4),
                    message: `出价范围为${formatBidAmount(
                      MANUAL_BID_MIN_AMOUNT,
                    )}-${formatBidAmount(
                      currentShop?.currentBid?.maxBidAmount ?? 4,
                    )}元`,
                  },
                ]}
              >
                <InputNumber
                  min={MANUAL_BID_MIN_AMOUNT}
                  max={Number(currentShop?.currentBid?.maxBidAmount ?? 4)}
                  precision={2}
                  step={MANUAL_BID_STEP}
                  style={{ flex: 1, minWidth: 0 }}
                  placeholder="0.1-4"
                />
              </Form.Item>
              <Text style={{ flexShrink: 0, whiteSpace: "nowrap" }}>元/次</Text>
            </div>
          </Form.Item>
          <Text type="secondary">
            修改后，当前窗口后续出价将以此为基础继续计算
          </Text>
        </Form>
      </Modal>

      <Modal
        title={
          currentShop
            ? `${currentShop.shopName || currentShop.poiId} 推广配置`
            : "推广配置"
        }
        open={configModalOpen}
        onCancel={() => {
          if (currentShop && canWriteRecord(currentShop)) {
            reportAdminBehaviorEvent(
              "ADMIN_PROMOTION_CONFIG_CANCEL",
              currentShop.shopId,
            );
          }
          setConfigModalOpen(false);
          setCurrentShop(null);
          setCurrentSettings(null);
          configForm.resetFields();
        }}
        onOk={() => {
          if (currentShopCanWrite) {
            void handleSaveConfig();
            return;
          }
          setConfigModalOpen(false);
          setCurrentShop(null);
          setCurrentSettings(null);
          configForm.resetFields();
        }}
        confirmLoading={configSaving}
        okText={currentShopCanWrite ? "保存配置" : "关闭"}
        cancelButtonProps={{
          style: { display: currentShopCanWrite ? undefined : "none" },
        }}
        width={getResponsiveOverlayWidth(isCompact, 760, 760)}
        styles={{
          body: {
            maxHeight: isCompact ? "58vh" : "62vh",
            overflowY: "auto",
            paddingRight: isCompact ? 4 : 8,
          },
        }}
        destroyOnHidden
      >
        {configLoading ? (
          <div style={{ padding: 48, textAlign: "center" }}>
            <Spin />
          </div>
        ) : (
          <Form
            form={configForm}
            layout="vertical"
            disabled={!currentShopCanWrite}
            onValuesChange={handleConfigValuesChange}
          >
            <Form.Item
              label="推广策略"
              name="priority"
              rules={[{ required: true, message: "请选择推广策略" }]}
            >
              <Radio.Group
                options={[
                  { label: "费率优先", value: "rate" },
                  { label: "曝光优先", value: "exposure" },
                ]}
              />
            </Form.Item>

            <Form.Item
              label="推广时段"
              name="timeMode"
              rules={[{ required: true, message: "请选择推广时段" }]}
            >
              <Radio.Group
                options={[
                  {
                    label: (
                      <Space size={6}>
                        <span>
                          让AI自动管理
                          {currentSettings?.autoTimeModeHintText
                            ? `（${currentSettings.autoTimeModeHintText}）`
                            : ""}
                        </span>
                        {currentSettings?.autoTimeModeAvailable === false ? (
                          <LockOutlined style={{ color: "#8c8c8c" }} />
                        ) : null}
                      </Space>
                    ),
                    value: "auto",
                    disabled: currentSettings?.autoTimeModeAvailable === false,
                  },
                  { label: "按营业时长", value: "business" },
                  { label: "自定义时段", value: "custom" },
                ]}
              />
            </Form.Item>

            <Form.Item shouldUpdate noStyle>
              {({ getFieldValue }) =>
                getFieldValue("timeMode") === "custom" ? (
                  <Form.List name="customSlots">
                    {(fields, { add, remove }) => (
                      <Space
                        orientation="vertical"
                        size={12}
                        style={{ width: "100%", marginBottom: 16 }}
                      >
                        {fields.map((field) => (
                          <div
                            key={field.key}
                            style={{
                              width: "100%",
                              display: "flex",
                              alignItems: "flex-end",
                              gap: isCompact ? 8 : 12,
                            }}
                          >
                            <div
                              style={{
                                flex: 1,
                                minWidth: 0,
                              }}
                            >
                              {!isCompact ? (
                                <Text type="secondary">开始</Text>
                              ) : null}
                              <Space.Compact style={{ width: "100%" }}>
                                <Form.Item
                                  name={[field.name, "startHour"]}
                                  rules={[
                                    {
                                      required: true,
                                      message: "请选择开始小时",
                                    },
                                  ]}
                                  style={{ flex: 1, marginBottom: 0 }}
                                >
                                  <Select
                                    placeholder="时"
                                    options={HOUR_OPTIONS}
                                  />
                                </Form.Item>
                                <Form.Item
                                  name={[field.name, "startMinute"]}
                                  rules={[
                                    {
                                      required: true,
                                      message: "请选择开始分钟",
                                    },
                                  ]}
                                  style={{ flex: 1, marginBottom: 0 }}
                                >
                                  <Select
                                    placeholder="分"
                                    options={MINUTE_OPTIONS}
                                  />
                                </Form.Item>
                              </Space.Compact>
                            </div>
                            <div
                              style={{
                                flex: 1,
                                minWidth: 0,
                              }}
                            >
                              {!isCompact ? (
                                <Text type="secondary">结束</Text>
                              ) : null}
                              <Space.Compact style={{ width: "100%" }}>
                                <Form.Item
                                  name={[field.name, "endHour"]}
                                  rules={[
                                    {
                                      required: true,
                                      message: "请选择结束小时",
                                    },
                                  ]}
                                  style={{ flex: 1, marginBottom: 0 }}
                                >
                                  <Select
                                    placeholder="时"
                                    options={HOUR_OPTIONS}
                                  />
                                </Form.Item>
                                <Form.Item
                                  name={[field.name, "endMinute"]}
                                  rules={[
                                    {
                                      required: true,
                                      message: "请选择结束分钟",
                                    },
                                  ]}
                                  style={{ flex: 1, marginBottom: 0 }}
                                >
                                  <Select
                                    placeholder="分"
                                    options={MINUTE_OPTIONS}
                                  />
                                </Form.Item>
                              </Space.Compact>
                            </div>
                            <Button
                              danger
                              onClick={() => remove(field.name)}
                              style={{ flexShrink: 0 }}
                            >
                              删除
                            </Button>
                          </div>
                        ))}
                        <Button
                          onClick={() =>
                            add({
                              startHour: "10",
                              startMinute: "00",
                              endHour: "11",
                              endMinute: "00",
                            })
                          }
                        >
                          新增时段
                        </Button>
                      </Space>
                    )}
                  </Form.List>
                ) : null
              }
            </Form.Item>

            <Form.Item label="费率区间" style={{ marginBottom: 8 }}>
              <Form.Item shouldUpdate noStyle>
                {({ getFieldValue }) => {
                  const isExposurePriority =
                    getFieldValue("priority") === "exposure";

                  return (
                    <div
                      style={{
                        width: "100%",
                        display: "flex",
                        alignItems: "center",
                        gap: isCompact ? 8 : 12,
                      }}
                    >
                      <Form.Item
                        name="rateMin"
                        rules={[{ required: true, message: "请输入最小费率" }]}
                        style={{ flex: 1, marginBottom: 0 }}
                      >
                        <Select
                          disabled={!currentShopCanWrite || isExposurePriority}
                          options={Array.from(
                            { length: 20 },
                            (_, index) => index + 1,
                          ).map((value) => ({
                            label: `${value}%`,
                            value,
                          }))}
                        />
                      </Form.Item>
                      <Text
                        type="secondary"
                        style={{ lineHeight: "32px", flexShrink: 0 }}
                      >
                        -
                      </Text>
                      <Form.Item
                        name="rateMax"
                        rules={[{ required: true, message: "请输入最大费率" }]}
                        style={{ flex: 1, marginBottom: 0 }}
                      >
                        <Select
                          disabled={!currentShopCanWrite || isExposurePriority}
                          options={Array.from(
                            { length: 20 },
                            (_, index) => index + 1,
                          ).map((value) => ({
                            label: `${value}%`,
                            value,
                          }))}
                        />
                      </Form.Item>
                    </div>
                  );
                }}
              </Form.Item>
            </Form.Item>
            <Form.Item shouldUpdate noStyle>
              {({ getFieldValue }) =>
                getFieldValue("priority") === "exposure" ? (
                  <Text type="secondary">曝光优先下费率固定展示，不可编辑</Text>
                ) : null
              }
            </Form.Item>

            <Form.Item label="AI出价范围" style={{ marginTop: 16 }}>
              <Form.Item shouldUpdate noStyle>
                {({ getFieldValue }) => {
                const [bidMin, bidMax] = normalizeBidRange(
                  getFieldValue("bidRange")?.[0],
                  getFieldValue("bidRange")?.[1],
                );
                const bidRangeSpan = DEFAULT_BID_MAX - DEFAULT_BID_MIN;
                const activeLeft =
                  ((bidMin - DEFAULT_BID_MIN) / bidRangeSpan) * 100;
                const activeRight =
                  100 - ((bidMax - DEFAULT_BID_MIN) / bidRangeSpan) * 100;
                const minLeft =
                  ((bidMin - DEFAULT_BID_MIN) / bidRangeSpan) * 100;
                const maxLeft =
                  ((bidMax - DEFAULT_BID_MIN) / bidRangeSpan) * 100;
                return (
                  <Space
                    direction="vertical"
                    size={6}
                    style={{ width: "100%" }}
                  >
                    <div
                      ref={bidRangeAxisRef}
                      onPointerDown={(event) =>
                        handleBidRangePointerDown(null, event)
                      }
                      onPointerMove={handleBidRangePointerMove}
                      onPointerUp={handleBidRangePointerEnd}
                      onPointerCancel={handleBidRangePointerEnd}
                      style={{
                        position: "relative",
                        height: 42,
                        margin: "0 8px",
                        cursor: currentShopCanWrite ? "pointer" : "not-allowed",
                        touchAction: "none",
                      }}
                    >
                      <div
                        style={{
                          position: "absolute",
                          left: 0,
                          right: 0,
                          top: 18,
                          height: 4,
                          borderRadius: 999,
                          background: "#f0f0f0",
                        }}
                      />
                      <div
                        style={{
                          position: "absolute",
                          left: `${activeLeft}%`,
                          right: `${activeRight}%`,
                          top: 18,
                          height: 4,
                          borderRadius: 999,
                          background: "#303133",
                        }}
                      />
                      <div
                        role="slider"
                        aria-label="最小出价"
                        aria-valuemin={DEFAULT_BID_MIN}
                        aria-valuemax={bidMax}
                        aria-valuenow={bidMin}
                        tabIndex={currentShopCanWrite ? 0 : -1}
                        onPointerDown={(event) =>
                          handleBidRangePointerDown("bidMin", event)
                        }
                        style={{
                          position: "absolute",
                          left: `${minLeft}%`,
                          top: 9,
                          zIndex: activeBidRangeHandle === "bidMin" ? 3 : 2,
                          width: 22,
                          height: 22,
                          borderRadius: "50%",
                          border: "2px solid #303133",
                          background: "#ffffff",
                          boxShadow: "0 2px 8px rgba(48, 49, 51, 0.24)",
                          transform: "translateX(-50%)",
                          cursor: currentShopCanWrite
                            ? "grab"
                            : "not-allowed",
                        }}
                      />
                      <div
                        role="slider"
                        aria-label="最大出价"
                        aria-valuemin={bidMin}
                        aria-valuemax={DEFAULT_BID_MAX}
                        aria-valuenow={bidMax}
                        tabIndex={currentShopCanWrite ? 0 : -1}
                        onPointerDown={(event) =>
                          handleBidRangePointerDown("bidMax", event)
                        }
                        style={{
                          position: "absolute",
                          left: `${maxLeft}%`,
                          top: 9,
                          zIndex: activeBidRangeHandle === "bidMax" ? 3 : 2,
                          width: 22,
                          height: 22,
                          borderRadius: "50%",
                          border: "2px solid #303133",
                          background: "#ffffff",
                          boxShadow: "0 2px 8px rgba(48, 49, 51, 0.24)",
                          transform: "translateX(-50%)",
                          cursor: currentShopCanWrite
                            ? "grab"
                            : "not-allowed",
                        }}
                      />
                    </div>
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                      }}
                    >
                      <Text type="secondary">{DEFAULT_BID_MIN}元</Text>
                      <Text type="secondary">
                        当前范围：{bidMin.toFixed(1)}元 - {bidMax.toFixed(1)}
                        元
                      </Text>
                      <Text type="secondary">{DEFAULT_BID_MAX}元</Text>
                    </div>
                  </Space>
                );
                }}
              </Form.Item>
            </Form.Item>
          </Form>
        )}
      </Modal>

      <Drawer
        title={
          currentShop
            ? `${currentShop.shopName || currentShop.poiId} 推广日志`
            : "推广日志"
        }
        open={logDrawerOpen}
        size={getResponsiveDrawerWidth(isCompact, 960)}
        onClose={() => {
          setLogDrawerOpen(false);
          setCurrentShop(null);
          setLogRecords([]);
          setLogSummary(null);
          setLogTotal(0);
        }}
        destroyOnHidden
      >
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Space style={{ justifyContent: "space-between", width: "100%" }}>
            <Space size={12} wrap>
              <Title level={5} style={{ margin: 0 }}>
                {currentShop?.shopName || currentShop?.poiId || "推广日志"}
              </Title>
              <Tooltip
                title={renderBusinessStatusTooltip(logSummary)}
                styles={STATUS_TOOLTIP_STYLES}
              >
                {renderBusinessStatusTag(logSummary)}
              </Tooltip>
              <Tooltip
                title={renderPeakStatusTooltip(logSummary)}
                styles={STATUS_TOOLTIP_STYLES}
              >
                {renderPeakStatusTag(logSummary)}
              </Tooltip>
              <Text type="secondary">
                商圈平均出价：
                {logSummaryLoading
                  ? "查询中..."
                  : formatMoney(logSummary?.latestPeerAverageBid ?? null)}
              </Text>
            </Space>
            <Button
              icon={<ReloadOutlined />}
              loading={logLoading}
              onClick={() => {
                if (currentShop) {
                  void loadLogs(currentShop);
                }
              }}
            >
              刷新日志
            </Button>
          </Space>

          <Table<PromotionLogRecord>
            rowKey={(record) =>
              `${record.slotTime}-${record.status}-${record.type}`
            }
            loading={logLoading}
            columns={logColumns}
            dataSource={logRecords}
            size={isCompact ? "small" : "middle"}
            pagination={{
              current: logPageNum,
              pageSize: logPageSize,
              total: logTotal,
              showQuickJumper: !isCompact,
              showSizeChanger: true,
              showTotal: (value) => `共 ${value} 条`,
              onChange: (page, size) => {
                if (!currentShop) {
                  return;
                }
                void loadLogs(currentShop, page, size);
              },
            }}
            scroll={{ x: 960 }}
            locale={{ emptyText: "最近 7 天暂无调价日志" }}
          />
        </Space>
      </Drawer>
    </PageContainer>
  );
};

export default MasterConsole;
