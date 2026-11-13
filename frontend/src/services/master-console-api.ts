import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  PageInfo,
  ShopOperationSettingsUpdateParam,
  VisibleUserOption,
  ImAutoReplyLog,
  ImAutoReplyLogQuery,
  ImConversationHistoryPage,
  ImBroadcastSetting,
  ImBroadcastSettingHistory,
  ImBroadcastSettingUpdateParam,
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

export async function getMasterConsoleBroadcastSetting(
  shopId: number,
): Promise<ImBroadcastSetting> {
  const response = await request.get<unknown, { data: ImBroadcastSetting }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/broadcast-setting`,
  );
  return response.data;
}

export async function saveMasterConsoleBroadcastSetting(
  shopId: number,
  params: ImBroadcastSettingUpdateParam,
): Promise<ImBroadcastSetting> {
  const response = await request.put<unknown, { data: ImBroadcastSetting }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/broadcast-setting`, params,
  );
  return response.data;
}

export async function deleteMasterConsoleBroadcastSetting(
  shopId: number,
): Promise<ImBroadcastSetting> {
  const response = await request.delete<unknown, { data: ImBroadcastSetting }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/broadcast-setting`,
  );
  return response.data;
}

export async function optimizeMasterConsoleBroadcastContent(
  shopId: number,
  content: string,
): Promise<string> {
  const response = await request.post<unknown, { data: { content: string } }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/broadcast-content/optimize`,
    { content },
  );
  return response.data.content;
}

export async function getMasterConsoleBroadcastSettingHistory(
  shopId: number,
  params?: { pageNum?: number; pageSize?: number },
): Promise<PageInfo<ImBroadcastSettingHistory>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<ImBroadcastSettingHistory> }
  >(`${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/broadcast-setting/history`, { params });
  return response.data;
}

export async function getMasterConsoleImAutoReplyLogs(
  shopId: number,
  params: ImAutoReplyLogQuery,
): Promise<PageInfo<ImAutoReplyLog>> {
  const response = await request.get<unknown, { data: PageInfo<ImAutoReplyLog> }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/im-auto-replies`, { params },
  );
  return response.data;
}

export async function getMasterConsoleImConversationHistory(
  shopId: number,
  conversationId: number,
  params?: { cursorTime?: string; cursorId?: number; pageSize?: number },
): Promise<ImConversationHistoryPage> {
  const response = await request.get<unknown, { data: ImConversationHistoryPage }>(
    `${MASTER_CONSOLE_BASE_URL}/shops/${shopId}/im-conversations/${conversationId}/messages`, { params },
  );
  return response.data;
}
