import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EditOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from "@ant-design/icons";
import { message } from "@/components/AppMessageContext";
import PageContainer from "@/components/PageContainer";
import {
  createOfficialReplyKnowledge,
  deleteOfficialReplyKnowledge,
  importOfficialReplyKnowledge,
  listOfficialReplyKnowledge,
  updateOfficialReplyKnowledge,
} from "@/services/comment-reply-knowledge-api";
import type {
  AdminCommentReplyOfficialKnowledgeParam,
  CommentReplyKnowledgeItem,
  CommentReplySentiment,
} from "@/types";

const sentimentOptions = [
  { label: "好评回复", value: "GOOD" },
  { label: "中评回复", value: "NEUTRAL" },
  { label: "差评回复", value: "BAD" },
];

const sentimentText: Record<string, string> = {
  GOOD: "好评",
  NEUTRAL: "中评",
  BAD: "差评",
  UNKNOWN: "未知",
};

export default function CommentReplyKnowledgeManage() {
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [items, setItems] = useState<CommentReplyKnowledgeItem[]>([]);
  const [sentiment, setSentiment] = useState<CommentReplySentiment | undefined>();
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CommentReplyKnowledgeItem | null>(null);
  const [form] = Form.useForm<AdminCommentReplyOfficialKnowledgeParam>();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listOfficialReplyKnowledge(sentiment, pageNum, pageSize);
      setItems(page.list);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载官方文案失败");
    } finally {
      setLoading(false);
    }
  }, [pageNum, pageSize, sentiment]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue({ sentiment: "GOOD", enabled: true } as AdminCommentReplyOfficialKnowledgeParam);
    setModalOpen(true);
  };

  const openEdit = (record: CommentReplyKnowledgeItem) => {
    setEditing(record);
    form.setFieldsValue({
      title: record.title,
      sentiment: record.sentiment as Exclude<CommentReplySentiment, "UNKNOWN">,
      answer: record.answer,
      enabled: record.enabled,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await updateOfficialReplyKnowledge(editing.id, values);
      } else {
        await createOfficialReplyKnowledge(values);
      }
      message.success("保存成功");
      setModalOpen(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "保存失败");
    }
  };

  const handleImportExcel = async (file: File) => {
    const filename = file.name.toLowerCase();
    if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
      message.error("仅支持 xlsx 或 xls 格式的 Excel 文件");
      return false;
    }
    setImporting(true);
    try {
      const result = await importOfficialReplyKnowledge(file);
      message.success(
        `导入完成：新增 ${result.createdCount} 条，更新 ${result.updatedCount} 条，文件内重复 ${result.duplicateCount} 条`,
      );
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "导入失败");
    } finally {
      setImporting(false);
    }
    return false;
  };

  const columns: ColumnsType<CommentReplyKnowledgeItem> = [
    {
      title: "标题",
      dataIndex: "title",
      width: 220,
    },
    {
      title: "评论类型",
      dataIndex: "sentiment",
      width: 120,
      render: (value) => <Tag>{sentimentText[value] || value}</Tag>,
    },
    {
      title: "回复内容",
      dataIndex: "answer",
      ellipsis: true,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      width: 100,
      render: (enabled) => <Tag color={enabled ? "green" : "default"}>{enabled ? "启用" : "停用"}</Tag>,
    },
    {
      title: "操作",
      width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除这条官方文案？"
            onConfirm={async () => {
              await deleteOfficialReplyKnowledge(record.id);
              message.success("删除成功");
              await loadData();
            }}
          >
            <Button type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, marginBottom: 16, flexWrap: "wrap" }}>
        <h2 style={{ margin: 0 }}>官方回复文案</h2>
        <Space wrap>
          <Select
            allowClear
            placeholder="评论类型"
            style={{ width: 140 }}
            options={sentimentOptions}
            value={sentiment}
            onChange={(value) => {
              setSentiment(value);
              setPageNum(1);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={loadData}>
            刷新
          </Button>
          <Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={handleImportExcel}>
            <Button icon={<UploadOutlined />} loading={importing}>
              上传 Excel
            </Button>
          </Upload>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            新增文案
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
        title={editing ? "编辑官方文案" : "新增官方文案"}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" initialValues={{ sentiment: "GOOD", enabled: true }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: "请输入标题" }]}>
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item name="sentiment" label="评论类型" rules={[{ required: true, message: "请选择评论类型" }]}>
            <Select options={sentimentOptions} />
          </Form.Item>
          <Form.Item name="answer" label="回复内容" rules={[{ required: true, message: "请输入回复内容" }]}>
            <Input.TextArea rows={5} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
}
