/**
 * 认证上下文
 * 管理用户登录状态、用户信息和权限
 */
import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  useMemo,
} from "react";
import type {
  CurrentUser,
  AuthState,
  MenuTreeNode,
  BackendPortal,
} from "@/types";
import {
  getCurrentUser,
  logout as logoutApi,
  switchPortal as switchPortalApi,
} from "@/services/auth-api";
import { getFullPath } from "@/utils/env";

/**
 * 认证上下文值类型
 */
interface AuthContextValue extends AuthState {
  /** 登录成功后刷新用户信息 */
  refreshUser: () => Promise<void>;
  /** 登出 */
  logout: () => Promise<void>;
  /** 切换后台工作台 */
  switchPortal: (portal: BackendPortal) => Promise<void>;
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
}

/**
 * 认证上下文
 */
const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 认证上下文 Provider 属性
 */
interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * 认证上下文 Provider
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    currentUser: null,
    loading: true,
  });

  /**
   * 刷新用户信息
   */
  const refreshUser = useCallback(async () => {
    try {
      setState((prev) => ({ ...prev, loading: true }));
      const user = await getCurrentUser();
      setState({
        isAuthenticated: true,
        currentUser: user,
        loading: false,
      });
    } catch {
      setState({
        isAuthenticated: false,
        currentUser: null,
        loading: false,
      });
    }
  }, []);

  /**
   * 登出
   */
  const logout = useCallback(async () => {
    try {
      await logoutApi();
    } finally {
      setState({
        isAuthenticated: false,
        currentUser: null,
        loading: false,
      });
      // 跳转到登录页
      window.location.href = getFullPath("/login");
    }
  }, []);

  /**
   * 切换后台工作台
   */
  const switchPortal = useCallback(async (portal: BackendPortal) => {
    setState((prev) => ({ ...prev, loading: true }));
    try {
      const user = await switchPortalApi(portal);
      setState({
        isAuthenticated: true,
        currentUser: user,
        loading: false,
      });
    } catch (error) {
      setState((prev) => ({ ...prev, loading: false }));
      throw error;
    }
  }, []);

  /**
   * 检查是否有指定权限
   */
  const hasPermission = useCallback(
    (permission: string): boolean => {
      if (!state.currentUser) return false;
      // 超级管理员仅在总后台拥有全局权限；切换到工作台后按工作台过滤后的权限判断。
      if (
        state.currentUser.activePortal === "ADMIN" &&
        (state.currentUser.permissions.includes("*:*:*") ||
          state.currentUser.roles.includes("super_admin"))
      ) {
        return true;
      }
      return state.currentUser.permissions.includes(permission);
    },
    [state.currentUser],
  );

  /**
   * 检查是否有任一权限
   */
  const hasAnyPermission = useCallback(
    (permissions: string[]): boolean => {
      return permissions.some((p) => hasPermission(p));
    },
    [hasPermission],
  );

  /**
   * 检查是否有所有权限
   */
  const hasAllPermissions = useCallback(
    (permissions: string[]): boolean => {
      return permissions.every((p) => hasPermission(p));
    },
    [hasPermission],
  );

  /**
   * 检查是否有指定角色
   */
  const hasRole = useCallback(
    (role: string): boolean => {
      if (!state.currentUser) return false;
      if (state.currentUser.roles.includes("super_admin")) return true;
      return state.currentUser.roles.includes(role);
    },
    [state.currentUser],
  );

  /**
   * 检查是否有任一角色
   */
  const hasAnyRole = useCallback(
    (roles: string[]): boolean => {
      return roles.some((r) => hasRole(r));
    },
    [hasRole],
  );

  // 初始化时获取用户信息
  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...state,
      refreshUser,
      logout,
      switchPortal,
      hasPermission,
      hasAnyPermission,
      hasAllPermissions,
      hasRole,
      hasAnyRole,
    }),
    [
      state,
      refreshUser,
      logout,
      switchPortal,
      hasPermission,
      hasAnyPermission,
      hasAllPermissions,
      hasRole,
      hasAnyRole,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * 使用认证上下文 Hook
 */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

/**
 * 获取当前用户（便捷方法）
 */
export function useCurrentUser(): CurrentUser | null {
  const { currentUser } = useAuth();
  return currentUser;
}

/**
 * 获取用户菜单（便捷方法）
 */
export function useUserMenus(): MenuTreeNode[] {
  const { currentUser } = useAuth();
  return currentUser?.menus ?? [];
}

export default AuthContext;
