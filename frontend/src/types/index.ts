/**
 * 类型定义统一导出
 */

// 通用类型
export * from "./common-types";

// 认证相关类型
export * from "./auth-types";

// 用户管理类型
export * from "./user-types";

// 角色管理类型
export * from "./role-types";

// 门店管理类型
export * from "./shop-manage-types";

// 总控台类型
export * from "./master-console-types";

// 网销兑换码与提成类型
export * from "./sales-types";

// 算力管理类型
export * from "./power-manage-types";

// 充值订单管理类型
export * from "./power-recharge-order-types";

// 美团订单类型
export * from "./mt-app-order-types";

// 有赞订单类型
export * from "./youzan-app-order-types";

// 用户行为统计类型
export * from "./user-behavior-analytics-types";

// 评论回复知识库类型
export * from "./comment-reply-knowledge-types";

// 系统提示词类型
export * from "./system-prompt-types";

// 菜单管理类型
export type {
  Menu,
  MenuCreateParam,
  MenuUpdateParam,
  MenuOption,
  MenuTreeNode,
} from "./menu-types";
export { MenuType, MenuTypeLabels } from "./menu-types";
export type { MenuTypeValue } from "./menu-types";

// 通用响应类型
export interface ResultVO<T = unknown> {
  code: number;
  msg: string;
  data: T;
  success: boolean;
}

// 分页响应类型
export interface PageInfo<T> {
  total: number;
  list: T[];
  pageNum: number;
  pageSize: number;
  pages: number;
}

// 业务类型
export interface BusinessType {
  code: string;
  name: string;
}
