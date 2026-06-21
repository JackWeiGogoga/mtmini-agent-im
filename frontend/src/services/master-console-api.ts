import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  PageInfo,
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
