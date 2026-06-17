/**
 * 角色管理相关类型定义
 */

/**
 * 角色实体
 */
export interface Role {
  /** 角色ID */
  roleId: string;
  /** 角色名称 */
  roleName: string;
  /** 角色编码 */
  roleCode: string;
  /** 角色描述 */
  description?: string;
  /** 排序 */
  orderNum: number;
  /** 状态：1-正常, 0-停用 */
  status: number;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 角色视图对象（包含菜单信息）
 */
export interface RoleVO extends Role {
  /** 菜单ID列表 */
  menuIds: string[];
}

/**
 * 角色查询参数
 */
export interface RoleQueryParam {
  /** 角色名称（模糊查询） */
  roleName?: string;
  /** 角色编码（模糊查询） */
  roleCode?: string;
  /** 状态 */
  status?: number;
  /** 页码 */
  pageNum?: number;
  /** 每页数量 */
  pageSize?: number;
}

/**
 * 创建角色参数
 */
export interface RoleCreateParam {
  /** 角色名称 */
  roleName: string;
  /** 角色编码 */
  roleCode: string;
  /** 角色描述 */
  description?: string;
  /** 排序 */
  orderNum?: number;
  /** 状态：1-正常, 0-停用 */
  status?: number;
  /** 菜单ID列表 */
  menuIds?: string[];
}

/**
 * 更新角色参数
 */
export interface RoleUpdateParam {
  /** 角色ID */
  roleId: string;
  /** 角色名称 */
  roleName?: string;
  /** 角色描述 */
  description?: string;
  /** 排序 */
  orderNum?: number;
  /** 状态：1-正常, 0-停用 */
  status?: number;
  /** 菜单ID列表 */
  menuIds?: string[];
}

/**
 * 分配菜单参数
 */
export interface AssignMenusParam {
  /** 角色ID */
  roleId: string;
  /** 菜单ID列表 */
  menuIds: string[];
}

/**
 * 角色选项（用于下拉框）
 */
export interface RoleOption {
  /** 角色ID */
  value: string;
  /** 角色名称 */
  label: string;
}
