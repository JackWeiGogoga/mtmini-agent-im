/**
 * 用户管理 API 服务
 */
import request from "@/utils/request";
import type {
  UserVO,
  UserQueryParam,
  UserCreateParam,
  UserUpdateParam,
  AssignRolesParam,
  ResetPasswordParam,
  PageResult,
} from "@/types";

const BASE_URL = "/system/users";

/**
 * 获取用户列表（分页）
 * @param params 查询参数
 * @returns 分页用户列表
 */
export async function getUserList(
  params?: UserQueryParam,
): Promise<PageResult<UserVO>> {
  const response = await request.get<unknown, { data: PageResult<UserVO> }>(
    BASE_URL,
    {
      params,
    },
  );
  return response.data;
}

/**
 * 获取用户详情
 * @param userId 用户ID
 * @returns 用户详情
 */
export async function getUserDetail(userId: string): Promise<UserVO> {
  const response = await request.get<unknown, { data: UserVO }>(
    `${BASE_URL}/${userId}`,
  );
  return response.data;
}

/**
 * 创建用户
 * @param params 创建参数
 * @returns 新用户ID
 */
export async function createUser(params: UserCreateParam): Promise<string> {
  const response = await request.post<unknown, { data: string }>(
    BASE_URL,
    params,
  );
  return response.data;
}

/**
 * 更新用户
 * @param params 更新参数
 */
export async function updateUser(params: UserUpdateParam): Promise<void> {
  await request.put(BASE_URL, params);
}

/**
 * 删除用户
 * @param userId 用户ID
 */
export async function deleteUser(userId: string): Promise<void> {
  await request.delete(`${BASE_URL}/${userId}`);
}

/**
 * 分配角色
 * @param params 分配参数
 */
export async function assignRoles(params: AssignRolesParam): Promise<void> {
  await request.post(`${BASE_URL}/assign-roles`, params);
}

/**
 * 重置密码
 * @param params 重置密码参数
 */
export async function resetPassword(params: ResetPasswordParam): Promise<void> {
  await request.post(`${BASE_URL}/reset-password`, params);
}

/**
 * 更新用户状态
 * @param userId 用户ID
 * @param status 状态值
 */
export async function updateUserStatus(
  userId: string,
  status: number,
): Promise<void> {
  await request.patch(`${BASE_URL}/${userId}/status`, { status });
}
