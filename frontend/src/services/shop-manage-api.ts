/**
 * 门店管理 API 服务
 */
import request from "@/utils/request";
import type {
  AdminPowerGrantCreateParam,
  AdminPowerGrantRecord,
  BatchBindUserShopParam,
  BatchBindUserShopResult,
  BindUserShopParam,
  PageInfo,
  UserShopBinding,
  UserShopBindingQueryParam,
  VisibleUserOption,
} from "@/types";

const USER_SHOP_BASE_URL = "/system/user-shops";
const POWER_GRANT_BASE_URL = "/system/shop-power-grants";

export async function getBoundShops(
  params: UserShopBindingQueryParam,
): Promise<PageInfo<UserShopBinding>> {
  const response = await request.get<unknown, { data: PageInfo<UserShopBinding> }>(
    USER_SHOP_BASE_URL,
    {
      params,
    },
  );
  return response.data;
}

export async function getVisibleShopUsers(
  keyword?: string,
  bindingDomain?: string,
): Promise<VisibleUserOption[]> {
  const response = await request.get<unknown, { data: VisibleUserOption[] }>(
    `${USER_SHOP_BASE_URL}/visible-users`,
    {
      params: { keyword, bindingDomain },
    },
  );
  return response.data;
}

export async function bindUserShop(
  params: BindUserShopParam,
): Promise<UserShopBinding> {
  const response = await request.post<unknown, { data: UserShopBinding }>(
    `${USER_SHOP_BASE_URL}/bind`,
    params,
  );
  return response.data;
}

export async function batchBindUserShops(
  params: BatchBindUserShopParam,
): Promise<BatchBindUserShopResult> {
  const response = await request.post<unknown, { data: BatchBindUserShopResult }>(
    `${USER_SHOP_BASE_URL}/bind/batch`,
    params,
  );
  return response.data;
}

export async function unbindUserShop(
  bindingId: number,
  bindingDomain?: string,
): Promise<void> {
  await request.delete(`${USER_SHOP_BASE_URL}/${bindingId}`, {
    params: { bindingDomain },
  });
}

export async function listShopPowerGrants(
  shopId: number,
): Promise<AdminPowerGrantRecord[]> {
  const response = await request.get<unknown, { data: AdminPowerGrantRecord[] }>(
    POWER_GRANT_BASE_URL,
    {
      params: { shopId },
    },
  );
  return response.data;
}

export async function createShopPowerGrant(
  params: AdminPowerGrantCreateParam,
): Promise<AdminPowerGrantRecord> {
  const response = await request.post<unknown, { data: AdminPowerGrantRecord }>(
    POWER_GRANT_BASE_URL,
    params,
  );
  return response.data;
}
