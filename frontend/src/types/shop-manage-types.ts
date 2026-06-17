import type { PageParam } from "./common-types";

/**
 * 门店管理类型定义
 */

export type ShopBindingDomain = "ALL" | "ORDINARY" | "SALES";
export type ShopBindingStatus = "ALL" | "UNBOUND" | "BOUND" | "BOTH";
export type PromotionStatus = "ALL" | "ENABLED" | "DISABLED";
export type MtAuthorizationStatus = 1 | 2;

export interface UserShopBinding {
  bindingId?: number | null;
  shopId: number;
  poiId: string;
  epoiId?: string;
  shopName?: string;
  address?: string;
  contactPhone?: string;
  boundUserId?: string;
  boundUserName?: string;
  bindingDomain?: ShopBindingDomain;
  bindingDomainName?: string;
  bindingStatus?: ShopBindingStatus;
  bindingStatusName?: string;
  ordinaryBindingId?: number | null;
  ordinaryBoundUserId?: string;
  ordinaryBoundUserName?: string;
  ordinaryBindTime?: string;
  salesBindingId?: number | null;
  salesBoundUserId?: string;
  salesBoundUserName?: string;
  salesBoundUserPhone?: string;
  salesBindTime?: string;
  powerBalance?: number;
  totalRecharge?: number;
  totalGrant?: number;
  totalConsume?: number;
  rightExpireAt?: string;
  bindTime?: string;
}

export interface BindUserShopParam {
  poiId: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
}

export interface UserShopBindingQueryParam extends PageParam {
  keyword?: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
  bindingStatus?: ShopBindingStatus;
  sortField?: "powerBalance";
  sortOrder?: "ascend" | "descend";
}

export interface BatchBindUserShopParam {
  poiIds: string[];
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
}

export interface VisibleUserOption {
  userId: string;
  username: string;
  nickname?: string;
  displayName: string;
}

export interface BatchBindFailureItem {
  poiId: string;
  errorMessage: string;
}

export interface BatchBindUserShopResult {
  totalCount: number;
  successCount: number;
  failureCount: number;
  successPoiIds: string[];
  failureItems: BatchBindFailureItem[];
}

export interface AdminPowerGrantCreateParam {
  shopId: number;
  powerAmount: number;
  grantReason: string;
  remark?: string;
}

export interface AdminPowerGrantRecord {
  id: number;
  grantNo: string;
  shopId: number;
  poiId?: string;
  shopName?: string;
  powerAmount: number;
  grantReason: string;
  remark?: string;
  operatorUserId?: string;
  operatorUsername?: string;
  status: string;
  createTime: string;
}

export interface PromotionTimeSlot {
  start: string;
  end: string;
}

export interface PromotionStrategyConfig {
  priority: "rate" | "exposure";
  timeMode: "auto" | "business" | "custom";
  rateMode: "control";
  rateMin: string;
  rateMax: string;
  bidMin?: number | null;
  bidMax?: number | null;
  decisionMode?: "rule" | "llm";
  customSlots?: PromotionTimeSlot[];
}

export interface PromotionSettings {
  promotionEnabled: boolean;
  balanceReminderEnabled: boolean;
  strategyConfigured: boolean;
  strategyConfigJson?: string;
  strategyEntryVisible: boolean;
  autoTimeModeAvailable?: boolean;
  autoTimeModeHintText?: string;
}

export interface PromotionSettingsUpdateParam {
  promotionEnabled?: boolean;
  balanceReminderEnabled?: boolean;
  strategyConfigured?: boolean;
  strategyConfigJson?: string;
}

export interface PromotionLogRecord {
  slotTime: string;
  status: string;
  type: string;
  price: string;
  message: string;
  markdownMessage?: string;
}

export interface PromotionLogSummary {
  latestPeerAverageBid?: number | null;
  generatedAt?: string;
  businessStatus?: {
    open?: boolean | null;
    shippingTime?: string | null;
    reason?: string | null;
    currentWindow?: string | null;
  } | null;
  peakStatus?: {
    peakMatched?: boolean | null;
    gatingEnabled?: boolean | null;
    trusted?: boolean | null;
    currentDayType?: string | null;
    profileDayType?: string | null;
    profileStatus?: string | null;
    sampleDayCount?: number | null;
    sampleOrderCount?: number | null;
    unavailableReason?: string | null;
    matchedWindow?: string | null;
    configuredWindows?: string[] | null;
  } | null;
}

export interface CurrentBidSummary {
  inPromotionWindow?: boolean | null;
  editable?: boolean | null;
  disabledReason?: string | null;
  currentBidAmount?: number | null;
  previousBidAmount?: number | null;
  latestExecuteStatus?: string | null;
  displayStatusText?: string | null;
  minBidAmount?: number | null;
  maxBidAmount?: number | null;
  windowKey?: string | null;
  windowStartAt?: string | null;
  windowEndAt?: string | null;
  latestSuccessSlotTime?: string | null;
}

export interface ManualPromotionBidResult {
  previousBidAmount?: number | null;
  effectiveBidAmount?: number | null;
  windowKey?: string | null;
  windowStartAt?: string | null;
  windowEndAt?: string | null;
  attributedSlotTime?: string | null;
  operatedAt?: string | null;
  remoteSynced?: boolean | null;
  recordId?: number | null;
}

export type DashboardStatsRange =
  | "TODAY"
  | "LAST_7_DAYS"
  | "LAST_30_DAYS";

export interface MasterConsoleShopRow {
  bindingId?: number | null;
  shopId: number;
  poiId: string;
  epoiId?: string;
  shopName?: string;
  address?: string;
  boundUserId?: string;
  boundUserName?: string;
  bindingDomain?: ShopBindingDomain;
  bindingDomainName?: string;
  bindingStatus?: ShopBindingStatus;
  bindingStatusName?: string;
  ordinaryBindingId?: number | null;
  ordinaryBoundUserId?: string;
  ordinaryBoundUserName?: string;
  ordinaryBindTime?: string;
  salesBindingId?: number | null;
  salesBoundUserId?: string;
  salesBoundUserName?: string;
  salesBoundUserPhone?: string;
  salesBindTime?: string;
  currentCompletedIncome: number;
  previousCompletedIncome: number;
  currentCompletedOrderCount: number;
  previousCompletedOrderCount: number;
  currentFreeExposure: number;
  previousFreeExposure: number;
  currentPaidExposure: number;
  previousPaidExposure: number;
  latestPromotionBid?: number | null;
  currentBid?: CurrentBidSummary | null;
  promotionBalance?: number | null;
  currentPromotionCost: number;
  previousPromotionCost: number;
  currentCostRatio: number;
  previousCostRatio: number;
  powerBalance?: number | null;
  currentConsumedPower?: number | null;
  previousConsumedPower?: number | null;
  promotionEnabled: boolean;
  strategyConfigured: boolean;
  strategyEntryVisible: boolean;
  authStatus?: MtAuthorizationStatus | number | null;
  authStatusText?: string | null;
  reauthRequired?: boolean | null;
  authStatusChangedAt?: string | null;
  lastAuthFailureAt?: string | null;
  rightExpireAt?: string;
  bindTime?: string;
}

export interface MasterConsoleQueryParam extends PageParam {
  keyword?: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
  bindingStatus?: ShopBindingStatus;
  promotionStatus?: PromotionStatus;
  statsRange?: DashboardStatsRange;
  sortField?: "powerBalance";
  sortOrder?: "ascend" | "descend";
}
