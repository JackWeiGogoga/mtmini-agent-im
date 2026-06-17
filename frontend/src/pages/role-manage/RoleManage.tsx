/**
 * 角色管理页面
 * 参考 tangram 项目实现
 */
import React, { useState, useEffect, useCallback } from "react";
import {
  Table,
  Button,
  Space,
  Input,
  Select,
  Form,
  Modal,
  Tag,
  Popconfirm,
  Row,
  Col,
  Switch,
  Checkbox,
  InputNumber,
} from "antd";
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  SafetyCertificateOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { message } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import type {
  RoleVO,
  RoleQueryParam,
  RoleCreateParam,
  RoleUpdateParam,
} from "@/types";
import type { MenuTreeNode } from "@/types/menu-types";
import {
  getRoleList,
  createRole,
  updateRole,
  deleteRole,
  updateRoleStatus,
  assignMenus,
  getRoleDetail,
} from "@/services/role-manage-api";
import { getMenuTree, getAllMenus } from "@/services/menu-manage-api";
import { useResponsive } from "@/hooks";
import {
  getResponsiveControlStyle,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";

const { Option } = Select;
const { TextArea } = Input;

/**
 * 角色表单属性
 */
interface RoleFormProps {
  visible: boolean;
  isCompact: boolean;
  editData: RoleVO | null;
  onCancel: () => void;
  onSubmit: (values: RoleCreateParam | RoleUpdateParam) => Promise<void>;
}

/**
 * 角色表单弹窗
 */
const RoleFormModal: React.FC<RoleFormProps> = ({
  visible,
  isCompact,
  editData,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const isEdit = !!editData;

  useEffect(() => {
    if (visible) {
      if (editData) {
        form.setFieldsValue(editData);
      } else {
        form.resetFields();
      }
    }
  }, [visible, editData, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      if (isEdit) {
        await onSubmit({ ...values, roleId: editData!.roleId });
      } else {
        await onSubmit(values);
      }
      form.resetFields();
    } catch {
      // 表单验证错误
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={isEdit ? "编辑角色" : "新增角色"}
      open={visible}
      onCancel={onCancel}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={getResponsiveOverlayWidth(isCompact, 500, 500)}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ status: 1, orderNum: 0 }}
      >
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="roleName"
              label="角色名称"
              rules={[
                { required: true, message: "请输入角色名称" },
                { max: 50, message: "角色名称最多 50 个字符" },
              ]}
            >
              <Input placeholder="请输入角色名称" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="roleCode"
              label="角色编码"
              rules={[
                { required: true, message: "请输入角色编码" },
                {
                  pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/,
                  message: "只能包含字母、数字和下划线",
                },
              ]}
            >
              <Input placeholder="请输入角色编码" disabled={isEdit} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item name="orderNum" label="排序">
              <InputNumber min={0} max={999} style={{ width: "100%" }} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item name="status" label="状态" valuePropName="checked">
              <Switch
                checkedChildren="正常"
                unCheckedChildren="停用"
                defaultChecked
                onChange={(checked) =>
                  form.setFieldValue("status", checked ? 1 : 0)
                }
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="description" label="描述">
          <TextArea
            rows={3}
            placeholder="请输入角色描述"
            maxLength={200}
            showCount
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

/**
 * 菜单表格数据类型
 */
interface MenuTableData {
  key: string;
  name: string;
  menuType: string;
  permissions: MenuTreeNode[];
  children?: MenuTableData[];
}

/**
 * 权限分配弹窗 - 使用 Table + Checkbox 方式
 */
interface AssignPermissionModalProps {
  visible: boolean;
  isCompact: boolean;
  role: RoleVO | null;
  onCancel: () => void;
  onSubmit: (roleId: string, menuIds: string[]) => Promise<void>;
}

const AssignPermissionModal: React.FC<AssignPermissionModalProps> = ({
  visible,
  isCompact,
  role,
  onCancel,
  onSubmit,
}) => {
  const [loading, setLoading] = useState(false);
  const [menuTableData, setMenuTableData] = useState<MenuTableData[]>([]);
  const [menuLoading, setMenuLoading] = useState(false);
  // 所有权限类型的菜单（F - 按钮）
  const [allPermissions, setAllPermissions] = useState<MenuTreeNode[]>([]);
  // 选中的菜单ID（目录和菜单）
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);
  // 选中的权限（按钮），格式：{ menuId: [permId1, permId2] }
  const [selectedPermissions, setSelectedPermissions] = useState<
    Record<string, string[]>
  >({});
  // 是否全选
  const [selectAll, setSelectAll] = useState(false);
  // 菜单到权限的映射
  const [allMenuPermissionsMap, setAllMenuPermissionsMap] = useState<
    Record<string, MenuTreeNode[]>
  >({});

  // 将菜单数据转换为表格数据
  const convertMenusToTableData = useCallback(
    (
      menus: MenuTreeNode[],
      selectedMenus: string[] = [],
      selectedPerms: Record<string, string[]> = {},
    ): MenuTableData[] => {
      return menus
        .filter((menu) => menu.menuType === "M" || menu.menuType === "C") // 只处理目录和菜单
        .map((menu) => {
          // 查找该菜单下的权限（按钮）
          const menuPermissions = allPermissions.filter(
            (perm) => perm.parentId === menu.menuId,
          );

          // 检查是否有子菜单（不包括按钮）
          const hasRealChildren =
            menu.children &&
            menu.children.length > 0 &&
            menu.children.some((child) => child.menuType !== "F");

          return {
            key: menu.menuId,
            name: menu.menuName,
            menuType: menu.menuType,
            permissions: menuPermissions,
            children:
              hasRealChildren && menu.children
                ? convertMenusToTableData(
                    menu.children,
                    selectedMenus,
                    selectedPerms,
                  )
                : undefined,
          };
        });
    },
    [allPermissions],
  );

  const loadData = async () => {
    if (!role) return;
    setMenuLoading(true);
    setSelectAll(false);

    try {
      // 并行获取菜单树、所有菜单、角色详情
      const [menuTree, allMenuItems, roleDetail] = await Promise.all([
        getMenuTree(),
        getAllMenus(),
        getRoleDetail(role.roleId),
      ]);

      // 筛选出权限类型的菜单（按钮 F）
      const permissions = allMenuItems.filter((menu) => menu.menuType === "F");
      setAllPermissions(permissions);

      // 构建菜单ID到权限列表的映射
      const menuPermMap: Record<string, MenuTreeNode[]> = {};
      permissions.forEach((perm) => {
        if (perm.parentId) {
          if (!menuPermMap[perm.parentId]) {
            menuPermMap[perm.parentId] = [];
          }
          menuPermMap[perm.parentId].push(perm);
        }
      });
      setAllMenuPermissionsMap(menuPermMap);

      // 获取角色已分配的菜单ID
      const assignedMenuIds = roleDetail.menuIds || [];

      // 分离出目录/菜单 和 按钮权限
      const menuIds: string[] = [];
      const permissionMap: Record<string, string[]> = {};

      assignedMenuIds.forEach((menuId) => {
        // 查找这个 menuId 是不是权限（按钮）
        const perm = permissions.find((p) => p.menuId === menuId);
        if (perm && perm.parentId) {
          // 是权限，加入到对应菜单的权限列表
          if (!permissionMap[perm.parentId]) {
            permissionMap[perm.parentId] = [];
          }
          permissionMap[perm.parentId].push(menuId);
        } else {
          // 是目录/菜单
          menuIds.push(menuId);
        }
      });

      // 设置已选菜单和权限
      setSelectedRowKeys(menuIds);
      setSelectedPermissions(permissionMap);

      // 转换为表格数据（需要等 allPermissions 设置后再转换）
      const tableData = convertMenusToTableDataWithPerms(
        menuTree,
        permissions,
        menuIds,
        permissionMap,
      );
      setMenuTableData(tableData);
    } catch (error) {
      console.error("Error fetching data:", error);
      message.error("获取数据失败");
    } finally {
      setMenuLoading(false);
    }
  };

  // 加载菜单和角色详情
  useEffect(() => {
    if (visible && role) {
      loadData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, role]);

  // 单独的转换函数，不依赖 state
  const convertMenusToTableDataWithPerms = (
    menus: MenuTreeNode[],
    permissions: MenuTreeNode[],
    selectedMenus: string[],
    selectedPerms: Record<string, string[]>,
  ): MenuTableData[] => {
    return menus
      .filter((menu) => menu.menuType === "M" || menu.menuType === "C")
      .map((menu) => {
        const menuPermissions = permissions.filter(
          (perm) => perm.parentId === menu.menuId,
        );
        const hasRealChildren =
          menu.children &&
          menu.children.length > 0 &&
          menu.children.some((child) => child.menuType !== "F");

        return {
          key: menu.menuId,
          name: menu.menuName,
          menuType: menu.menuType,
          permissions: menuPermissions,
          children:
            hasRealChildren && menu.children
              ? convertMenusToTableDataWithPerms(
                  menu.children,
                  permissions,
                  selectedMenus,
                  selectedPerms,
                )
              : undefined,
        };
      });
  };

  // 处理表格行选择变化
  const handleRowSelectionChange = (selectedKeys: React.Key[]) => {
    setSelectedRowKeys(selectedKeys as string[]);
  };

  // 处理权限复选框变化
  const handlePermissionCheckboxChange = (
    menuId: string,
    permId: string,
    checked: boolean,
  ) => {
    const updatedPermissions = { ...selectedPermissions };

    if (!updatedPermissions[menuId]) {
      updatedPermissions[menuId] = [];
    }

    if (checked) {
      if (!updatedPermissions[menuId].includes(permId)) {
        updatedPermissions[menuId] = [...updatedPermissions[menuId], permId];
      }
    } else {
      updatedPermissions[menuId] = updatedPermissions[menuId].filter(
        (id) => id !== permId,
      );
    }

    setSelectedPermissions(updatedPermissions);
  };

  // 检查权限是否被选中
  const isPermissionChecked = (menuId: string, permId: string): boolean => {
    return selectedPermissions[menuId]?.includes(permId) || false;
  };

  // 菜单级别的全选/取消全选
  const handleMenuPermissionsSelectAll = (menuId: string, checked: boolean) => {
    const updatedPermissions = { ...selectedPermissions };
    const menuPermissions = allPermissions.filter(
      (perm) => perm.parentId === menuId,
    );

    if (checked) {
      updatedPermissions[menuId] = menuPermissions.map((perm) => perm.menuId);
    } else {
      updatedPermissions[menuId] = [];
    }

    setSelectedPermissions(updatedPermissions);
  };

  // 检查菜单下的权限是否全部选中
  const isMenuPermissionsAllSelected = (menuId: string): boolean => {
    const menuPermissions = allPermissions.filter(
      (perm) => perm.parentId === menuId,
    );
    const selectedMenuPerms = selectedPermissions[menuId] || [];

    return (
      menuPermissions.length > 0 &&
      menuPermissions.every((perm) => selectedMenuPerms.includes(perm.menuId))
    );
  };

  // 检查菜单下的权限是否部分选中
  const isMenuPermissionsIndeterminate = (menuId: string): boolean => {
    const menuPermissions = allPermissions.filter(
      (perm) => perm.parentId === menuId,
    );
    const selectedMenuPerms = selectedPermissions[menuId] || [];

    return (
      selectedMenuPerms.length > 0 &&
      selectedMenuPerms.length < menuPermissions.length
    );
  };

  // 递归收集所有菜单ID
  const collectAllMenuIds = (items: MenuTableData[]): string[] => {
    const ids: string[] = [];
    items.forEach((item) => {
      ids.push(item.key);
      if (item.children) {
        ids.push(...collectAllMenuIds(item.children));
      }
    });
    return ids;
  };

  // 全选/取消全选
  const handleSelectAllPermissions = () => {
    const newSelectAll = !selectAll;
    setSelectAll(newSelectAll);

    if (newSelectAll) {
      // 收集所有菜单ID
      const allMenuIds = collectAllMenuIds(menuTableData);
      setSelectedRowKeys(allMenuIds);

      // 全选所有权限
      const allPerms: Record<string, string[]> = {};
      Object.keys(allMenuPermissionsMap).forEach((menuId) => {
        allPerms[menuId] = allMenuPermissionsMap[menuId].map(
          (perm) => perm.menuId,
        );
      });
      setSelectedPermissions(allPerms);
    } else {
      setSelectedRowKeys([]);
      setSelectedPermissions({});
    }
  };

  // 提交
  const handleSubmit = async () => {
    if (!role) return;
    setLoading(true);

    try {
      // 收集所有选中的菜单ID
      const menuIds = [...selectedRowKeys];

      // 收集所有选中的权限ID
      const permissionIds: string[] = [];
      Object.values(selectedPermissions).forEach((permissions) => {
        permissionIds.push(...permissions);
      });

      // 合并菜单和权限ID
      const allMenuIds = [...new Set([...menuIds, ...permissionIds])];

      await onSubmit(role.roleId, allMenuIds);
    } finally {
      setLoading(false);
    }
  };

  // 菜单权限表格列配置
  const menuColumns: ColumnsType<MenuTableData> = [
    {
      title: "菜单名称",
      dataIndex: "name",
      key: "name",
      width: 240,
    },
    {
      title: "类型",
      dataIndex: "menuType",
      key: "menuType",
      width: 100,
      render: (type: string) => (
        <Tag color={type === "M" ? "gold" : "green"}>
          {type === "M" ? "目录" : "菜单"}
        </Tag>
      ),
    },
    {
      title: (
        <div>
          <Checkbox checked={selectAll} onChange={handleSelectAllPermissions}>
            全部权限
          </Checkbox>
        </div>
      ),
      key: "allPermissions",
      width: 120,
      render: (_, record) => {
        if (!record.permissions || record.permissions.length === 0) {
          return "-";
        }
        return (
          <Checkbox
            checked={isMenuPermissionsAllSelected(record.key)}
            indeterminate={isMenuPermissionsIndeterminate(record.key)}
            onChange={(e) =>
              handleMenuPermissionsSelectAll(record.key, e.target.checked)
            }
          >
            全选
          </Checkbox>
        );
      },
    },
    {
      title: "权限",
      key: "permissions",
      render: (_, record) => {
        if (!record.permissions || record.permissions.length === 0) {
          return "-";
        }
        return (
          <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
            {record.permissions.map((perm) => (
              <Checkbox
                key={perm.menuId}
                checked={isPermissionChecked(record.key, perm.menuId)}
                onChange={(e) =>
                  handlePermissionCheckboxChange(
                    record.key,
                    perm.menuId,
                    e.target.checked,
                  )
                }
              >
                {perm.menuName}
              </Checkbox>
            ))}
          </div>
        );
      },
    },
  ];

  return (
    <Modal
      title="分配权限"
      open={visible}
      onCancel={onCancel}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={getResponsiveOverlayWidth(isCompact, 1000, 1000)}
      destroyOnClose
    >
      <div style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 8 }}>
          <span style={{ fontWeight: "bold", marginRight: 8 }}>角色名称:</span>
          <span>{role?.roleName}</span>
        </div>
        {role?.description && (
          <div>
            <span style={{ fontWeight: "bold", marginRight: 8 }}>
              角色描述:
            </span>
            <span>{role?.description}</span>
          </div>
        )}
      </div>
      <div style={{ maxHeight: "500px", overflow: "auto" }}>
        <Table
          rowSelection={{
            type: "checkbox",
            selectedRowKeys: selectedRowKeys,
            onChange: handleRowSelectionChange,
            checkStrictly: false,
          }}
          columns={menuColumns}
          dataSource={menuTableData}
          rowKey="key"
          loading={menuLoading}
          pagination={false}
          scroll={{ x: 760 }}
          expandable={{
            defaultExpandAllRows: true,
          }}
        />
      </div>
    </Modal>
  );
};

/**
 * 角色管理页面
 */
const RoleManage: React.FC = () => {
  const { styles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RoleVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [query, setQuery] = useState<RoleQueryParam>({});
  const [formVisible, setFormVisible] = useState(false);
  const [editData, setEditData] = useState<RoleVO | null>(null);
  const [permissionVisible, setPermissionVisible] = useState(false);
  const [selectedRole, setSelectedRole] = useState<RoleVO | null>(null);

  const [searchForm] = Form.useForm();

  // 加载角色列表
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getRoleList({
        ...query,
        pageNum: current,
        pageSize,
      });
      setData(result.list);
      setTotal(result.total);
    } catch (err) {
      console.error("Failed to load roles:", err);
    } finally {
      setLoading(false);
    }
  }, [query, current, pageSize]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 搜索
  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setQuery(values);
    setCurrent(1);
  };

  // 重置搜索
  const handleReset = () => {
    searchForm.resetFields();
    setQuery({});
    setCurrent(1);
  };

  // 新增角色
  const handleAdd = () => {
    setEditData(null);
    setFormVisible(true);
  };

  // 编辑角色
  const handleEdit = (record: RoleVO) => {
    setEditData(record);
    setFormVisible(true);
  };

  // 删除角色
  const handleDelete = async (roleId: string) => {
    try {
      await deleteRole(roleId);
      message.success("删除成功");
      loadData();
    } catch (err) {
      console.error("Failed to delete role:", err);
    }
  };

  // 提交表单
  const handleFormSubmit = async (
    values: RoleCreateParam | RoleUpdateParam,
  ) => {
    try {
      if ("roleId" in values) {
        await updateRole(values);
        message.success("更新成功");
      } else {
        await createRole(values);
        message.success("创建成功");
      }
      setFormVisible(false);
      loadData();
    } catch (err) {
      console.error("Failed to submit:", err);
      throw err;
    }
  };

  // 更新状态
  const handleStatusChange = async (roleId: string, status: number) => {
    try {
      await updateRoleStatus(roleId, status);
      message.success("状态更新成功");
      loadData();
    } catch (err) {
      console.error("Failed to update status:", err);
    }
  };

  // 分配权限
  const handleAssignPermission = (record: RoleVO) => {
    setSelectedRole(record);
    setPermissionVisible(true);
  };

  const handlePermissionSubmit = async (roleId: string, menuIds: string[]) => {
    try {
      await assignMenus({ roleId, menuIds });
      message.success("权限分配成功");
      setPermissionVisible(false);
    } catch (err) {
      console.error("Failed to assign permissions:", err);
      throw err;
    }
  };

  // 表格列定义
  const columns: ColumnsType<RoleVO> = [
    {
      title: "角色名称",
      dataIndex: "roleName",
      key: "roleName",
      width: 150,
    },
    {
      title: "角色编码",
      dataIndex: "roleCode",
      key: "roleCode",
      width: 150,
      render: (code: string) => <Tag color="geekblue">{code}</Tag>,
    },
    {
      title: "排序",
      dataIndex: "orderNum",
      key: "orderNum",
      width: 80,
    },
    {
      title: "描述",
      dataIndex: "description",
      key: "description",
      ellipsis: true,
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (status: number, record) => (
        <PermissionControl permission="system:role:edit">
          <Switch
            checked={status === 1}
            onChange={(checked) =>
              handleStatusChange(record.roleId, checked ? 1 : 0)
            }
            checkedChildren="正常"
            unCheckedChildren="停用"
          />
        </PermissionControl>
      ),
    },
    {
      title: "创建时间",
      dataIndex: "createTime",
      key: "createTime",
      width: 180,
    },
    {
      title: "操作",
      key: "action",
      width: 220,
      fixed: isCompact ? undefined : "right",
      render: (_, record) => (
        <Space size="small" wrap={isCompact}>
          <PermissionControl permission="system:role:edit">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
          </PermissionControl>
          <PermissionControl permission="system:role:assign">
            <Button
              type="link"
              size="small"
              icon={<SafetyCertificateOutlined />}
              onClick={() => handleAssignPermission(record)}
            >
              权限
            </Button>
          </PermissionControl>
          <PermissionControl permission="system:role:delete">
            <Popconfirm
              title="确定要删除该角色吗？"
              onConfirm={() => handleDelete(record.roleId)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </PermissionControl>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="角色管理">
      <div className={styles.toolbar}>
        <Form form={searchForm} layout="inline" className={styles.searchForm}>
          <Form.Item name="roleName">
            <Input
              placeholder="角色名称"
              allowClear
              style={getResponsiveControlStyle(isCompact, 150)}
            />
          </Form.Item>
          <Form.Item name="roleCode">
            <Input
              placeholder="角色编码"
              allowClear
              style={getResponsiveControlStyle(isCompact, 150)}
            />
          </Form.Item>
          <Form.Item name="status">
            <Select
              placeholder="状态"
              allowClear
              style={getResponsiveControlStyle(isCompact, 120)}
            >
              <Option value={1}>正常</Option>
              <Option value={0}>停用</Option>
            </Select>
          </Form.Item>
          <Form.Item className={styles.compactFull}>
            <Space className={styles.searchActions}>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={handleSearch}
              >
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
        <PermissionControl permission="system:role:add">
          <Button
            className={styles.toolbarAction}
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
          >
            新增角色
          </Button>
        </PermissionControl>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="roleId"
        loading={loading}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1000 }}
        pagination={{
          current,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: !isCompact,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (page, size) => {
            setCurrent(page);
            setPageSize(size);
          },
        }}
      />

      <RoleFormModal
        visible={formVisible}
        isCompact={isCompact}
        editData={editData}
        onCancel={() => setFormVisible(false)}
        onSubmit={handleFormSubmit}
      />

      <AssignPermissionModal
        visible={permissionVisible}
        isCompact={isCompact}
        role={selectedRole}
        onCancel={() => setPermissionVisible(false)}
        onSubmit={handlePermissionSubmit}
      />
    </PageContainer>
  );
};

export default RoleManage;
