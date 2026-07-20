import { useCallback, useEffect, useState } from "react";
import {
  Button,
  Card,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from "@ant-design/icons";
import PageContainer from "@/components/PageContainer";
import { message, modal } from "@/components/AppMessageContext";
import { useAuth } from "@/contexts/AuthContext";
import {
  clearKnowledge,
  createKnowledgeQa,
  deleteKnowledgeChunk,
  deleteKnowledgeDocument,
  deleteKnowledgeQa,
  listKnowledgeChunks,
  listKnowledgeDocuments,
  listKnowledgeQa,
  previewKnowledgeDocument,
  updateKnowledgeChunkEnabled,
  updateKnowledgeQa,
  uploadKnowledgeDocument,
} from "@/services/knowledge-api";
import { getBoundShops } from "@/services/shop-manage-api";
import type { KnowledgeChunk, KnowledgeDocument, KnowledgeItem, KnowledgeQaParam, UserShopBinding } from "@/types";
import TestChatPanel from "./TestChatPanel";

const parseStatusText: Record<string, string> = {
  PENDING: "待解析",
  PARSING: "解析中",
  SUCCESS: "已学习",
  FAILED: "解析失败",
};

const KNOWLEDGE_CATEGORY_OPTIONS = [
  { value: "GENERAL", label: "普通类" },
  { value: "DELIVERY", label: "配送类" },
  { value: "AFTER_SALE", label: "售后类" },
  { value: "OTHER", label: "其他" },
];

const knowledgeCategoryText: Record<string, string> = Object.fromEntries(
  KNOWLEDGE_CATEGORY_OPTIONS.map((option) => [option.value, option.label]),
);

export default function MerchantKnowledgeManage() {
  const { hasPermission } = useAuth();
  const [form] = Form.useForm<KnowledgeQaParam>();
  const [shops, setShops] = useState<UserShopBinding[]>([]);
  const [shopId, setShopId] = useState<number>();
  const [items, setItems] = useState<KnowledgeItem[]>([]);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [chunks, setChunks] = useState<KnowledgeChunk[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [editing, setEditing] = useState<KnowledgeItem>();
  const [formOpen, setFormOpen] = useState(false);
  const [chunkDocument, setChunkDocument] = useState<KnowledgeDocument>();

  const loadShops = useCallback(async () => {
    const page = await getBoundShops({ pageNum: 1, pageSize: 200, bindingStatus: "BOUND" });
    setShops(page.list || []);
    setShopId((current) => current || page.list?.[0]?.shopId);
  }, []);

  const refresh = useCallback(async () => {
    if (!shopId) return;
    setLoading(true);
    try {
      const [qa, docs] = await Promise.all([listKnowledgeQa(shopId), listKnowledgeDocuments(shopId)]);
      setItems(qa);
      setDocuments(docs);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "知识库加载失败");
    } finally {
      setLoading(false);
    }
  }, [shopId]);

  useEffect(() => { void loadShops(); }, [loadShops]);
  useEffect(() => { void refresh(); }, [refresh]);

  const submitQa = async () => {
    if (!shopId) return;
    const values = await form.validateFields();
    if (editing) await updateKnowledgeQa(shopId, editing.id, values);
    else await createKnowledgeQa(shopId, values);
    setFormOpen(false);
    setEditing(undefined);
    form.resetFields();
    message.success("知识问答已保存");
    await refresh();
  };

  const selectUpload = async (file: File) => {
    if (!shopId) return false;
    setUploading(true);
    try {
      const preview = await previewKnowledgeDocument(shopId, file);
      modal.confirm({
        title: "确认上传知识文档",
        content: `“${preview.fileName}”将生成 ${preview.chunkCount} 个知识片段。`,
        okText: "上传并解析",
        onOk: async () => {
          await uploadKnowledgeDocument(shopId, file);
          message.success("文档已上传，正在异步解析");
          await refresh();
        },
      });
    } catch (error) {
      message.error(error instanceof Error ? error.message : "文档预览失败");
    } finally {
      setUploading(false);
    }
    return false;
  };

  const openChunks = async (document: KnowledgeDocument) => {
    if (!shopId) return;
    setChunkDocument(document);
    setChunks(await listKnowledgeChunks(shopId, document.id));
  };

  const qaColumns: ColumnsType<KnowledgeItem> = [
    { title: "标题", dataIndex: "title", width: 180 },
    { title: "分类", dataIndex: "category", width: 100, render: (value) => <Tag>{knowledgeCategoryText[value] || "普通类"}</Tag> },
    { title: "问题", dataIndex: "question" },
    { title: "答案", dataIndex: "answer" },
    { title: "状态", dataIndex: "enabled", width: 90, render: (value) => <Tag color={value ? "green" : "default"}>{value ? "启用" : "停用"}</Tag> },
    {
      title: "操作", width: 150, render: (_, record) => (
        <Space>
          {hasPermission("knowledge:edit") && <Button type="link" icon={<EditOutlined />} onClick={() => { setEditing(record); form.setFieldsValue(record); setFormOpen(true); }}>编辑</Button>}
          {hasPermission("knowledge:delete") && <Popconfirm title="确认删除这条知识问答？" onConfirm={async () => { if (shopId) { await deleteKnowledgeQa(shopId, record.id); await refresh(); } }}><Button type="link" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm>}
        </Space>
      ),
    },
  ];

  const documentColumns: ColumnsType<KnowledgeDocument> = [
    { title: "文件名", dataIndex: "fileName" },
    { title: "格式", dataIndex: "fileType", width: 90 },
    { title: "解析状态", dataIndex: "parseStatus", width: 110, render: (value) => <Tag color={value === "SUCCESS" ? "green" : value === "FAILED" ? "red" : "processing"}>{parseStatusText[value] || value}</Tag> },
    { title: "片段数", dataIndex: "chunkCount", width: 90 },
    { title: "失败摘要", dataIndex: "parseError" },
    {
      title: "操作", width: 170, render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => void openChunks(record)}>查看片段</Button>
          {hasPermission("knowledge:delete") && <Popconfirm title="确认删除文档？" onConfirm={async () => { if (shopId) { await deleteKnowledgeDocument(shopId, record.id); await refresh(); } }}><Button type="link" danger>删除</Button></Popconfirm>}
        </Space>
      ),
    },
  ];

  return (
    <PageContainer title="门店知识库">
      <Card>
        <Space wrap style={{ marginBottom: 16 }}>
          <Select
            showSearch
            style={{ width: 360 }}
            placeholder="选择门店"
            value={shopId}
            optionFilterProp="label"
            onChange={setShopId}
            options={shops.map((shop) => ({ value: shop.shopId, label: `${shop.shopName || shop.poiId}（${shop.poiId}）` }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新</Button>
        </Space>
        {!shopId ? <Empty description="请先选择门店" /> : (
          <Tabs items={[
            {
              key: "qa",
              label: `知识问答（${items.length}）`,
              children: <>
                <Space style={{ marginBottom: 12 }}>
                  {hasPermission("knowledge:add") && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(undefined); form.resetFields(); form.setFieldsValue({ enabled: true, category: "GENERAL" }); setFormOpen(true); }}>新增问答</Button>}
                  {hasPermission("knowledge:delete") && <Popconfirm title="确认清空当前门店全部知识？" onConfirm={async () => { await clearKnowledge(shopId); await refresh(); }}><Button danger>清空知识库</Button></Popconfirm>}
                </Space>
                <Table rowKey="id" loading={loading} columns={qaColumns} dataSource={items} pagination={false} />
              </>,
            },
            {
              key: "documents",
              label: `知识文档（${documents.length}）`,
              children: <>
                {hasPermission("knowledge:add") && <Upload showUploadList={false} beforeUpload={selectUpload}><Button style={{ marginBottom: 12 }} loading={uploading} icon={<UploadOutlined />}>上传文档</Button></Upload>}
                <Table rowKey="id" loading={loading} columns={documentColumns} dataSource={documents} pagination={false} />
              </>,
            },
            {
              key: "test-chat",
              label: "测试聊天",
              children: <TestChatPanel shopId={shopId} />,
            },
          ]} />
        )}
      </Card>

      <Modal title={editing ? "编辑知识问答" : "新增知识问答"} open={formOpen} onCancel={() => setFormOpen(false)} onOk={() => void submitQa()} destroyOnHidden>
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}><Input maxLength={100} /></Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: "请选择分类" }]}>
            <Select options={KNOWLEDGE_CATEGORY_OPTIONS} placeholder="选择知识分类" />
          </Form.Item>
          <Form.Item name="question" label="问题" rules={[{ required: true }]}><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="answer" label="答案" rules={[{ required: true }]}><Input.TextArea rows={5} /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Modal>

      <Drawer title={chunkDocument?.fileName || "知识片段"} open={Boolean(chunkDocument)} width={720} onClose={() => { setChunkDocument(undefined); setChunks([]); }}>
        <Table<KnowledgeChunk>
          rowKey="id"
          pagination={false}
          dataSource={chunks}
          columns={[
            { title: "片段内容", dataIndex: "chunkText" },
            { title: "启用", dataIndex: "enabled", width: 80, render: (enabled, record) => <Switch checked={enabled} disabled={!hasPermission("knowledge:edit")} onChange={async (checked) => { if (shopId) { await updateKnowledgeChunkEnabled(shopId, record.id, checked); await openChunks(chunkDocument!); } }} /> },
            { title: "操作", width: 80, render: (_, record) => hasPermission("knowledge:delete") ? <Popconfirm title="确认删除片段？" onConfirm={async () => { if (shopId) { await deleteKnowledgeChunk(shopId, record.id); await openChunks(chunkDocument!); } }}><Button type="link" danger>删除</Button></Popconfirm> : null },
          ]}
        />
      </Drawer>
    </PageContainer>
  );
}
