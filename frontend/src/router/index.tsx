/**
 * 路由配置
 */
import React, { lazy, Suspense } from "react";
import { createBrowserRouter, Navigate, Outlet } from "react-router-dom";
import AppLoading from "@/components/AppLoading/AppLoading";
import { MainLayout } from "@/layouts";
import { AuthLayout, PublicLayout } from "@/components/AuthLayout";
import { BASE_PATH } from "@/utils/env";

// 懒加载页面组件
const Login = lazy(() => import("@/pages/login"));
const Register = lazy(() => import("@/pages/register"));
const OAuthCallback = lazy(() => import("@/pages/oauth"));
const MasterConsole = lazy(() => import("@/pages/master-console"));
const MtAppOrderManage = lazy(() => import("@/pages/mt-app-order-manage"));
const YouzanAppOrderManage = lazy(
  () => import("@/pages/youzan-app-order-manage"),
);
const ShopManage = lazy(() => import("@/pages/shop-manage"));
const PowerManage = lazy(() => import("@/pages/power-manage"));
const PowerRechargeOrderManage = lazy(
  () => import("@/pages/power-recharge-order-manage"),
);
const UserBehaviorAnalytics = lazy(
  () => import("@/pages/user-behavior-analytics"),
);
const MerchantKnowledgeManage = lazy(
  () => import("@/pages/merchant-knowledge"),
);
const SalesConfig = lazy(() => import("@/pages/sales-config"));
const SalesCommissionManage = lazy(
  () => import("@/pages/sales-commission-manage"),
);
const SalesRedemptionCodeManage = lazy(
  () => import("@/pages/sales-redemption-code-manage"),
);
const UserManage = lazy(() => import("@/pages/user-manage"));
const RoleManage = lazy(() => import("@/pages/role-manage"));
const MenuManage = lazy(() => import("@/pages/menu-manage"));
const SystemPromptManage = lazy(() => import("@/pages/system-prompt"));
const ImAutoReplyManage = lazy(() => import("@/pages/im-auto-replies"));
const ImBroadcastManage = lazy(() => import("@/pages/im-broadcasts"));
const Forbidden = lazy(() => import("@/pages/error/Forbidden"));

/**
 * 加载中组件
 */
const LoadingFallback = () => <AppLoading text="页面加载中..." fullPage />;

/**
 * 懒加载包装器
 */
const LazyWrapper = ({ children }: { children: React.ReactNode }) => (
  <Suspense fallback={<LoadingFallback />}>{children}</Suspense>
);

/**
 * 受保护的布局（需要登录）
 */
const ProtectedLayout = () => (
  <AuthLayout>
    <MainLayout>
      <Outlet />
    </MainLayout>
  </AuthLayout>
);

/**
 * 公开布局（不需要登录）
 */
const PublicLayoutWrapper = () => (
  <PublicLayout>
    <Outlet />
  </PublicLayout>
);

/**
 * 路由配置
 */
const router = createBrowserRouter(
  [
    // 公开路由
    {
      element: <PublicLayoutWrapper />,
      children: [
        {
          path: "/login",
          element: (
            <LazyWrapper>
              <Login />
            </LazyWrapper>
          ),
        },
        {
          path: "/register",
          element: (
            <LazyWrapper>
              <Register />
            </LazyWrapper>
          ),
        },
        {
          path: "/oauth/callback/:provider",
          element: (
            <LazyWrapper>
              <OAuthCallback />
            </LazyWrapper>
          ),
        },
      ],
    },
    // 受保护路由
    {
      element: <ProtectedLayout />,
      children: [
        {
          path: "/",
          element: <Navigate to="/master-console" replace />,
        },
        {
          path: "/welcome",
          element: <Navigate to="/master-console" replace />,
        },
        {
          path: "/master-console",
          element: (
            <AuthLayout
              anyPermissions={[
                "shop:master-console:list",
                "sales:master-console:list",
              ]}
            >
              <LazyWrapper>
                <MasterConsole />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/mt-app-orders",
          element: (
            <AuthLayout permission="shop:mt-app-order:list">
              <LazyWrapper>
                <MtAppOrderManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/youzan-app-orders",
          element: (
            <AuthLayout permission="youzan:app-order:list">
              <LazyWrapper>
                <YouzanAppOrderManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/shops",
          element: (
            <AuthLayout
              anyPermissions={[
                "shop:binding:list",
                "sales:binding:list",
                "youzan:binding:list",
              ]}
            >
              <LazyWrapper>
                <ShopManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/power-management",
          element: (
            <AuthLayout permission="shop:power-management:list">
              <LazyWrapper>
                <PowerManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/power-recharge-orders",
          element: (
            <AuthLayout permission="system:power-recharge-order:list">
              <LazyWrapper>
                <PowerRechargeOrderManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/analytics/user-behavior",
          element: (
            <AuthLayout permission="analytics:user-behavior:list">
              <LazyWrapper>
                <UserBehaviorAnalytics />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/merchant-knowledge",
          element: (
            <AuthLayout permission="knowledge:list">
              <LazyWrapper>
                <MerchantKnowledgeManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/sales/config",
          element: (
            <AuthLayout permission="sales:self-config:view">
              <LazyWrapper>
                <SalesConfig />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/sales/commissions",
          element: (
            <AuthLayout permission="sales:commission:list">
              <LazyWrapper>
                <SalesCommissionManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/sales/redemption-codes",
          element: (
            <AuthLayout permission="sales:redemption-code:list">
              <LazyWrapper>
                <SalesRedemptionCodeManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        // 系统管理
        {
          path: "/im-auto-replies",
          element: (
            <AuthLayout permission="system:im-auto-reply:list">
              <LazyWrapper>
                <ImAutoReplyManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/im-broadcasts",
          element: (
            <AuthLayout permission="system:im-broadcast:list">
              <LazyWrapper>
                <ImBroadcastManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/system/user",
          element: (
            <AuthLayout permission="system:user:list">
              <LazyWrapper>
                <UserManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/system/role",
          element: (
            <AuthLayout permission="system:role:list">
              <LazyWrapper>
                <RoleManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/system/menu",
          element: (
            <AuthLayout permission="system:menu:list">
              <LazyWrapper>
                <MenuManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
        {
          path: "/system/prompts",
          element: (
            <AuthLayout permission="system:prompt:list">
              <LazyWrapper>
                <SystemPromptManage />
              </LazyWrapper>
            </AuthLayout>
          ),
        },
      ],
    },
    // 错误页面
    {
      path: "/403",
      element: (
        <LazyWrapper>
          <Forbidden />
        </LazyWrapper>
      ),
    },
    // 404 重定向
    {
      path: "*",
      element: <Navigate to="/" replace />,
    },
  ],
  {
    basename: BASE_PATH,
  },
);

export default router;
