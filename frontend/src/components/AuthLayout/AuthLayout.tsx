/**
 * 认证布局组件
 * 处理需要登录的页面布局
 */
import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "@/contexts/AuthContext";

/**
 * 认证布局属性
 */
interface AuthLayoutProps {
  /** 子元素 */
  children: React.ReactNode;
  /** 需要的权限（可选） */
  permission?: string;
  /** 满足任一权限即可 */
  anyPermissions?: string[];
  /** 无权限时的跳转路径 */
  noPermissionRedirect?: string;
}

/**
 * 认证布局组件
 * 如果用户未登录，重定向到登录页
 * 如果用户已登录但无权限，显示无权限提示或重定向
 */
export function AuthLayout({
  children,
  permission,
  anyPermissions,
  noPermissionRedirect = "/403",
}: AuthLayoutProps) {
  const { isAuthenticated, loading, hasPermission, hasAnyPermission } =
    useAuth();
  const location = useLocation();

  // 加载中
  if (loading) {
    return null;
  }

  // 未登录，重定向到登录页
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // 检查权限
  if (permission && !hasPermission(permission)) {
    return <Navigate to={noPermissionRedirect} replace />;
  }
  if (anyPermissions && anyPermissions.length > 0 && !hasAnyPermission(anyPermissions)) {
    return <Navigate to={noPermissionRedirect} replace />;
  }

  return <>{children}</>;
}

/**
 * 公开布局组件
 * 用于不需要登录的页面（如登录页）
 * 如果用户已登录，重定向到首页
 */
interface PublicLayoutProps {
  /** 子元素 */
  children: React.ReactNode;
  /** 已登录时的跳转路径 */
  redirectTo?: string;
}

export function PublicLayout({
  children,
  redirectTo = "/",
}: PublicLayoutProps) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  // 加载中
  if (loading) {
    return null;
  }

  // 已登录，重定向到来源页面或首页
  if (isAuthenticated) {
    const from =
      (location.state as { from?: Location })?.from?.pathname || redirectTo;
    return <Navigate to={from} replace />;
  }

  return <>{children}</>;
}

export default AuthLayout;
