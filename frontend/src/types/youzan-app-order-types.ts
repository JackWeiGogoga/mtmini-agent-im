import type { PageParam } from "./common-types";

export interface YouzanAppOrderQueryParam extends PageParam {
  orderNo?: string;
  kdtId?: number;
  shopName?: string;
  buyerKeyword?: string;
  skuKeyword?: string;
  status?: number;
  useStatus?: string;
  payStartTime?: string;
  payEndTime?: string;
}

export interface YouzanAppOrderRow {
  id: number;
  orderNo: string;
  kdtId?: number;
  rootKdtId?: number;
  appId?: number;
  buyerId?: number;
  buyerPhone?: string;
  openId?: string;
  shopName?: string;
  shopDisplayNo?: string;
  skuVersionText?: string;
  skuIntervalText?: string;
  price?: number;
  status?: number;
  payTime?: string;
  useStatus?: string;
  usedMtShopId?: number;
  usedMtShopName?: string;
  usedRightCode?: string;
  usedAt?: string;
  usedByUsername?: string;
  useRemark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface YouzanAppOrderDetail extends YouzanAppOrderRow {
  yzShopId?: number;
  shopType?: string;
  shopRole?: string;
  env?: string;
  usedByUserId?: string;
}

export interface YouzanOrderRedeemParam {
  orderId: number;
  mtShopId: number;
  remark?: string;
}

export interface YouzanOrderRedeemResult {
  recordId: number;
  orderId: number;
  orderNo: string;
  mtShopId: number;
  mtShopName?: string;
  rightCode: string;
  intervalDays: number;
  previousExpireAt?: string;
  newExpireAt: string;
  usedAt: string;
}
