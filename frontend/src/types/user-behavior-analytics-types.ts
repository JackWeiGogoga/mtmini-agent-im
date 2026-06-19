export type UserBehaviorStatsRange = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS";

export interface UserBehaviorAnalyticsQuery {
  keyword?: string;
  statsRange?: UserBehaviorStatsRange;
  sortField?: keyof UserBehaviorAnalyticsRow;
  sortOrder?: "ascend" | "descend" | null;
  pageNum?: number;
  pageSize?: number;
}

export interface UserBehaviorAnalyticsRow {
  shopId: number;
  poiId?: string;
  shopName?: string;
  shopPhone?: string;
  h5AppOpenCount: number;
  pcMasterConsoleOperationCount: number;
  h5ConfigButtonClickCount: number;
  h5UnlockAiPeakClickCount: number;
  h5ConfigModalConfirmClickCount: number;
  h5ConfigModalCancelClickCount: number;
  h5PowerTabClickCount: number;
  h5LogTabClickCount: number;
  h5SupportTabClickCount: number;
  h5RechargeGifClickCount: number;
  h5PowerPayClickCount: number;
  h5RedemptionCodeClickCount: number;
}

export interface UserBehaviorAnalyticsPage {
  total: number;
  list: UserBehaviorAnalyticsRow[];
  pageNum: number;
  pageSize: number;
  pages: number;
}

export type AdminBehaviorEventType = string;
