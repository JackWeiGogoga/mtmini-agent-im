/**
 * 菜单管理页面
 * 参考 tangram 项目实现
 */
import React, { useState, useEffect, useCallback } from "react";
import {
  Table,
  Button,
  Space,
  Input,
  Form,
  Modal,
  Tag,
  Row,
  Col,
  TreeSelect,
  InputNumber,
  Select,
  Drawer,
  Tabs,
  Descriptions,
} from "antd";
import {
  PlusOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  FolderOutlined,
  FileOutlined,
  ControlOutlined,
  FolderAddOutlined,
  AppstoreAddOutlined,
  PlusSquareOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { createStyles } from "antd-style";
import { message, modal } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import type { MenuTypeValue } from "@/types";
import type { MenuTreeNode } from "@/types/menu-types";
import { MenuType, MenuTypeLabels } from "@/types/menu-types";
import {
  getMenuTree,
  getAllMenus,
  createMenu,
  updateMenu,
  deleteMenu,
} from "@/services/menu-manage-api";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { useResponsive } from "@/hooks";
import {
  getResponsiveDrawerWidth,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";

const useStyles = createStyles(({ token }) => ({
  toolbar: {
    marginBottom: 16,
    display: "flex",
    justifyContent: "space-between",
    flexWrap: "wrap",
    gap: 16,
    "@media (max-width: 991px)": {
      flexDirection: "column",
      alignItems: "stretch",
      "& .ant-space": {
        width: "100%",
        flexWrap: "wrap",
      },
      "& .ant-space-item": {
        flex: "1 1 0",
      },
      "& .ant-btn": {
        width: "100%",
      },
    },
  },
  menuTableContainer: {
    ".menu-name": {
      display: "flex",
      alignItems: "center",
      whiteSpace: "nowrap",
      overflow: "hidden",
    },
    ".menu-icon": {
      display: "flex",
      alignItems: "center",
      marginRight: token.marginXS,
    },
    ".menu-buttons": {
      display: "inline-flex",
      flexWrap: "wrap",
      overflow: "hidden",
      maxWidth: "300px",
    },
  },
  modal: {
    "& .ant-form-item": {
      marginBottom: token.marginSM,
    },
  },
}));

/**
 * 菜单表格数据类型
 */
interface MenuTableData {
  key: string;
  name: string;
  icon?: string;
  menuType: string;
  path?: string;
  component?: string;
  permission?: string;
  orderNum: number;
  status?: number;
  visible?: number;
  children?: MenuTableData[];
  buttonPerms: MenuTreeNode[];
  menu: MenuTreeNode;
}

/**
 * 获取菜单类型图标
 */
function getMenuTypeIcon(type: string) {
  switch (type) {
    case MenuType.DIRECTORY:
      return <FolderAddOutlined />;
    case MenuType.MENU:
      return <AppstoreAddOutlined />;
    case MenuType.BUTTON:
      return <PlusSquareOutlined />;
    default:
      return null;
  }
}

/**
 * 获取菜单类型名称
 */
function getMenuTypeName(type: string) {
  return MenuTypeLabels[type as MenuTypeValue] || type;
}

/**
 * 菜单管理页面
 */
const MenuManage: React.FC = () => {
  const { styles } = useStyles();
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [loading, setLoading] = useState(false);
  const [menuTree, setMenuTree] = useState<MenuTreeNode[]>([]);
  const [tableData, setTableData] = useState<MenuTableData[]>([]);
  const [buttonPermissions, setButtonPermissions] = useState<MenuTreeNode[]>(
    [],
  );
  const [selectedMenu, setSelectedMenu] = useState<MenuTreeNode | null>(null);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isDrawerVisible, setIsDrawerVisible] = useState(false);
  const [editingMenu, setEditingMenu] = useState<MenuTreeNode | null>(null);
  const [modalType, setModalType] = useState<"menu" | "directory" | "button">(
    "menu",
  );
  // 当前表单中的菜单类型，用于动态控制表单项显示
  const [formMenuType, setFormMenuType] = useState<string>("C");

  const [form] = Form.useForm();

  // 将菜单数据转换为表格数据
  const convertMenusToTableData = useCallback(
    (menus: MenuTreeNode[], permissions: MenuTreeNode[]): MenuTableData[] => {
      return menus
        .filter((menu) => menu.menuType === "M" || menu.menuType === "C")
        .map((menu) => {
          // 查找该菜单下的功能权限
          const buttonPerms = permissions.filter(
            (btn) => btn.parentId === menu.menuId,
          );

          // 检查是否有子菜单（不包括按钮）
          const hasRealChildren =
            menu.children &&
            menu.children.length > 0 &&
            menu.children.some((child) => child.menuType !== "F");

          return {
            key: menu.menuId,
            name: menu.menuName,
            icon: menu.icon,
            menuType: menu.menuType,
            path: menu.path,
            component: menu.component,
            permission: menu.permission,
            orderNum: menu.orderNum,
            status: menu.status,
            visible: menu.visible,
            children:
              hasRealChildren && menu.children
                ? convertMenusToTableData(menu.children, permissions)
                : undefined,
            buttonPerms: buttonPerms,
            menu: menu,
          };
        });
    },
    [],
  );

  // 加载菜单数据
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      // 获取所有菜单（包括按钮权限）
      const allMenus = await getAllMenus();
      // 筛选出按钮权限
      const buttonPerms = allMenus.filter((menu) => menu.menuType === "F");
      setButtonPermissions(buttonPerms);

      // 获取菜单树
      const tree = await getMenuTree();
      setMenuTree(tree);

      // 转换为表格数据
      const tableDataItems = convertMenusToTableData(tree, buttonPerms);
      setTableData(tableDataItems);
    } catch (err) {
      console.error("Failed to load menus:", err);
      message.error("获取菜单列表失败");
    } finally {
      setLoading(false);
    }
  }, [convertMenusToTableData]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 重新构建表格数据
  useEffect(() => {
    if (menuTree.length > 0) {
      const tableDataItems = convertMenusToTableData(
        menuTree,
        buttonPermissions,
      );
      setTableData(tableDataItems);
    }
  }, [buttonPermissions, menuTree, convertMenusToTableData]);

  // 显示新增/编辑弹窗
  const showModal = (
    type?: "menu" | "directory" | "button",
    record?: MenuTreeNode,
    isRootDirectory = false,
    parentRecord?: MenuTreeNode,
  ) => {
    setModalType(type || "menu");
    setEditingMenu(record || null);
    form.resetFields();

    if (record) {
      // 编辑现有菜单
      form.setFieldsValue({
        parentId: record.parentId,
        menuName: record.menuName,
        path: record.path,
        component: record.component,
        perms: record.permission, // 后端使用 perms 字段
        icon: record.icon,
        menuType: record.menuType,
        orderNum: record.orderNum,
        status: record.status,
        visible: record.visible,
      });
      setFormMenuType(record.menuType);
    } else {
      // 新建菜单
      if (parentRecord) {
        form.setFieldsValue({
          parentId: parentRecord.menuId,
        });
        setSelectedMenu(parentRecord);
      } else if (isRootDirectory || !selectedMenu) {
        form.setFieldsValue({
          parentId: undefined,
        });
      } else {
        form.setFieldsValue({
          parentId: selectedMenu.menuId,
        });
      }

      // 设置默认类型
      let defaultType = "C";
      if (type) {
        if (type === "directory") defaultType = "M";
        else if (type === "button") defaultType = "F";
        form.setFieldsValue({ menuType: defaultType });
      }
      setFormMenuType(defaultType);
    }

    setIsModalVisible(true);
  };

  // 提交表单
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();

      if (editingMenu) {
        await updateMenu({ menuId: editingMenu.menuId, ...values });
        message.success("更新菜单成功");
      } else {
        await createMenu(values);
        message.success("创建菜单成功");
      }

      setIsModalVisible(false);
      loadData();
    } catch (error) {
      console.error("Error submitting form:", error);
    }
  };

  // 删除菜单
  const handleDelete = async (menuId: string) => {
    modal.confirm({
      title: "确认删除",
      content: "确定要删除这个菜单吗？删除后子菜单也会一并删除。",
      okText: "确认",
      cancelText: "取消",
      onOk: async () => {
        try {
          await deleteMenu(menuId);
          message.success("删除菜单成功");
          loadData();
          // 如果删除的是当前选中的菜单，关闭抽屉
          if (selectedMenu?.menuId === menuId) {
            setIsDrawerVisible(false);
            setSelectedMenu(null);
          }
        } catch (error) {
          console.error("Error deleting menu:", error);
          message.error("删除菜单失败");
        }
      },
    });
  };

  // 处理表格行点击
  const handleRowClick = (record: MenuTableData) => {
    setSelectedMenu(record.menu);
    setIsDrawerVisible(true);
  };

  // 关闭抽屉
  const handleDrawerClose = () => {
    setIsDrawerVisible(false);
  };

  // 获取当前选中菜单的功能权限
  const getButtonPermissionsForMenu = () => {
    if (!selectedMenu) return [];
    return buttonPermissions.filter(
      (btn) => btn.parentId === selectedMenu.menuId,
    );
  };

  // 获取模态窗标题
  const getModalTitle = () => {
    if (editingMenu) {
      return `编辑${modalType === "directory" ? "目录" : modalType === "menu" ? "菜单" : "权限"}`;
    }
    return "新增";
  };

  // 主表格列配置
  const menuColumns: ColumnsType<MenuTableData> = [
    {
      title: "菜单名称",
      dataIndex: "name",
      key: "name",
      width: 240,
      render: (text, record) => (
        <div className="menu-name">
          <span className="menu-icon">{getMenuTypeIcon(record.menuType)}</span>
          <span>{text}</span>
        </div>
      ),
    },
    {
      title: "类型",
      dataIndex: "menuType",
      key: "menuType",
      width: 80,
      render: (type: string) => (
        <Tag color={type === "M" ? "gold" : "green"}>
          {getMenuTypeName(type)}
        </Tag>
      ),
    },
    {
      title: "路径",
      dataIndex: "path",
      key: "path",
      width: 150,
      ellipsis: true,
    },
    {
      title: "权限标识",
      dataIndex: "permission",
      key: "permission",
      width: 150,
      ellipsis: true,
    },
    {
      title: "功能权限",
      key: "buttonPerms",
      width: 200,
      render: (_, record) => {
        if (!record.buttonPerms || record.buttonPerms.length === 0) {
          return "-";
        }
        return (
          <div className="menu-buttons">
            {record.buttonPerms.slice(0, 3).map((btn) => (
              <Tag
                key={btn.menuId}
                color="blue"
                style={{
                  marginRight: 4,
                  fontSize: "12px",
                  padding: "0 4px",
                  lineHeight: "16px",
                }}
              >
                {btn.menuName}
              </Tag>
            ))}
            {record.buttonPerms.length > 3 && (
              <Tag
                color="blue"
                style={{
                  marginRight: 0,
                  fontSize: "12px",
                  padding: "0 4px",
                  lineHeight: "16px",
                }}
              >
                +{record.buttonPerms.length - 3}
              </Tag>
            )}
          </div>
        );
      },
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 70,
      render: (status: number) => (
        <Tag color={status === 1 ? "green" : "red"}>
          {status === 1 ? "正常" : "禁用"}
        </Tag>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 240,
      render: (_, record) => {
        const menuType = record.menuType;
        return (
          <div style={{ display: "flex", flexWrap: "wrap", gap: "4px" }}>
            <PermissionControl permission="system:menu:edit">
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  showModal(
                    menuType === "M" ? "directory" : "menu",
                    record.menu,
                  );
                }}
                size="small"
                style={{ padding: "0 4px", minWidth: "60px" }}
              >
                编辑
              </Button>
            </PermissionControl>
            <PermissionControl permission="system:menu:delete">
              <Button
                type="link"
                danger
                icon={<DeleteOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  handleDelete(record.key);
                }}
                size="small"
                style={{ padding: "0 4px", minWidth: "60px" }}
              >
                删除
              </Button>
            </PermissionControl>
            {(menuType === "M" || menuType === "C") && (
              <PermissionControl permission="system:menu:add">
                <Button
                  type="link"
                  icon={<PlusOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    showModal(undefined, undefined, false, record.menu);
                  }}
                  size="small"
                  style={{ padding: "0 4px" }}
                >
                  新增
                </Button>
              </PermissionControl>
            )}
          </div>
        );
      },
    },
  ];

  // 功能权限表格列配置
  const buttonColumns: ColumnsType<MenuTreeNode> = [
    {
      title: "权限名称",
      dataIndex: "menuName",
      key: "menuName",
    },
    {
      title: "权限标识",
      dataIndex: "permission",
      key: "permission",
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: number) => (
        <Tag color={status === 1 ? "green" : "red"}>
          {status === 1 ? "正常" : "禁用"}
        </Tag>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Space size="middle">
          <PermissionControl permission="system:menu:edit">
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => showModal("button", record)}
            >
              编辑
            </Button>
          </PermissionControl>
          <PermissionControl permission="system:menu:delete">
            <Button
              type="link"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.menuId)}
            >
              删除
            </Button>
          </PermissionControl>
        </Space>
      ),
    },
  ];

  // 将菜单树转换为 TreeSelect 数据
  interface TreeSelectNode {
    value: string;
    title: string;
    children?: TreeSelectNode[];
  }
  const convertToTreeSelectData = (
    menus: MenuTreeNode[],
    excludeId?: string,
  ): TreeSelectNode[] => {
    return menus
      .filter((menu) => menu.menuId !== excludeId && menu.menuType !== "F")
      .map((menu) => ({
        value: menu.menuId,
        title: menu.menuName,
        children: menu.children
          ? convertToTreeSelectData(menu.children, excludeId)
          : undefined,
      }));
  };

  return (
    <PageContainer title="菜单管理">
      <div className={pageStyles.toolbar}>
        <div />
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            刷新
          </Button>
          <PermissionControl permission="system:menu:add">
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setSelectedMenu(null);
                showModal(undefined, undefined, true);
              }}
            >
              添加顶级菜单
            </Button>
          </PermissionControl>
        </Space>
      </div>
      <div className={styles.menuTableContainer}>
        <Table
          columns={menuColumns}
          dataSource={tableData}
          rowKey="key"
          size="middle"
          loading={loading}
          pagination={false}
          scroll={{ x: "max-content" }}
          expandable={{
            defaultExpandAllRows: true,
          }}
          onRow={(record) => ({
            onClick: () => handleRowClick(record),
            style: {
              cursor: "pointer",
              background:
                selectedMenu && selectedMenu.menuId === record.key
                  ? "#f0f7ff"
                  : undefined,
            },
          })}
        />
      </div>

      {/* 菜单详情抽屉 */}
      <Drawer
        title={selectedMenu ? `${selectedMenu.menuName} 详情` : "菜单详情"}
        size={getResponsiveDrawerWidth(isCompact, 580)}
        placement="right"
        onClose={handleDrawerClose}
        open={isDrawerVisible}
        extra={
          selectedMenu && (
            <Space>
              <PermissionControl permission="system:menu:edit">
                <Button
                  icon={<EditOutlined />}
                  onClick={() =>
                    showModal(
                      selectedMenu.menuType === "M" ? "directory" : "menu",
                      selectedMenu,
                    )
                  }
                  size="small"
                >
                  编辑
                </Button>
              </PermissionControl>
              <PermissionControl permission="system:menu:delete">
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(selectedMenu.menuId)}
                  size="small"
                >
                  删除
                </Button>
              </PermissionControl>
            </Space>
          )
        }
      >
        {selectedMenu ? (
          <Tabs
            defaultActiveKey="detail"
            items={[
              {
                key: "detail",
                label: "基本信息",
                children: (
                  <Descriptions
                    column={isCompact ? 1 : 2}
                    bordered
                    size="small"
                    labelStyle={{ fontWeight: 500, width: 100 }}
                  >
                    <Descriptions.Item label="菜单名称" span={2}>
                      <Space>
                        {getMenuTypeIcon(selectedMenu.menuType)}
                        {selectedMenu.menuName}
                      </Space>
                    </Descriptions.Item>
                    <Descriptions.Item label="类型">
                      <Tag
                        color={
                          selectedMenu.menuType === "M"
                            ? "gold"
                            : selectedMenu.menuType === "C"
                              ? "green"
                              : "blue"
                        }
                      >
                        {getMenuTypeName(selectedMenu.menuType)}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="排序">
                      {selectedMenu.orderNum}
                    </Descriptions.Item>
                    {selectedMenu.menuType !== "F" && (
                      <>
                        <Descriptions.Item label="路径" span={2}>
                          <code
                            style={{
                              background: "#f5f5f5",
                              padding: "2px 6px",
                              borderRadius: 4,
                            }}
                          >
                            {selectedMenu.path || "-"}
                          </code>
                        </Descriptions.Item>
                        <Descriptions.Item label="组件" span={2}>
                          <code
                            style={{
                              background: "#f5f5f5",
                              padding: "2px 6px",
                              borderRadius: 4,
                            }}
                          >
                            {selectedMenu.component || "-"}
                          </code>
                        </Descriptions.Item>
                        <Descriptions.Item label="图标">
                          {selectedMenu.icon || "-"}
                        </Descriptions.Item>
                        <Descriptions.Item label="可见性">
                          <Tag
                            color={
                              selectedMenu.visible === 1 ? "green" : "default"
                            }
                          >
                            {selectedMenu.visible === 1 ? "显示" : "隐藏"}
                          </Tag>
                        </Descriptions.Item>
                      </>
                    )}
                    <Descriptions.Item label="权限标识" span={2}>
                      {selectedMenu.permission ? (
                        <Tag color="purple">{selectedMenu.permission}</Tag>
                      ) : (
                        "-"
                      )}
                    </Descriptions.Item>
                    <Descriptions.Item label="状态">
                      <Tag
                        color={selectedMenu.status === 1 ? "success" : "error"}
                      >
                        {selectedMenu.status === 1 ? "正常" : "禁用"}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="菜单ID">
                      <span style={{ fontSize: 12, color: "#999" }}>
                        {selectedMenu.menuId}
                      </span>
                    </Descriptions.Item>
                  </Descriptions>
                ),
              },
              {
                key: "buttons",
                label: "功能权限",
                children: (
                  <div>
                    <div style={{ marginBottom: 16 }}>
                      {selectedMenu.menuType !== "F" && (
                        <PermissionControl permission="system:menu:add">
                          <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            onClick={() =>
                              showModal(
                                "button",
                                undefined,
                                false,
                                selectedMenu,
                              )
                            }
                            size="small"
                          >
                            新增权限
                          </Button>
                        </PermissionControl>
                      )}
                    </div>
                    <Table
                      columns={buttonColumns}
                      dataSource={getButtonPermissionsForMenu()}
                      rowKey="menuId"
                      size="small"
                      pagination={false}
                      scroll={{ x: 520 }}
                    />
                  </div>
                ),
              },
            ]}
          />
        ) : (
          <div style={{ textAlign: "center", padding: "20px 0" }}>
            请在表格中选择一个菜单项查看详情
          </div>
        )}
      </Drawer>

      {/* 新增/编辑弹窗 */}
      <Modal
        title={getModalTitle()}
        open={isModalVisible}
        onOk={handleSubmit}
        onCancel={() => setIsModalVisible(false)}
        okText="确定"
        cancelText="取消"
        width={getResponsiveOverlayWidth(isCompact, 600, 600)}
        className={styles.modal}
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            status: 1,
            visible: 1,
            orderNum: 0,
            menuType: "C",
          }}
        >
          {/* 父菜单选择 */}
          <Form.Item name="parentId" label="上级菜单">
            <TreeSelect
              placeholder="选择上级菜单（不选则为顶级菜单）"
              allowClear
              treeData={convertToTreeSelectData(menuTree, editingMenu?.menuId)}
              treeDefaultExpandAll
            />
          </Form.Item>

          <Form.Item
            name="menuType"
            label="类型"
            rules={[{ required: true, message: "请选择菜单类型" }]}
          >
            <Select
              placeholder="请选择菜单类型"
              onChange={(value) => {
                setFormMenuType(value);
                if (value === "M") setModalType("directory");
                else if (value === "C") setModalType("menu");
                else if (value === "F") setModalType("button");
              }}
            >
              <Select.Option value="M">
                <FolderOutlined /> 目录
              </Select.Option>
              <Select.Option value="C">
                <FileOutlined /> 菜单
              </Select.Option>
              <Select.Option value="F">
                <ControlOutlined /> 权限
              </Select.Option>
            </Select>
          </Form.Item>

          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                name="menuName"
                label={formMenuType === "F" ? "权限名称" : "菜单名称"}
                rules={[
                  {
                    required: true,
                    message: `请输入${formMenuType === "F" ? "权限名称" : "菜单名称"}`,
                  },
                ]}
              >
                <Input
                  placeholder={`请输入${formMenuType === "F" ? "权限名称" : "菜单名称"}`}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="orderNum" label="排序">
                <InputNumber min={0} max={999} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>

          {formMenuType !== "F" && (
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="path" label="路径">
                  <Input placeholder="请输入路径" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="icon" label="图标">
                  <Input placeholder="请输入图标" />
                </Form.Item>
              </Col>
            </Row>
          )}

          {formMenuType === "C" && (
            <Form.Item name="component" label="组件路径">
              <Input placeholder="如：pages/user-manage/UserManage" />
            </Form.Item>
          )}

          <Form.Item
            name="perms"
            label="权限标识"
            rules={
              formMenuType === "F"
                ? [{ required: true, message: "请输入权限标识" }]
                : []
            }
          >
            <Input placeholder="如：system:user:list" />
          </Form.Item>

          <Row gutter={16}>
            {formMenuType !== "F" && (
              <Col xs={24} md={12}>
                <Form.Item name="visible" label="是否可见">
                  <Select>
                    <Select.Option value={1}>显示</Select.Option>
                    <Select.Option value={0}>隐藏</Select.Option>
                  </Select>
                </Form.Item>
              </Col>
            )}
            <Col xs={24} md={formMenuType !== "F" ? 12 : 24}>
              <Form.Item
                name="status"
                label="状态"
                rules={[{ required: true, message: "请选择状态" }]}
              >
                <Select placeholder="请选择状态">
                  <Select.Option value={1}>正常</Select.Option>
                  <Select.Option value={0}>禁用</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default MenuManage;
