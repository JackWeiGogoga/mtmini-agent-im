/**
 * 菜单管理相关类型定义
 */
import type { MenuTreeNode as BaseMenuTreeNode } from "./auth-types";

/**
 * 菜单类型常量
 */
export const MenuType = {
  /** 目录 */
  DIRECTORY: "M",
  /** 菜单 */
  MENU: "C",
  /** 按钮 */
  BUTTON: "F",
} as const;

export type MenuTypeValue = (typeof MenuType)[keyof typeof MenuType];

/**
 * 菜单类型标签映射
 */
export const MenuTypeLabels: Record<MenuTypeValue, string> = {
  [MenuType.DIRECTORY]: "目录",
  [MenuType.MENU]: "菜单",
  [MenuType.BUTTON]: "按钮",
};

/**
 * 菜单实体
 */
export interface Menu {
  /** 菜单ID */
  menuId: string;
  /** 菜单名称 */
  menuName: string;
  /** 父菜单ID */
  parentId?: string;
  /** 菜单类型：M-目录, C-菜单, F-按钮 */
  menuType: MenuTypeValue | string;
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
  /** 状态：1-正常, 0-停用 */
  status?: number;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 菜单树节点 - 重新导出 auth-types 中的定义
 */
export type MenuTreeNode = BaseMenuTreeNode;

/**
 * 创建菜单参数
 */
export interface MenuCreateParam {
  /** 菜单名称 */
  menuName: string;
  /** 父菜单ID */
  parentId?: string;
  /** 菜单类型：M-目录, C-菜单, F-按钮 */
  menuType: MenuTypeValue | string;
  /** 路由地址 */
  path?: string;
  /** 组件路径 */
  component?: string;
  /** 权限标识（后端字段名为 perms） */
  perms?: string;
  /** 菜单图标 */
  icon?: string;
  /** 排序 */
  orderNum?: number;
  /** 是否可见：1-可见, 0-隐藏 */
  visible?: number;
  /** 状态：1-正常, 0-停用 */
  status?: number;
}

/**
 * 更新菜单参数
 */
export interface MenuUpdateParam {
  /** 菜单ID */
  menuId: string;
  /** 菜单名称 */
  menuName?: string;
  /** 父菜单ID */
  parentId?: string;
  /** 菜单类型：M-目录, C-菜单, F-按钮 */
  menuType?: MenuTypeValue | string;
  /** 路由地址 */
  path?: string;
  /** 组件路径 */
  component?: string;
  /** 权限标识（后端字段名为 perms） */
  perms?: string;
  /** 菜单图标 */
  icon?: string;
  /** 排序 */
  orderNum?: number;
  /** 是否可见：1-可见, 0-隐藏 */
  visible?: number;
  /** 状态：1-正常, 0-停用 */
  status?: number;
}

/**
 * 菜单选项（用于树形选择）
 */
export interface MenuOption {
  /** 菜单ID */
  value: string;
  /** 菜单名称 */
  title: string;
  /** 子菜单 */
  children?: MenuOption[];
  /** 是否可选 */
  selectable?: boolean;
  /** 是否禁用 */
  disabled?: boolean;
}
