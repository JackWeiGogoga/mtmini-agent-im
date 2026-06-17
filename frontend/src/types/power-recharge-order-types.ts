import type { PageParam } from "./common-types";

export interface PowerRechargeOrderQueryParam extends PageParam {
  keyword?: string;
  payStatus?: number;
  startTime?: string;
  endTime?: string;
}

export interface PowerRechargeOrderRow {
  id: number;
  rechargeNo: string;
  shopId: number;
  poiId?: string;
  epoiId?: string;
  shopName?: string;
  powerAmount: number;
  unitPower?: number;
  unitPriceFen?: number;
  originalAmountFen?: number;
  totalAmountFen: number;
  discountContextType?: string;
  discountTag?: string;
  discountRateBasisPoints?: number;
  redemptionCode?: string;
  salesUserId?: string;
  salesUserName?: string;
  salesUserPhone?: string;
  commissionWindowStartAt?: string;
  defaultCommissionRatio?: number;
  payChannel?: string;
  payStatus?: number;
  payStatusDesc?: string;
  paymentOrderNo?: string;
  transactionId?: string;
  paidAt?: string;
  createTime?: string;
  updateTime?: string;
}

export type PowerRechargeOrderDetail = PowerRechargeOrderRow;
