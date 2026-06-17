import request from "@/utils/request";
import type {
  AdminPowerGrantRecord,
  PageInfo,
  PowerAccountSummary,
  PowerConsumeRecord,
  PowerDetailRecordQueryParam,
  PowerManagementQueryParam,
  PowerManagementRow,
  PowerRechargeRecord,
} from "@/types";

const POWER_MANAGE_BASE_URL = "/system/power-management";

export async function getPowerManagementAccounts(
  params: PowerManagementQueryParam,
): Promise<PageInfo<PowerManagementRow>> {
  const response = await request.get<unknown, { data: PageInfo<PowerManagementRow> }>(
    `${POWER_MANAGE_BASE_URL}/accounts`,
    {
      params,
    },
  );
  return response.data;
}

export async function getPowerAccountSummary(
  shopId: number,
): Promise<PowerAccountSummary> {
  const response = await request.get<unknown, { data: PowerAccountSummary }>(
    `${POWER_MANAGE_BASE_URL}/summary`,
    {
      params: { shopId },
    },
  );
  return response.data;
}

export async function getPowerRechargeRecords(
  params: PowerDetailRecordQueryParam,
): Promise<PageInfo<PowerRechargeRecord>> {
  const response = await request.get<unknown, { data: PageInfo<PowerRechargeRecord> }>(
    `${POWER_MANAGE_BASE_URL}/recharges`,
    {
      params,
    },
  );
  return response.data;
}

export async function getPowerGrantRecords(
  params: PowerDetailRecordQueryParam,
): Promise<PageInfo<AdminPowerGrantRecord>> {
  const response = await request.get<unknown, { data: PageInfo<AdminPowerGrantRecord> }>(
    `${POWER_MANAGE_BASE_URL}/grants`,
    {
      params,
    },
  );
  return response.data;
}

export async function getPowerConsumeRecords(
  params: PowerDetailRecordQueryParam,
): Promise<PageInfo<PowerConsumeRecord>> {
  const response = await request.get<unknown, { data: PageInfo<PowerConsumeRecord> }>(
    `${POWER_MANAGE_BASE_URL}/consumes`,
    {
      params,
    },
  );
  return response.data;
}
