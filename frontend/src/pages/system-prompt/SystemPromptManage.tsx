import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Descriptions, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EditOutlined, ReloadOutlined, RollbackOutlined } from "@ant-design/icons";
import { message } from "@/components/AppMessageContext";
import PageContainer from "@/components/PageContainer";
import PromptVariableEditor from "@/components/PromptVariableEditor";
import type { PromptVariableOption } from "@/components/PromptVariableEditor";
import {
  getSystemPrompt,
  listSystemPrompts,
  restoreSystemPromptDefault,
  updateSystemPrompt,
  updateSystemPromptEnabled,
} from "@/services/system-prompt-api";
import type { SystemPromptConfig, SystemPromptUpdateParam } from "@/types";

const promptTypeOptions = [
  { label: "System", value: "SYSTEM" },
  { label: "User", value: "USER" },
  { label: "Vision", value: "VISION" },
  { label: "Analysis", value: "ANALYSIS" },
];

const enabledOptions = [
  { label: "启用", value: "true" },
  { label: "停用", value: "false" },
];

interface PromptVariable extends PromptVariableOption {
  name: string;
  description?: string;
}

function parseVariables(variablesJson?: string): PromptVariable[] {
  if (!variablesJson) return [];
  try {
    const parsed = JSON.parse(variablesJson);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item) => {
        if (typeof item === "string") return { name: item };
        if (item && typeof item === "object" && typeof item.name === "string") {
          return {
            name: item.name,
            description: typeof item.description === "string" ? item.description : undefined,
          };
        }
        return null;
      })
      .filter((item): item is PromptVariable => !!item && item.name.trim().length > 0);
  } catch {
    return [];
  }
}

export default function SystemPromptManage() {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [items, setItems] = useState<SystemPromptConfig[]>([]);
  const [promptType, setPromptType] = useState<string | undefined>();
  const [enabledFilter, setEnabledFilter] = useState<string | undefined>();
  const [keyword, setKeyword] = useState("");
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [editing, setEditing] = useState<SystemPromptConfig | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<SystemPromptUpdateParam>();
  const editingVariables = useMemo(() => parseVariables(editing?.variablesJson), [editing?.variablesJson]);

  const enabled = useMemo(() => {
    if (enabledFilter === undefined) return undefined;
    return enabledFilter === "true";
  }, [enabledFilter]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listSystemPrompts({
        promptType,
        enabled,
        keyword: keyword.trim() || undefined,
        pageNum,
        pageSize,
      });
      setItems(page.list);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载系统提示词失败");
    } finally {
      setLoading(false);
    }
  }, [enabled, keyword, pageNum, pageSize, promptType]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openEdit = async (record: SystemPromptConfig) => {
    try {
      const detail = await getSystemPrompt(record.id);
      setEditing(detail);
      form.setFieldsValue({
        content: detail.content,
        enabled: detail.enabled,
        remark: detail.remark,
      });
      setModalOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载提示词详情失败");
    }
  };

  const submit = async () => {
    if (!editing) return;
    const values = await form.validateFields();
    setSaving(true);
    try {
      await updateSystemPrompt(editing.id, values);
      message.success("保存成功");
      setModalOpen(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<SystemPromptConfig> = [
    {
      title: "Key",
      dataIndex: "promptKey",
      width: 260,
      render: (value) => <code>{value}</code>,
    },
    {
      title: "名称",
      dataIndex: "promptName",
      width: 180,
    },
    {
      title: "类型",
      dataIndex: "promptType",
      width: 110,
      render: (value) => <Tag>{value}</Tag>,
    },
    {
      title: "版本",
      dataIndex: "version",
      width: 90,
      render: (value) => <Tag color="blue">v{value}</Tag>,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      width: 100,
      render: (value, record) => (
        <Switch
          size="small"
          checked={value}
          checkedChildren="启"
          unCheckedChildren="停"
          onChange={async (checked) => {
            try {
              await updateSystemPromptEnabled(record.id, checked);
              message.success("状态已更新");
              await loadData();
            } catch (error) {
              message.error(error instanceof Error ? error.message : "状态更新失败");
            }
          }}
        />
      ),
    },
    {
      title: "更新时间",
      dataIndex: "updateTime",
      width: 180,
    },
    {
      title: "备注",
      dataIndex: "remark",
      ellipsis: true,
    },
    {
      title: "操作",
      width: 190,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="恢复默认提示词？"
            description="当前内容会被默认资源覆盖，并生成新版本。"
            onConfirm={async () => {
              try {
                await restoreSystemPromptDefault(record.id);
                message.success("已恢复默认");
                await loadData();
              } catch (error) {
                message.error(error instanceof Error ? error.message : "恢复默认失败");
              }
            }}
          >
            <Button type="link" icon={<RollbackOutlined />}>
              默认
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 16, flexWrap: "wrap" }}>
        <h2 style={{ margin: 0 }}>系统提示词</h2>
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="Key / 名称 / 备注"
            style={{ width: 220 }}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={() => setPageNum(1)}
          />
          <Select
            allowClear
            placeholder="类型"
            style={{ width: 130 }}
            options={promptTypeOptions}
            value={promptType}
            onChange={(value) => {
              setPromptType(value);
              setPageNum(1);
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 110 }}
            options={enabledOptions}
            value={enabledFilter}
            onChange={(value) => {
              setEnabledFilter(value);
              setPageNum(1);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            刷新
          </Button>
        </Space>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={items}
        pagination={{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          pageSizeOptions: [10, 20, 50, 100],
          onChange: (nextPage, nextPageSize) => {
            setPageNum(nextPage);
            setPageSize(nextPageSize);
          },
        }}
      />

      <Modal
        title={editing ? `编辑提示词：${editing.promptKey}` : "编辑提示词"}
        open={modalOpen}
        width={920}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        destroyOnHidden
      >
        {editing && (
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="名称">{editing.promptName}</Descriptions.Item>
            <Descriptions.Item label="类型">{editing.promptType}</Descriptions.Item>
            <Descriptions.Item label="默认资源">{editing.defaultResourcePath || "无"}</Descriptions.Item>
            <Descriptions.Item label="建议模型">{editing.modelHint || "无"}</Descriptions.Item>
            <Descriptions.Item label="变量说明" span={2}>
              <Table
                size="small"
                rowKey="name"
                pagination={false}
                dataSource={editingVariables}
                locale={{ emptyText: "无可用变量" }}
                columns={[
                  {
                    title: "变量",
                    dataIndex: "name",
                    width: 220,
                    render: (value: string) => <Tag color="blue">{`{{${value}}}`}</Tag>,
                  },
                  {
                    title: "说明",
                    dataIndex: "description",
                    render: (value?: string) => value || "无",
                  },
                ]}
              />
            </Descriptions.Item>
          </Descriptions>
        )}
        <Form form={form} layout="vertical">
          <Form.Item name="content" label="提示词内容" rules={[{ required: true, message: "请输入提示词内容" }]}>
            <PromptVariableEditor variables={editingVariables} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
}
