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
  h5HomePageViewCount: number;
  h5LogPageViewCount: number;
  h5PowerPageViewCount: number;
  h5KnowledgePageViewCount: number;
  h5SupportPageViewCount: number;
  h5SupportEntryClickCount: number;
  h5ExternalLinkClickCount: number;
  h5RechargeSubmitClickCount: number;
  h5RedemptionApplySuccessCount: number;
  h5LogDetailExpandCount: number;
  h5KnowledgeActionCount: number;
  pcCommentLogViewCount: number;
  pcKnowledgeViewCount: number;
  h5AutoReplyChangeCount: number;
  h5NegativeAppealChangeCount: number;
  pcAutoReplyChangeCount: number;
  pcNegativeAppealChangeCount: number;
  systemAutoDisableCount: number;
}

export interface UserBehaviorAnalyticsPage {
  total: number;
  list: UserBehaviorAnalyticsRow[];
  pageNum: number;
  pageSize: number;
  pages: number;
}

export type AdminBehaviorEventType =
  | "PC_COMMENT_LOG_VIEW"
  | "PC_KNOWLEDGE_VIEW";
