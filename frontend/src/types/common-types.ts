/**
 * 通用类型定义
 */

/**
 * API 响应结构
 */
export interface ApiResponse<T = unknown> {
  /** 状态码 */
  code: number;
  /** 消息 */
  msg: string;
  /** 数据 */
  data: T;
}

/**
 * 分页参数
 */
export interface PageParam {
  /** 页码 */
  pageNum?: number;
  /** 每页数量 */
  pageSize?: number;
}

/**
 * 分页结果
 */
export interface PageResult<T> {
  /** 数据列表 */
  list: T[];
  /** 总数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页数量 */
  pageSize: number;
  /** 总页数 */
  pages: number;
}

/**
 * 通用选项类型
 */
export interface Option<T = string> {
  /** 值 */
  value: T;
  /** 标签 */
  label: string;
  /** 是否禁用 */
  disabled?: boolean;
}

/**
 * 树形数据节点
 */
export interface TreeNode<T = unknown> {
  /** 节点数据 */
  data: T;
  /** 子节点 */
  children?: TreeNode<T>[];
}

/**
 * 状态常量
 */
export const Status = {
  /** 正常 */
  ENABLED: 1,
  /** 停用 */
  DISABLED: 0,
} as const;

export type StatusType = (typeof Status)[keyof typeof Status];

/**
 * 状态标签映射
 */
export const StatusLabels: Record<StatusType, string> = {
  [Status.ENABLED]: "正常",
  [Status.DISABLED]: "停用",
};

/**
 * 可见性常量
 */
export const Visible = {
  /** 可见 */
  SHOW: 1,
  /** 隐藏 */
  HIDE: 0,
} as const;

export type VisibleType = (typeof Visible)[keyof typeof Visible];

/**
 * 可见性标签映射
 */
export const VisibleLabels: Record<VisibleType, string> = {
  [Visible.SHOW]: "显示",
  [Visible.HIDE]: "隐藏",
};
