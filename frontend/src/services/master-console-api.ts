import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  ManualPromotionBidResult,
  PageInfo,
  PromotionLogRecord,
  PromotionLogSummary,
  PromotionSettings,
  PromotionSettingsUpdateParam,
  VisibleUserOption,
} from "@/types";

const MASTER_CONSOLE_BASE_URL = "/system/master-console";

export async function getMasterConsoleShops(
  params: MasterConsoleQueryParam,
): Promise<PageInfo<MasterConsoleShopRow>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<MasterConsoleShopRow> }
  >(`${MASTER_CONSOLE_BASE_URL}/shops`, {
    params,
  });
  return response.data;
}

export async function exportMasterConsoleShops(
  params: MasterConsoleQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${MASTER_CONSOLE_BASE_URL}/shops/export`,
    params,
    "总控台.xlsx",
  );
}

export async function getMasterConsoleVisibleUsers(
  keyword?: string,
  bindingDomain?: string,
): Promise<
  VisibleUserOption[]
> {
  const response = await request.get<unknown, { data: VisibleUserOption[] }>(
    `${MASTER_CONSOLE_BASE_URL}/visible-users`,
    {
      params: { keyword, bindingDomain },
    },
  );
  return response.data;
}

export async function getMasterConsolePromotionSettings(
  shopId: number,
  bindingDomain?: string,
): Promise<PromotionSettings> {
  const response = await request.get<unknown, { data: PromotionSettings }>(
    `${MASTER_CONSOLE_BASE_URL}/promotion-settings`,
    {
      params: { shopId, bindingDomain },
    },
  );
  return response.data;
}

export async function updateMasterConsolePromotionSettings(
  shopId: number,
  params: PromotionSettingsUpdateParam,
  bindingDomain?: string,
): Promise<PromotionSettings> {
  const response = await request.put<unknown, { data: PromotionSettings }>(
    `${MASTER_CONSOLE_BASE_URL}/promotion-settings`,
    params,
    {
      params: { shopId, bindingDomain },
    },
  );
  return response.data;
}

export async function updateMasterConsoleManualPromotionBid(
  shopId: number,
  bidAmount: number,
  bindingDomain?: string,
): Promise<ManualPromotionBidResult> {
  const response = await request.post<unknown, { data: ManualPromotionBidResult }>(
    `${MASTER_CONSOLE_BASE_URL}/promotion-settings/manual-bid`,
    { bidAmount },
    {
      params: { shopId, bindingDomain },
    },
  );
  return response.data;
}

export async function getMasterConsolePromotionLogs(
  shopId: number,
  bindingDomain?: string,
  pageNum = 1,
  pageSize = 20,
): Promise<PageInfo<PromotionLogRecord>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<PromotionLogRecord> }
  >(`${MASTER_CONSOLE_BASE_URL}/promotion-logs`, {
    params: { shopId, bindingDomain, pageNum, pageSize },
  });
  return response.data;
}

export async function getMasterConsolePromotionLogSummary(
  shopId: number,
  bindingDomain?: string,
): Promise<PromotionLogSummary> {
  const response = await request.get<unknown, { data: PromotionLogSummary }>(
    `${MASTER_CONSOLE_BASE_URL}/promotion-log-summary`,
    {
      params: { shopId, bindingDomain },
    },
  );
  return response.data;
}
