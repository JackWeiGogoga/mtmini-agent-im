/**
 * 角色管理 API 服务
 */
import request from "@/utils/request";
import type {
  Role,
  RoleVO,
  RoleQueryParam,
  RoleCreateParam,
  RoleUpdateParam,
  AssignMenusParam,
  PageResult,
} from "@/types";

const BASE_URL = "/system/roles";

/**
 * 获取角色列表（分页）
 * @param params 查询参数
 * @returns 分页角色列表
 */
export async function getRoleList(
  params?: RoleQueryParam,
): Promise<PageResult<RoleVO>> {
  const response = await request.get<unknown, { data: PageResult<RoleVO> }>(
    BASE_URL,
    {
      params,
    },
  );
  return response.data;
}

/**
 * 获取所有角色（用于下拉选择）
 * @returns 角色列表
 */
export async function getAllRoles(): Promise<Role[]> {
  const response = await request.get<unknown, { data: Role[] }>(
    `${BASE_URL}/all`,
  );
  return response.data;
}

/**
 * 获取角色详情
 * @param roleId 角色ID
 * @returns 角色详情
 */
export async function getRoleDetail(roleId: string): Promise<RoleVO> {
  const response = await request.get<unknown, { data: RoleVO }>(
    `${BASE_URL}/${roleId}`,
  );
  return response.data;
}

/**
 * 创建角色
 * @param params 创建参数
 * @returns 新角色ID
 */
export async function createRole(params: RoleCreateParam): Promise<string> {
  const response = await request.post<unknown, { data: string }>(
    BASE_URL,
    params,
  );
  return response.data;
}

/**
 * 更新角色
 * @param params 更新参数
 */
export async function updateRole(params: RoleUpdateParam): Promise<void> {
  await request.put(BASE_URL, params);
}

/**
 * 删除角色
 * @param roleId 角色ID
 */
export async function deleteRole(roleId: string): Promise<void> {
  await request.delete(`${BASE_URL}/${roleId}`);
}

/**
 * 分配菜单权限
 * @param params 分配参数
 */
export async function assignMenus(params: AssignMenusParam): Promise<void> {
  await request.post(`${BASE_URL}/assign-menus`, params);
}

/**
 * 更新角色状态
 * @param roleId 角色ID
 * @param status 状态值
 */
export async function updateRoleStatus(
  roleId: string,
  status: number,
): Promise<void> {
  await request.patch(`${BASE_URL}/${roleId}/status`, { status });
}
