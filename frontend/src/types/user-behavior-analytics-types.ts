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
  h5PromotionSwitchClickCount: number;
  h5ConfigButtonClickCount: number;
  h5UnlockAiPeakClickCount: number;
  h5ConfigModalConfirmClickCount: number;
  h5ConfigModalCancelClickCount: number;
  h5ManualBidEditOpenCount: number;
  h5ManualBidSubmitClickCount: number;
  h5PowerTabClickCount: number;
  h5LogTabClickCount: number;
  h5SupportTabClickCount: number;
  h5RechargeGifClickCount: number;
  h5PowerPayClickCount: number;
  h5RedemptionCodeClickCount: number;
  adminPromotionSwitchUpdateCount: number;
  adminPromotionConfigSaveCount: number;
  adminPromotionConfigCancelCount: number;
  adminPromotionLogViewCount: number;
}

export interface UserBehaviorAnalyticsPage {
  total: number;
  list: UserBehaviorAnalyticsRow[];
  pageNum: number;
  pageSize: number;
  pages: number;
}

export type AdminBehaviorEventType =
  | "ADMIN_PROMOTION_SWITCH_UPDATE"
  | "ADMIN_PROMOTION_CONFIG_SAVE"
  | "ADMIN_PROMOTION_CONFIG_CANCEL"
  | "ADMIN_PROMOTION_LOG_VIEW";
