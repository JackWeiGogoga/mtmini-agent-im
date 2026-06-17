/**
 * 菜单管理 API 服务
 */
import request from "@/utils/request";
import type { MenuTreeNode, MenuCreateParam, MenuUpdateParam } from "@/types";

const BASE_URL = "/system/menus";

/**
 * 获取菜单树
 * @returns 菜单树结构
 */
export async function getMenuTree(): Promise<MenuTreeNode[]> {
  const response = await request.get<unknown, { data: MenuTreeNode[] }>(
    `${BASE_URL}/tree`,
  );
  return response.data;
}

/**
 * 获取所有菜单（平铺，用于权限分配时查找权限）
 * @returns 菜单列表
 */
export async function getAllMenus(): Promise<MenuTreeNode[]> {
  const response = await request.get<unknown, { data: MenuTreeNode[] }>(
    `${BASE_URL}/all`,
  );
  return response.data;
}

/**
 * 获取角色的菜单树（包含选中状态）
 * @param roleId 角色ID
 * @returns 菜单树结构（带选中标记）
 */
export async function getMenuTreeForRole(
  roleId: string,
): Promise<MenuTreeNode[]> {
  const response = await request.get<unknown, { data: MenuTreeNode[] }>(
    `${BASE_URL}/role-tree`,
    { params: { roleId } },
  );
  return response.data;
}

/**
 * 获取菜单详情
 * @param menuId 菜单ID
 * @returns 菜单详情
 */
export async function getMenuDetail(menuId: string): Promise<MenuTreeNode> {
  const response = await request.get<unknown, { data: MenuTreeNode }>(
    `${BASE_URL}/${menuId}`,
  );
  return response.data;
}

/**
 * 创建菜单
 * @param params 创建参数
 * @returns 新菜单ID
 */
export async function createMenu(params: MenuCreateParam): Promise<string> {
  const response = await request.post<unknown, { data: string }>(
    BASE_URL,
    params,
  );
  return response.data;
}

/**
 * 更新菜单
 * @param params 更新参数
 */
export async function updateMenu(params: MenuUpdateParam): Promise<void> {
  await request.put(BASE_URL, params);
}

/**
 * 删除菜单
 * @param menuId 菜单ID
 */
export async function deleteMenu(menuId: string): Promise<void> {
  await request.delete(`${BASE_URL}/${menuId}`);
}

/**
 * 更新菜单状态
 * @param menuId 菜单ID
 * @param status 状态值
 */
export async function updateMenuStatus(
  menuId: string,
  status: number,
): Promise<void> {
  await request.patch(`${BASE_URL}/${menuId}/status`, { status });
}

/**
 * 更新菜单可见性
 * @param menuId 菜单ID
 * @param visible 可见性
 */
export async function updateMenuVisible(
  menuId: string,
  visible: number,
): Promise<void> {
  await request.patch(`${BASE_URL}/${menuId}/visible`, { visible });
}
