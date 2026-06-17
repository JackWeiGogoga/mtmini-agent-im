/**
 * 用户管理相关类型定义
 */

/**
 * 用户实体
 */
export interface User {
  /** 用户ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 昵称 */
  nickname: string;
  /** 邮箱 */
  email?: string;
  /** 手机号 */
  phone?: string;
  /** 头像URL */
  avatar?: string;
  /** 状态：1-正常, 0-停用 */
  status: number;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 认证提供者类型
 */
export type AuthProviderType = "PASSWORD" | "SMS" | "GOOGLE" | "GITHUB";

/**
 * 用户视图对象（包含角色信息）
 */
export interface UserVO extends User {
  /** 角色ID列表 */
  roleIds: string[];
  /** 角色名称列表 */
  roleNames: string[];
  /** 认证提供者（用户来源） */
  authProvider?: AuthProviderType;
}

/**
 * 用户查询参数
 */
export interface UserQueryParam {
  /** 用户名（模糊查询） */
  username?: string;
  /** 昵称（模糊查询） */
  nickname?: string;
  /** 状态 */
  status?: number;
  /** 页码 */
  pageNum?: number;
  /** 每页数量 */
  pageSize?: number;
}

/**
 * 创建用户参数
 */
export interface UserCreateParam {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
  /** 昵称 */
  nickname: string;
  /** 邮箱 */
  email?: string;
  /** 手机号 */
  phone?: string;
  /** 头像URL */
  avatar?: string;
  /** 状态：1-正常, 0-停用 */
  status?: number;
  /** 角色ID列表 */
  roleIds?: string[];
}

/**
 * 更新用户参数
 */
export interface UserUpdateParam {
  /** 用户ID */
  userId: string;
  /** 昵称 */
  nickname?: string;
  /** 邮箱 */
  email?: string;
  /** 手机号 */
  phone?: string;
  /** 头像URL */
  avatar?: string;
  /** 状态：1-正常, 0-停用 */
  status?: number;
  /** 角色ID列表 */
  roleIds?: string[];
}

/**
 * 分配角色参数
 */
export interface AssignRolesParam {
  /** 用户ID */
  userId: string;
  /** 角色ID列表 */
  roleIds: string[];
}

/**
 * 重置密码参数
 */
export interface ResetPasswordParam {
  /** 用户ID */
  userId: string;
  /** 新密码 */
  newPassword: string;
}
