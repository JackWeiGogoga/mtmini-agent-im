/**
 * 权限控制 Hook
 */
import { useMemo } from "react";
import { useAuth } from "@/contexts/AuthContext";

/**
 * 权限 Hook 返回值
 */
interface UsePermissionReturn {
  /** 检查是否有指定权限 */
  hasPermission: (permission: string) => boolean;
  /** 检查是否有任一权限 */
  hasAnyPermission: (permissions: string[]) => boolean;
  /** 检查是否有所有权限 */
  hasAllPermissions: (permissions: string[]) => boolean;
  /** 检查是否有指定角色 */
  hasRole: (role: string) => boolean;
  /** 检查是否有任一角色 */
  hasAnyRole: (roles: string[]) => boolean;
  /** 是否是超级管理员 */
  isSuperAdmin: boolean;
  /** 当前用户的所有权限 */
  permissions: string[];
  /** 当前用户的所有角色 */
  roles: string[];
}

/**
 * 权限控制 Hook
 * @returns 权限检查方法和状态
 */
export function usePermission(): UsePermissionReturn {
  const {
    currentUser,
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
  } = useAuth();

  const permissions = useMemo(
    () => currentUser?.permissions ?? [],
    [currentUser],
  );
  const roles = useMemo(() => currentUser?.roles ?? [], [currentUser]);

  const isSuperAdmin = useMemo(() => {
    return permissions.includes("*:*:*") || roles.includes("super_admin");
  }, [permissions, roles]);

  return {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
    hasAnyRole,
    isSuperAdmin,
    permissions,
    roles,
  };
}

/**
 * 检查单个权限的 Hook
 * @param permission 权限标识
 * @returns 是否有权限
 */
export function useHasPermission(permission: string): boolean {
  const { hasPermission } = usePermission();
  return useMemo(() => hasPermission(permission), [hasPermission, permission]);
}

/**
 * 检查多个权限的 Hook（满足任一）
 * @param permissions 权限标识数组
 * @returns 是否有权限
 */
export function useHasAnyPermission(permissions: string[]): boolean {
  const { hasAnyPermission } = usePermission();
  return useMemo(
    () => hasAnyPermission(permissions),
    [hasAnyPermission, permissions],
  );
}

/**
 * 检查多个权限的 Hook（满足全部）
 * @param permissions 权限标识数组
 * @returns 是否有权限
 */
export function useHasAllPermissions(permissions: string[]): boolean {
  const { hasAllPermissions } = usePermission();
  return useMemo(
    () => hasAllPermissions(permissions),
    [hasAllPermissions, permissions],
  );
}

export default usePermission;
