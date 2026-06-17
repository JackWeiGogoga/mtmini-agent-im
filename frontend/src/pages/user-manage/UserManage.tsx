/**
 * 用户管理页面
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
} from "antd";
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  DeleteOutlined,
  KeyOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { message } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import type {
  UserVO,
  UserQueryParam,
  UserCreateParam,
  UserUpdateParam,
  Role,
  AuthProviderType,
} from "@/types";
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  resetPassword,
} from "@/services/user-manage-api";
import { getAllRoles } from "@/services/role-manage-api";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { useResponsive } from "@/hooks";
import {
  getResponsiveControlStyle,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";

const { Option } = Select;

/**
 * 用户表单属性
 */
interface UserFormProps {
  visible: boolean;
  isCompact: boolean;
  editData: UserVO | null;
  roles: Role[];
  onCancel: () => void;
  onSubmit: (values: UserCreateParam | UserUpdateParam) => Promise<void>;
}

/**
 * 用户表单弹窗
 */
const UserFormModal: React.FC<UserFormProps> = ({
  visible,
  isCompact,
  editData,
  roles,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const isEdit = !!editData;

  useEffect(() => {
    if (visible) {
      if (editData) {
        form.setFieldsValue({
          ...editData,
          roleIds: editData.roleIds || [],
        });
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
        await onSubmit({ ...values, userId: editData!.userId });
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
      title={isEdit ? "编辑用户" : "新增用户"}
      open={visible}
      onCancel={onCancel}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={getResponsiveOverlayWidth(isCompact, 600, 600)}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ status: 1 }}>
        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="username"
              label="用户名"
              rules={[
                { required: true, message: "请输入用户名" },
                { min: 2, max: 50, message: "用户名长度为 2-50 个字符" },
              ]}
            >
              <Input placeholder="请输入用户名" disabled={isEdit} />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="nickname"
              label="昵称"
              rules={[
                { required: true, message: "请输入昵称" },
                { max: 50, message: "昵称最多 50 个字符" },
              ]}
            >
              <Input placeholder="请输入昵称" />
            </Form.Item>
          </Col>
        </Row>

        {!isEdit && (
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, max: 20, message: "密码长度为 6-20 个字符" },
            ]}
          >
            <Input.Password placeholder="请输入密码" />
          </Form.Item>
        )}

        <Row gutter={16}>
          <Col xs={24} md={12}>
            <Form.Item
              name="email"
              label="邮箱"
              rules={[{ type: "email", message: "请输入有效的邮箱地址" }]}
            >
              <Input placeholder="请输入邮箱" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                {
                  pattern: /^1[3-9]\d{9}$/,
                  message: "请输入有效的手机号",
                },
              ]}
            >
              <Input placeholder="请输入手机号" />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="roleIds" label="角色">
          <Select mode="multiple" placeholder="请选择角色" allowClear>
            {roles.map((role) => (
              <Option key={role.roleId} value={role.roleId}>
                {role.roleName}
              </Option>
            ))}
          </Select>
        </Form.Item>

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
      </Form>
    </Modal>
  );
};

/**
 * 重置密码弹窗
 */
interface ResetPasswordModalProps {
  visible: boolean;
  isCompact: boolean;
  userId: string | null;
  onCancel: () => void;
  onSubmit: (userId: string, newPassword: string) => Promise<void>;
}

const ResetPasswordModal: React.FC<ResetPasswordModalProps> = ({
  visible,
  isCompact,
  userId,
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (visible) {
      form.resetFields();
    }
  }, [visible, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (!userId) return;
      setLoading(true);
      await onSubmit(userId, values.newPassword);
      form.resetFields();
    } catch {
      // 表单验证错误
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="重置密码"
      open={visible}
      onCancel={onCancel}
      onOk={handleSubmit}
      confirmLoading={loading}
      width={getResponsiveOverlayWidth(isCompact, 400, 400)}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[
            { required: true, message: "请输入新密码" },
            { min: 6, max: 20, message: "密码长度为 6-20 个字符" },
          ]}
        >
          <Input.Password placeholder="请输入新密码" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认密码"
          dependencies={["newPassword"]}
          rules={[
            { required: true, message: "请确认密码" },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue("newPassword") === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error("两次输入的密码不一致"));
              },
            }),
          ]}
        >
          <Input.Password placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

/**
 * 用户管理页面
 */
const UserManage: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<UserVO[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [query, setQuery] = useState<UserQueryParam>({});
  const [roles, setRoles] = useState<Role[]>([]);
  const [formVisible, setFormVisible] = useState(false);
  const [editData, setEditData] = useState<UserVO | null>(null);
  const [resetPwdVisible, setResetPwdVisible] = useState(false);
  const [resetPwdUserId, setResetPwdUserId] = useState<string | null>(null);

  const [searchForm] = Form.useForm();

  // 加载角色列表
  const loadRoles = useCallback(async () => {
    try {
      const roleList = await getAllRoles();
      setRoles(roleList);
    } catch (err) {
      console.error("Failed to load roles:", err);
    }
  }, []);

  // 加载用户列表
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await getUserList({
        ...query,
        pageNum: current,
        pageSize,
      });
      setData(result.list);
      setTotal(result.total);
    } catch (err) {
      console.error("Failed to load users:", err);
    } finally {
      setLoading(false);
    }
  }, [query, current, pageSize]);

  useEffect(() => {
    loadRoles();
  }, [loadRoles]);

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

  // 新增用户
  const handleAdd = () => {
    setEditData(null);
    setFormVisible(true);
  };

  // 编辑用户
  const handleEdit = (record: UserVO) => {
    setEditData(record);
    setFormVisible(true);
  };

  // 删除用户
  const handleDelete = async (userId: string) => {
    try {
      await deleteUser(userId);
      message.success("删除成功");
      loadData();
    } catch (err) {
      console.error("Failed to delete user:", err);
    }
  };

  // 提交表单
  const handleFormSubmit = async (
    values: UserCreateParam | UserUpdateParam,
  ) => {
    try {
      if ("userId" in values) {
        await updateUser(values);
        message.success("更新成功");
      } else {
        await createUser(values);
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
  const handleStatusChange = async (userId: string, status: number) => {
    try {
      await updateUserStatus(userId, status);
      message.success("状态更新成功");
      loadData();
    } catch (err) {
      console.error("Failed to update status:", err);
    }
  };

  // 重置密码
  const handleResetPassword = (userId: string) => {
    setResetPwdUserId(userId);
    setResetPwdVisible(true);
  };

  const handleResetPwdSubmit = async (userId: string, newPassword: string) => {
    try {
      await resetPassword({ userId, newPassword });
      message.success("密码重置成功");
      setResetPwdVisible(false);
    } catch (err) {
      console.error("Failed to reset password:", err);
      throw err;
    }
  };

  /**
   * 获取用户来源标签配置
   */
  const getAuthProviderConfig = (provider: AuthProviderType | undefined) => {
    const configs: Record<string, { label: string; color: string }> = {
      PASSWORD: { label: "邮箱注册", color: "blue" },
      SMS: { label: "短信验证码", color: "cyan" },
      GOOGLE: { label: "Google", color: "red" },
      GITHUB: { label: "GitHub", color: "purple" },
    };
    return provider && configs[provider]
      ? configs[provider]
      : { label: "未知", color: "default" };
  };

  // 表格列定义
  const columns: ColumnsType<UserVO> = [
    {
      title: "用户名",
      dataIndex: "username",
      key: "username",
      width: 120,
    },
    {
      title: "昵称",
      dataIndex: "nickname",
      key: "nickname",
      width: 120,
    },
    {
      title: "邮箱",
      dataIndex: "email",
      key: "email",
      width: 200,
    },
    {
      title: "用户来源",
      dataIndex: "authProvider",
      key: "authProvider",
      width: 100,
      render: (authProvider: AuthProviderType) => {
        const config = getAuthProviderConfig(authProvider);
        return <Tag color={config.color}>{config.label}</Tag>;
      },
    },
    {
      title: "角色",
      dataIndex: "roleNames",
      key: "roleNames",
      width: 200,
      render: (roleNames: string[]) => (
        <Space size={4} wrap>
          {roleNames?.map((name, index) => (
            <Tag key={index} color="blue">
              {name}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      width: 100,
      render: (status: number, record) => (
        <PermissionControl permission="system:user:edit">
          <Switch
            checked={status === 1}
            onChange={(checked) =>
              handleStatusChange(record.userId, checked ? 1 : 0)
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
      width: 240,
      fixed: isCompact ? undefined : "right",
      render: (_, record) => (
        <Space size={4} wrap={isCompact}>
          <PermissionControl permission="system:user:edit">
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
          </PermissionControl>
          <PermissionControl permission="system:user:delete">
            <Popconfirm
              title="确定要删除该用户吗？"
              onConfirm={() => handleDelete(record.userId)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </PermissionControl>
          {/* 只有邮箱登录的用户才支持重置密码 */}
          {record.authProvider === "PASSWORD" && (
            <PermissionControl permission="system:user:resetPwd">
              <Button
                type="link"
                size="small"
                icon={<KeyOutlined />}
                onClick={() => handleResetPassword(record.userId)}
              >
                重置密码
              </Button>
            </PermissionControl>
          )}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="用户管理">
      <div className={pageStyles.toolbar}>
        <Form
          form={searchForm}
          layout="inline"
          className={pageStyles.searchForm}
        >
          <Form.Item name="username">
            <Input
              placeholder="用户名"
              allowClear
              style={getResponsiveControlStyle(isCompact, 150)}
            />
          </Form.Item>
          <Form.Item name="nickname">
            <Input
              placeholder="昵称"
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
          <Form.Item className={pageStyles.compactFull}>
            <Space className={pageStyles.searchActions}>
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
        <PermissionControl permission="system:user:add">
          <Button
            className={pageStyles.toolbarAction}
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAdd}
          >
            新增用户
          </Button>
        </PermissionControl>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="userId"
        loading={loading}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1300 }}
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

      <UserFormModal
        visible={formVisible}
        isCompact={isCompact}
        editData={editData}
        roles={roles}
        onCancel={() => setFormVisible(false)}
        onSubmit={handleFormSubmit}
      />

      <ResetPasswordModal
        visible={resetPwdVisible}
        isCompact={isCompact}
        userId={resetPwdUserId}
        onCancel={() => setResetPwdVisible(false)}
        onSubmit={handleResetPwdSubmit}
      />
    </PageContainer>
  );
};

export default UserManage;
