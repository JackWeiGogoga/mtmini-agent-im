import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  MtAppOrderQueryParam,
  MtAppOrderRow,
  MtAppOrderSettlementQueryParam,
  MtAppOrderSettlementRow,
  MtAppOrderVersionOption,
  PageInfo,
} from "@/types";

const MT_APP_ORDER_BASE_URL = "/system/mt-app-orders";

export async function getMtAppOrders(
  params: MtAppOrderQueryParam,
): Promise<PageInfo<MtAppOrderRow>> {
  const response = await request.get<unknown, { data: PageInfo<MtAppOrderRow> }>(
    MT_APP_ORDER_BASE_URL,
    {
      params,
    },
  );
  return response.data;
}

export async function exportMtAppOrders(
  params: MtAppOrderQueryParam,
): Promise<void> {
  await downloadXlsx(`${MT_APP_ORDER_BASE_URL}/export`, params, "美团应用订单.xlsx");
}

export async function getMtAppOrderVersionOptions(): Promise<
  MtAppOrderVersionOption[]
> {
  const response = await request.get<
    unknown,
    { data: MtAppOrderVersionOption[] }
  >(`${MT_APP_ORDER_BASE_URL}/version-options`);
  return response.data;
}

export async function getMtAppOrderSettlementStats(
  params: MtAppOrderSettlementQueryParam,
): Promise<PageInfo<MtAppOrderSettlementRow>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<MtAppOrderSettlementRow> }
  >(`${MT_APP_ORDER_BASE_URL}/settlement-stats`, {
    params,
  });
  return response.data;
}

export async function exportMtAppOrderSettlementStats(
  params: MtAppOrderSettlementQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${MT_APP_ORDER_BASE_URL}/settlement-stats/export`,
    params,
    "美团订单结算统计.xlsx",
  );
}
