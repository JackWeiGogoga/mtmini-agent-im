/**
 * 权限控制组件
 * 根据权限控制子元素的显示
 */
import React from "react";
import { usePermission } from "@/hooks/usePermission";

/**
 * 权限控制组件属性
 */
interface PermissionControlProps {
  /** 子元素 */
  children: React.ReactNode;
  /** 需要的权限（单个） */
  permission?: string;
  /** 需要的权限（满足任一） */
  anyPermissions?: string[];
  /** 需要的权限（满足全部） */
  allPermissions?: string[];
  /** 需要的角色（单个） */
  role?: string;
  /** 需要的角色（满足任一） */
  anyRoles?: string[];
  /** 无权限时显示的内容 */
  fallback?: React.ReactNode;
}

/**
 * 权限控制组件
 * @example
 * // 单个权限控制
 * <PermissionControl permission="system:user:add">
 *   <Button>添加用户</Button>
 * </PermissionControl>
 *
 * // 任一权限
 * <PermissionControl anyPermissions={['system:user:edit', 'system:user:delete']}>
 *   <Button>编辑或删除</Button>
 * </PermissionControl>
 *
 * // 角色控制
 * <PermissionControl role="admin">
 *   <AdminPanel />
 * </PermissionControl>
 */
export function PermissionControl({
  children,
  permission,
  anyPermissions,
  allPermissions,
  role,
  anyRoles,
  fallback = null,
}: PermissionControlProps) {
  const {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
  } = usePermission();

  // 检查权限
  let hasAccess = true;

  if (permission) {
    hasAccess = hasAccess && hasPermission(permission);
  }

  if (anyPermissions && anyPermissions.length > 0) {
    hasAccess = hasAccess && hasAnyPermission(anyPermissions);
  }

  if (allPermissions && allPermissions.length > 0) {
    hasAccess = hasAccess && hasAllPermissions(allPermissions);
  }

  if (role) {
    hasAccess = hasAccess && hasRole(role);
  }

  if (anyRoles && anyRoles.length > 0) {
    hasAccess = hasAccess && hasAnyRole(anyRoles);
  }

  if (hasAccess) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
}

/**
 * 高阶组件：为组件添加权限控制
 * @param permission 权限标识
 * @returns 高阶组件
 */
export function withPermission<P extends object>(permission: string) {
  return function (WrappedComponent: React.ComponentType<P>) {
    return function PermissionWrapper(props: P) {
      return (
        <PermissionControl permission={permission}>
          <WrappedComponent {...props} />
        </PermissionControl>
      );
    };
  };
}

/**
 * 高阶组件：为组件添加任一权限控制
 * @param permissions 权限标识数组
 * @returns 高阶组件
 */
export function withAnyPermission<P extends object>(permissions: string[]) {
  return function (WrappedComponent: React.ComponentType<P>) {
    return function PermissionWrapper(props: P) {
      return (
        <PermissionControl anyPermissions={permissions}>
          <WrappedComponent {...props} />
        </PermissionControl>
      );
    };
  };
}

/**
 * 高阶组件：为组件添加角色控制
 * @param role 角色标识
 * @returns 高阶组件
 */
export function withRole<P extends object>(role: string) {
  return function (WrappedComponent: React.ComponentType<P>) {
    return function RoleWrapper(props: P) {
      return (
        <PermissionControl role={role}>
          <WrappedComponent {...props} />
        </PermissionControl>
      );
    };
  };
}

export default PermissionControl;
