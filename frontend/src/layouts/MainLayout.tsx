/**
 * 主布局组件
 * 包含侧边栏、头部和内容区域
 */
import React, { useState, useMemo, useCallback } from "react";
import {
  Layout,
  Menu,
  Dropdown,
  Avatar,
  Space,
  Breadcrumb,
  Drawer,
  Typography,
} from "antd";
import { useNavigate, useLocation, Link } from "react-router-dom";
import logo from "@/assets/logo.jpg";
import {
  UserOutlined,
  HomeOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  AppstoreOutlined,
  LogoutOutlined,
  MenuOutlined,
  RobotOutlined,
  CommentOutlined,
  BulbOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  OpenAIOutlined,
  MessageOutlined,
  ShopOutlined,
  DashboardOutlined,
  ProfileOutlined,
  CreditCardOutlined,
  LineChartOutlined,
} from "@ant-design/icons";
import type { MenuProps } from "antd";
import { useAuth } from "@/contexts/AuthContext";
import type { BackendPortal, MenuTreeNode } from "@/types/auth-types";
import { useResponsive } from "@/hooks";

const { Sider, Content, Header } = Layout;
const { Text } = Typography;

type MenuItem = Required<MenuProps>["items"][number];

/**
 * 图标映射表
 */
const iconMap: Record<string, React.ReactNode> = {
  HomeOutlined: <HomeOutlined />,
  UserOutlined: <UserOutlined />,
  SettingOutlined: <SettingOutlined />,
  OpenAIOutlined: <OpenAIOutlined />,
  TeamOutlined: <TeamOutlined />,
  SafetyCertificateOutlined: <SafetyCertificateOutlined />,
  AppstoreOutlined: <AppstoreOutlined />,
  MenuOutlined: <MenuOutlined />,
  RobotOutlined: <RobotOutlined />,
  CommentOutlined: <CommentOutlined />,
  BulbOutlined: <BulbOutlined />,
  DatabaseOutlined: <DatabaseOutlined />,
  FileSearchOutlined: <FileSearchOutlined />,
  FileTextOutlined: <FileTextOutlined />,
  MessageOutlined: <MessageOutlined />,
  ShopOutlined: <ShopOutlined />,
  DashboardOutlined: <DashboardOutlined />,
  ProfileOutlined: <ProfileOutlined />,
  CreditCardOutlined: <CreditCardOutlined />,
  LineChartOutlined: <LineChartOutlined />,
};

/**
 * 获取图标组件
 */
const getIcon = (iconName?: string): React.ReactNode => {
  if (!iconName) return null;
  return iconMap[iconName] || null;
};

/**
 * 默认菜单项（当后端未返回菜单时使用）
 */
const defaultMenuItems: MenuItem[] = [
  // {
  //   key: "/avatar",
  //   icon: <RobotOutlined />,
  //   label: "虚拟人管理",
  // },
];

/**
 * 将后端菜单树转换为 Ant Design Menu 的 items 格式
 */
const convertMenuTreeToItems = (menus: MenuTreeNode[]): MenuItem[] => {
  const seenKeys = new Set<string>();
  return menus
    .filter((menu) => menu.menuType !== "F") // 过滤掉按钮类型
    .filter((menu) => {
      const key = menu.path || menu.menuId;
      if (seenKeys.has(key)) {
        return false;
      }
      seenKeys.add(key);
      return true;
    })
    .sort((a, b) => a.orderNum - b.orderNum)
    .map((menu): MenuItem => {
      // 检查是否有子菜单
      const hasChildren =
        menu.children &&
        menu.children.length > 0 &&
        menu.children.some((child) => child.menuType !== "F");

      if (hasChildren) {
        // 有子菜单，返回 SubMenuType
        return {
          key: menu.path || menu.menuId,
          icon: getIcon(menu.icon),
          label: menu.menuName,
          children: convertMenuTreeToItems(menu.children!),
        };
      }

      // 无子菜单，返回普通菜单项
      return {
        key: menu.path || menu.menuId,
        icon: getIcon(menu.icon),
        label: menu.menuName,
      };
    });
};

/**
 * 根据当前路径获取默认展开的菜单keys
 */
const getOpenKeys = (pathname: string): string[] => {
  const parts = pathname.split("/").filter(Boolean);
  const keys: string[] = [];
  let currentPath = "";
  parts.forEach((part, index) => {
    currentPath += "/" + part;
    if (index < parts.length - 1) {
      keys.push(currentPath);
    }
  });
  return keys;
};

/**
 * 面包屑项类型
 */
interface BreadcrumbItem {
  path: string;
  title: string;
}

/**
 * 默认路由标题映射（用于菜单树中不存在的路由）
 */
const defaultRouteTitles: Record<string, string> = {
  "/master-console": "总控台",
  "/shops": "门店管理",
  "/power-management": "算力管理",
  "/power-recharge-orders": "充值订单",
  "/analytics/user-behavior": "行为统计",
  "/merchant-knowledge": "知识库管理",
  "/comment-reply-knowledge": "回复文案",
  "/comment-processing-logs": "评价处理日志",
  "/system": "系统管理",
  "/system/user": "用户管理",
  "/system/role": "角色管理",
  "/system/menu": "菜单管理",
  "/system/prompts": "系统提示词",
};

const portalTitleMap: Record<BackendPortal, string> = {
  ADMIN: "管理员工作台",
  ORDINARY: "商户工作台",
  SALES: "网销工作台",
  YOUZAN: "有赞工作台",
};

/**
 * 根据当前路径和菜单树生成面包屑
 */
const getBreadcrumbItems = (
  pathname: string,
  menus: MenuTreeNode[],
): BreadcrumbItem[] => {
  const result: BreadcrumbItem[] = [];

  const findPath = (
    menuList: MenuTreeNode[],
    targetPath: string,
    ancestors: BreadcrumbItem[],
  ): boolean => {
    for (const menu of menuList) {
      if (menu.menuType === "F") continue; // 跳过按钮

      const currentItem: BreadcrumbItem = {
        path: menu.path || "",
        title: menu.menuName,
      };

      if (menu.path === targetPath) {
        result.push(...ancestors, currentItem);
        return true;
      }

      if (menu.children && menu.children.length > 0) {
        if (findPath(menu.children, targetPath, [...ancestors, currentItem])) {
          return true;
        }
      }
    }
    return false;
  };

  // 先从菜单树中查找
  const found = findPath(menus, pathname, []);

  // 如果菜单树中没有找到，使用默认映射
  if (!found) {
    const parts = pathname.split("/").filter(Boolean);
    let currentPath = "";
    for (const part of parts) {
      currentPath += "/" + part;
      const title = defaultRouteTitles[currentPath];
      if (title) {
        result.push({ path: currentPath, title });
      }
    }
  }

  return result;
};

interface MainLayoutProps {
  children: React.ReactNode;
}

const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, logout, switchPortal } = useAuth();
  const { isCompact, isMobile } = useResponsive();

  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>(() =>
    getOpenKeys(location.pathname),
  );

  // 根据用户菜单生成菜单项
  const menus = currentUser?.menus;
  const menuItems = useMemo(() => {
    if (menus && menus.length > 0) {
      return convertMenuTreeToItems(menus);
    }
    return defaultMenuItems;
  }, [menus]);

  // 生成面包屑
  const breadcrumbItems = useMemo(() => {
    return getBreadcrumbItems(location.pathname, menus || []);
  }, [menus, location.pathname]);

  // 处理菜单点击
  const handleMenuClick = useCallback(
    ({ key }: { key: string }) => {
      navigate(key);
      setMobileNavOpen(false);
    },
    [navigate],
  );

  // 处理菜单展开
  const handleOpenChange = useCallback((keys: string[]) => {
    setOpenKeys(keys);
  }, []);

  // 处理退出登录
  const handleLogout = useCallback(async () => {
    await logout();
    setMobileNavOpen(false);
    navigate("/login");
  }, [logout, navigate]);

  const handleSwitchPortal = useCallback(
    async (portal: BackendPortal) => {
      if (!currentUser?.activePortal || currentUser.activePortal === portal) {
        return;
      }
      await switchPortal(portal);
      setMobileNavOpen(false);
      navigate("/");
    },
    [currentUser, navigate, switchPortal],
  );

  // 用户下拉菜单
  const userMenuItems: MenuProps["items"] = useMemo(() => {
    const items: MenuProps["items"] = [
      {
        key: "profile",
        icon: <UserOutlined />,
        label: "个人信息",
        disabled: true,
      },
    ];

    const availablePortals = currentUser?.availablePortals ?? [];
    if (availablePortals.length > 1) {
      items.push({ type: "divider" });
      availablePortals.forEach((portal) => {
        items.push({
          key: `portal:${portal}`,
          label:
            currentUser?.activePortal === portal
              ? `${portalTitleMap[portal]}（当前）`
              : portalTitleMap[portal],
          onClick: () => void handleSwitchPortal(portal),
        });
      });
    }

    items.push({ type: "divider" });
    items.push({
      key: "logout",
      icon: <LogoutOutlined />,
      label: "退出登录",
      onClick: handleLogout,
    });
    return items;
  }, [
    currentUser?.activePortal,
    currentUser?.availablePortals,
    handleLogout,
    handleSwitchPortal,
  ]);

  const currentPageTitle =
    breadcrumbItems[breadcrumbItems.length - 1]?.title || "外卖大师管理系统";

  const renderMenu = () => (
    <Menu
      mode="inline"
      selectedKeys={[location.pathname]}
      openKeys={openKeys}
      onOpenChange={handleOpenChange}
      onClick={handleMenuClick}
      items={menuItems}
      style={{ border: "none" }}
    />
  );

  const headerTrigger = isCompact ? (
    <div
      onClick={() => setMobileNavOpen(true)}
      style={{ fontSize: 18, cursor: "pointer", display: "flex" }}
    >
      <MenuOutlined />
    </div>
  ) : (
    <div
      onClick={() => setCollapsed((previous) => !previous)}
      style={{ fontSize: 18, cursor: "pointer", display: "flex" }}
    >
      {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
    </div>
  );

  return (
    <Layout style={{ height: "100vh", overflow: "hidden" }}>
      {!isCompact ? (
        <Sider
          collapsible
          collapsed={collapsed}
          onCollapse={(nextCollapsed) => setCollapsed(nextCollapsed)}
          trigger={null}
          style={{
            background: "#fff",
            boxShadow: "2px 0 8px rgba(0, 0, 0, 0.05)",
            height: "100vh",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              height: 64,
              display: "flex",
              alignItems: "center",
              justifyContent: collapsed ? "center" : "flex-start",
              padding: collapsed ? 0 : "0 16px",
              borderBottom: "1px solid #f0f0f0",
            }}
          >
            <img src={logo} alt="logo" style={{ width: 32, height: 32 }} />
            {!collapsed && (
              <span
                style={{
                  marginLeft: 6,
                  fontSize: 16,
                  fontWeight: 600,
                  color: "#1c2c3d",
                  whiteSpace: "nowrap",
                }}
              >
                外卖大师管理系统
              </span>
            )}
          </div>
          <div
            style={{
              height: "calc(100vh - 64px)",
              overflowY: "auto",
              paddingTop: 8,
            }}
          >
            {renderMenu()}
          </div>
        </Sider>
      ) : null}
      <Layout style={{ height: "100vh", minWidth: 0, overflow: "hidden" }}>
        <Header
          style={{
            padding: isCompact ? "0 12px" : "0 24px",
            background: "#fff",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            boxShadow: "0 1px 4px rgba(0, 0, 0, 0.05)",
            flex: "0 0 64px",
          }}
        >
          <Space size={isCompact ? "small" : "middle"}>
            {headerTrigger}
            {isCompact ? (
              <Text
                strong
                style={{
                  maxWidth: isMobile ? 180 : 280,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {currentPageTitle}
              </Text>
            ) : breadcrumbItems.length > 0 ? (
              <Breadcrumb
                items={breadcrumbItems.map((item, index) => ({
                  key: item.path || index,
                  title:
                    index < breadcrumbItems.length - 1 && item.path ? (
                      <Link to={item.path}>{item.title}</Link>
                    ) : (
                      item.title
                    ),
                }))}
              />
            ) : null}
          </Space>
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Space style={{ cursor: "pointer" }}>
              {currentUser?.avatar ? (
                <Avatar size="small" src={currentUser.avatar} />
              ) : (
                <Avatar size="small" icon={<UserOutlined />} />
              )}
              <span
                style={{
                  color: "#586673",
                  maxWidth: isCompact ? 96 : 180,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {currentUser?.nickname || currentUser?.username || "用户"}
                {currentUser?.activePortal
                  ? ` · ${portalTitleMap[currentUser.activePortal]}`
                  : ""}
              </span>
            </Space>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: isCompact ? 12 : 16,
            padding: 0,
            minHeight: 0,
            flex: 1,
            overflow: "auto",
          }}
        >
          {children}
        </Content>
      </Layout>
      <Drawer
        title={
          <Space size={8}>
            <img src={logo} alt="logo" style={{ width: 28, height: 28 }} />
            <Text strong>外卖大师管理系统</Text>
          </Space>
        }
        placement="left"
        onClose={() => setMobileNavOpen(false)}
        open={isCompact ? mobileNavOpen : false}
        size={280}
        styles={{ body: { padding: 0 } }}
      >
        {renderMenu()}
      </Drawer>
    </Layout>
  );
};

export default MainLayout;
