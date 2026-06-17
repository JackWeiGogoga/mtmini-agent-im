import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  PageInfo,
  PowerRechargeOrderDetail,
  PowerRechargeOrderQueryParam,
  PowerRechargeOrderRow,
} from "@/types";

const POWER_RECHARGE_ORDER_BASE_URL = "/system/power-recharge-orders";

export async function getPowerRechargeOrders(
  params: PowerRechargeOrderQueryParam,
): Promise<PageInfo<PowerRechargeOrderRow>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<PowerRechargeOrderRow> }
  >(POWER_RECHARGE_ORDER_BASE_URL, { params });
  return response.data;
}

export async function exportPowerRechargeOrders(
  params: PowerRechargeOrderQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${POWER_RECHARGE_ORDER_BASE_URL}/export`,
    params,
    "算力充值订单.xlsx",
  );
}

export async function getPowerRechargeOrderDetail(
  id: number,
): Promise<PowerRechargeOrderDetail> {
  const response = await request.get<unknown, { data: PowerRechargeOrderDetail }>(
    `${POWER_RECHARGE_ORDER_BASE_URL}/${id}`,
  );
  return response.data;
}
