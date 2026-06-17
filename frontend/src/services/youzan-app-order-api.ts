import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  PageInfo,
  YouzanAppOrderDetail,
  YouzanAppOrderQueryParam,
  YouzanAppOrderRow,
  YouzanOrderRedeemParam,
  YouzanOrderRedeemResult,
} from "@/types";

const YOUZAN_APP_ORDER_BASE_URL = "/system/youzan-app-orders";

export async function getYouzanAppOrders(
  params: YouzanAppOrderQueryParam,
): Promise<PageInfo<YouzanAppOrderRow>> {
  const response = await request.get<unknown, { data: PageInfo<YouzanAppOrderRow> }>(
    YOUZAN_APP_ORDER_BASE_URL,
    {
      params,
    },
  );
  return response.data;
}

export async function exportYouzanAppOrders(
  params: YouzanAppOrderQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${YOUZAN_APP_ORDER_BASE_URL}/export`,
    params,
    "有赞应用订单.xlsx",
  );
}

export async function getYouzanAppOrderDetail(
  id: number,
): Promise<YouzanAppOrderDetail> {
  const response = await request.get<unknown, { data: YouzanAppOrderDetail }>(
    `${YOUZAN_APP_ORDER_BASE_URL}/${id}`,
  );
  return response.data;
}

export async function redeemYouzanAppOrder(
  params: YouzanOrderRedeemParam,
): Promise<YouzanOrderRedeemResult> {
  const response = await request.post<unknown, { data: YouzanOrderRedeemResult }>(
    `${YOUZAN_APP_ORDER_BASE_URL}/redeem`,
    params,
  );
  return response.data;
}
