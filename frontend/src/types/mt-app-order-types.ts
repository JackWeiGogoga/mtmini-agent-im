import type { PageParam } from "./common-types";

export interface MtAppOrderQueryParam extends PageParam {
  orderId?: string;
  orderViewId?: string;
  poiId?: string;
  shopName?: string;
  payStatus?: number;
  rightStatus?: string;
  version?: string;
  versionName?: string;
  targetUserId?: string;
  orderStartTime?: string;
  orderEndTime?: string;
}

export interface MtAppOrderRow {
  id: number;
  shopId?: number;
  poiId?: string;
  orderId: string;
  orderViewId?: string;
  appId?: string;
  serviceCode?: string;
  payStatus?: number;
  version?: string;
  versionName?: string;
  days?: number;
  rightStatus?: string;
  realPayPrice?: number;
  type?: number;
  address?: string;
  shopName?: string;
  phone?: string;
  startTime?: string;
  endTime?: string;
  orderTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface MtAppOrderVersionOption {
  version: string;
  versionName?: string;
}

export interface MtAppOrderSettlementQueryParam extends PageParam {
  statsMonth?: string;
  poiId?: string;
}

export interface MtAppOrderSettlementRow {
  id: number;
  shopId?: number;
  poiId?: string;
  orderId: string;
  orderTime?: string;
  shopName?: string;
  phone?: string;
  paidSpec?: string;
  paidAmountFen?: number;
  paidAmount?: number;
  currentMonthUsedDays?: number;
  previousMonthUsedDays?: number;
  remainingUnusedDays?: number;
  meituanDeductionAmount?: number;
  previousMonthActualIncome?: number;
  currentMonthActualIncome?: number;
  remainingUnsettledActualIncome?: number;
  actualIncome?: number;
}
