import type { PageParam } from "./common-types";

/**
 * 门店管理类型定义
 */

export type ShopBindingDomain = "ALL" | "ORDINARY" | "SALES";
export type ShopBindingStatus = "ALL" | "UNBOUND" | "BOUND" | "BOTH";
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
