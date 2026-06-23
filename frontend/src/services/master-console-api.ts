import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  CommentReplyLogRecord,
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  PageInfo,
  ShopOperationSettingsUpdateParam,
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
): Promise<VisibleUserOption[]> {
  const response = await request.get<unknown, { data: VisibleUserOption[] }>(
    `${MASTER_CONSOLE_BASE_URL}/visible-users`,
    {
      params: { keyword, bindingDomain },
    },
  );
  return response.data;
}

export async function updateMasterConsoleShopOperationSettings(
  shopId: number,
  params: ShopOperationSettingsUpdateParam,
): Promise<ShopOperationSettingsUpdateParam & { shopId: number }> {
  const response = await request.put<
    unknown,
    { data: ShopOperationSettingsUpdateParam & { shopId: number } }
  >(`${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/operation-settings`, params);
  return response.data;
}

export async function getMasterConsoleCommentReplyLogs(
  shopId: number,
  bindingDomain?: string,
  pageNum = 1,
  pageSize = 20,
): Promise<PageInfo<CommentReplyLogRecord>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<CommentReplyLogRecord> }
  >(`${MASTER_CONSOLE_BASE_URL}/comment-reply-logs`, {
    params: { shopId, bindingDomain, pageNum, pageSize },
  });
  return response.data;
}
