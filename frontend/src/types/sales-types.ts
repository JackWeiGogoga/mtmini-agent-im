import type { PageResult } from "./common-types";

export interface SalesRedemptionCodeRow {
  id: number;
  code: string;
  salesUserId: string;
  salesUserName: string;
  salesUserPhone: string;
  receiptCodeUrl?: string | null;
  createTime?: string;
}

export interface SalesCommissionRow {
  id: number;
  redemptionCode: string;
  rechargeNo: string;
  paidAt?: string;
  shopName: string;
  poiId: string;
  shopPhone?: string;
  powerAmount: number;
  paidAmountFen: number;
  commissionRatio: number;
  commissionAmountFen: number;
  salesUserId: string;
  salesUserName: string;
  salesUserPhone: string;
  receiptCodeUrl?: string | null;
  settlementStatus: string;
  settledAt?: string;
  createTime?: string;
}

export interface SalesSelfConfig {
  salesUserId: string;
  salesUserName: string;
  salesUserPhone?: string;
  redemptionCode?: string | null;
  receiptCodeUrl?: string | null;
  receiptCodeUploadAllowed: boolean;
}

export interface SalesRedemptionCodeGenerateParam {
  phone: string;
}

export interface SalesCommissionQueryParam {
  shopKeyword?: string;
  redemptionCode?: string;
  rechargeNo?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface SalesRedemptionCodeQueryParam {
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

export interface SalesCommissionRatioUpdateParam {
  commissionRatio: number;
}

export interface SalesCommissionSettlementUpdateParam {
  settlementStatus: string;
}

export type SalesRedemptionCodePage = PageResult<SalesRedemptionCodeRow>;
export type SalesCommissionPage = PageResult<SalesCommissionRow>;
