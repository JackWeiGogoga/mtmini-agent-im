/**
 * 认证相关类型定义
 */

/**
 * 认证提供者类型
 */
export type AuthProvider = "PASSWORD" | "SMS" | "GOOGLE" | "GITHUB";

/**
 * 后台工作台类型
 */
export type BackendPortal = "ADMIN" | "ORDINARY" | "SALES" | "YOUZAN";

/**
 * 邮箱密码登录参数
 */
export interface PasswordLoginParam {
  /** 邮箱 */
  email: string;
  /** 密码 */
  password: string;
  /** 记住我（30天会话） */
  rememberMe?: boolean;
  /** 指定后台工作台 */
  portal?: BackendPortal;
}

/**
 * 短信验证码登录参数
 */
export interface SmsLoginParam {
  /** 手机号 */
  phone: string;
  /** 验证码 */
  code: string;
  /** 记住我（30天会话） */
  rememberMe?: boolean;
  /** 指定后台工作台 */
  portal?: BackendPortal;
}

/**
 * 发送短信验证码参数
 */
export interface SmsSendCodeParam {
  /** 手机号 */
  phone: string;
}

/**
 * 用户注册参数
 */
export interface RegisterParam {
  /** 邮箱 */
  email: string;
  /** 密码 */
  password: string;
  /** 昵称（可选） */
  nickname?: string;
}

/**
 * OAuth 回调参数
 */
export interface OAuthCallbackParam {
  /** 授权码 */
  code: string;
  /** 重定向 URI */
  redirectUri?: string;
  /** 记住我 */
  rememberMe?: boolean;
}

/**
 * 认证响应结果
 */
export interface AuthResult {
  /** 会话 Token */
  token: string;
  /** 过期时间（秒） */
  expiresIn: number;
  /** 是否新用户 */
  isNewUser: boolean;
  /** 认证提供者 */
  authProvider: AuthProvider;
  /** 用户信息 */
  user: {
    userId: string;
    username: string;
    nickname: string;
    email?: string;
    avatar?: string;
  };
}

/**
 * OAuth 配置信息
 */
export interface OAuthConfig {
  google: {
    clientId: string;
    authorizationUri: string;
    redirectUri: string;
    scope: string;
  };
  github: {
    clientId: string;
    authorizationUri: string;
    redirectUri: string;
    scope: string;
  };
}

/**
 * 当前登录用户信息
 */
export interface CurrentUser {
  /** 用户ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 昵称 */
  nickname: string;
  /** 邮箱 */
  email?: string;
  /** 头像URL */
  avatar?: string;
  /** 认证提供者 */
  authProvider?: AuthProvider;
  /** 角色列表 */
  roles: string[];
  /** 权限列表 */
  permissions: string[];
  /** 菜单列表 */
  menus: MenuTreeNode[];
  /** 当前后台工作台 */
  activePortal?: BackendPortal;
  /** 当前账号可用的后台工作台 */
  availablePortals?: BackendPortal[];
  /** 是否处于管理员模拟登录 */
  impersonated?: boolean;
  /** 发起模拟登录的管理员用户 ID */
  impersonatorUserId?: string;
}

/**
 * 菜单树节点
 * 注意：此类型需与 menu-types.ts 中的 MenuTreeNode 保持兼容
 */
export interface MenuTreeNode {
  /** 菜单ID */
  menuId: string;
  /** 菜单名称 */
  menuName: string;
  /** 父菜单ID */
  parentId?: string;
  /** 菜单类型：M-目录, C-菜单, F-按钮 */
  menuType: "M" | "C" | "F" | string;
  /** 路由地址 */
  path?: string;
  /** 组件路径 */
  component?: string;
  /** 权限标识 */
  permission?: string;
  /** 菜单图标 */
  icon?: string;
  /** 排序 */
  orderNum: number;
  /** 是否可见：1-可见, 0-隐藏 */
  visible: number;
  /** 状态：1-正常, 0-停用（菜单管理需要） */
  status?: number;
  /** 创建时间（菜单管理需要） */
  createTime?: string;
  /** 更新时间（菜单管理需要） */
  updateTime?: string;
  /** 是否选中（用于角色权限分配） */
  checked?: boolean;
  /** 子菜单 */
  children?: MenuTreeNode[];
}

/**
 * 认证状态
 */
export interface AuthState {
  /** 是否已登录 */
  isAuthenticated: boolean;
  /** 当前用户信息 */
  currentUser: CurrentUser | null;
  /** 是否正在加载 */
  loading: boolean;
}
