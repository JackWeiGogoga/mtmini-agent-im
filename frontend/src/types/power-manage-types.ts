import type { PageParam } from "./common-types";
import type { ShopBindingDomain, ShopBindingStatus } from "./shop-manage-types";

export type PowerRiskStatus = "NORMAL" | "WARNING" | "CRITICAL";

export interface PowerManagementQueryParam extends PageParam {
  keyword?: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
  bindingStatus?: ShopBindingStatus;
  riskStatus?: PowerRiskStatus;
  sortField?: "balance";
  sortOrder?: "ascend" | "descend";
}

export interface PowerManagementRow {
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
  salesBindingId?: number | null;
  salesBoundUserId?: string;
  salesBoundUserName?: string;
  balance: number;
  rechargeBalance: number;
  grantBalance: number;
  totalRecharge: number;
  totalGrant: number;
  totalConsume: number;
  riskStatus: PowerRiskStatus;
}

export interface PowerAccountSummary {
  shopId: number;
  poiId: string;
  epoiId?: string;
  shopName?: string;
  address?: string;
  boundUserId?: string;
  boundUserName?: string;
  bindingStatus?: ShopBindingStatus;
  bindingStatusName?: string;
  ordinaryBindingId?: number | null;
  ordinaryBoundUserId?: string;
  ordinaryBoundUserName?: string;
  salesBindingId?: number | null;
  salesBoundUserId?: string;
  salesBoundUserName?: string;
  balance: number;
  rechargeBalance: number;
  grantBalance: number;
  totalRecharge: number;
  totalGrant: number;
  totalConsume: number;
  riskStatus: PowerRiskStatus;
}

export interface PowerDetailRecordQueryParam extends PageParam {
  shopId: number;
  startTime?: string;
  endTime?: string;
}

export interface PowerRechargeRecord {
  id: number;
  rechargeNo: string;
  shopId: number;
  powerAmount: number;
  unitPower: number;
  unitPriceFen: number;
  totalAmountFen: number;
  payChannel?: string;
  payStatus: number;
  payStatusDesc?: string;
  paymentOrderNo?: string;
  transactionId?: string;
  paidAt?: string;
  createTime: string;
}

export interface PowerConsumeDetail {
  sourceBalanceType: string;
  sourceBalanceTypeText?: string;
  powerAmount: number;
  balanceBefore: number;
  balanceAfter: number;
}

export interface PowerConsumeRecord {
  id: number;
  consumeNo: string;
  shopId: number;
  sourceType: string;
  sourceTypeText?: string;
  sourceId?: string;
  requestNo?: string;
  powerAmount: number;
  balanceBefore: number;
  balanceAfter: number;
  remark?: string;
  createTime: string;
  details: PowerConsumeDetail[];
}
