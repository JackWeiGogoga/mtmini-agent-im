import type { PageParam } from "./common-types";
import type {
  ShopBindingDomain,
  ShopBindingStatus,
} from "./shop-manage-types";

export type DashboardStatsRange = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS";

export interface MasterConsoleQueryParam extends PageParam {
  keyword?: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
  bindingStatus?: ShopBindingStatus;
  statsRange?: DashboardStatsRange;
  sortField?: "powerBalance";
  sortOrder?: "ascend" | "descend";
}

export interface MasterConsoleShopRow {
  bindingId?: number | null;
  shopId: number;
  poiId?: string;
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
  goodCommentCount?: number;
  previousGoodCommentCount?: number;
  badCommentCount?: number;
  previousBadCommentCount?: number;
  repliedBadCommentCount?: number;
  badCommentReplyRate?: number | string;
  previousBadCommentReplyRate?: number | string;
  powerBalance?: number;
  totalRecharge?: number;
  totalGrant?: number;
  totalConsume?: number;
  currentConsumedPower?: number;
  previousConsumedPower?: number;
  authStatus?: number;
  authStatusText?: string;
  reauthRequired?: boolean;
  authStatusChangedAt?: string;
  lastAuthFailureAt?: string;
  rightExpireAt?: string;
  bindTime?: string;
  autoReplyEnabled?: boolean;
  negativeCommentAppealEnabled?: boolean;
}

export interface ShopOperationSettingsUpdateParam {
  autoReplyEnabled: boolean;
  negativeCommentAppealEnabled: boolean;
}

export interface CommentReplyLogRecord {
  id: number;
  commentId?: number;
  commentTime?: string;
  replyTime?: string;
  sentiment?: "GOOD" | "NEUTRAL" | "BAD" | string;
  score?: number;
  customerMessage?: string;
  addComment?: string;
  imageUrls?: string[];
  aiReplyText?: string;
  analysisText?: string;
  autoReplyStatus?: string;
}
