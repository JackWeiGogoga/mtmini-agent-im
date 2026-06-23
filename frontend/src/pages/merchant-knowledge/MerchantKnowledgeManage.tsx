import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { useSearchParams } from "react-router-dom";
import PageContainer from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { getBoundShops } from "@/services/shop-manage-api";
import {
  clearMerchantReplyKnowledge,
  createMerchantReplyQa,
  deleteMerchantReplyChunk,
  deleteMerchantReplyDocument,
  deleteMerchantReplyQa,
  listMerchantReplyDocumentChunks,
  listMerchantReplyDocuments,
  listMerchantReplyQa,
  previewMerchantReplyDocument,
  updateMerchantReplyChunkEnabled,
  updateMerchantReplyChunkSentiment,
  updateMerchantReplyQa,
  uploadMerchantReplyDocument,
} from "@/services/comment-reply-knowledge-api";
import type {
  CommentReplyKnowledgeChunk,
  CommentReplyKnowledgeDocument,
  CommentReplyKnowledgeDocumentPreview,
  CommentReplyKnowledgeItem,
  CommentReplyQaKnowledgeParam,
  CommentReplySentiment,
  UserShopBinding,
} from "@/types";

const sentimentOptions: { label: string; value: Exclude<CommentReplySentiment, "UNKNOWN"> }[] = [
  { label: "好评（4-5星）", value: "GOOD" },
  { label: "中评（3星）", value: "NEUTRAL" },
  { label: "差评（0-2星）", value: "BAD" },
];

const chunkSentimentOptions: { label: string; value: CommentReplySentiment }[] = [
  ...sentimentOptions,
  { label: "通用", value: "UNKNOWN" },
];

const sentimentText: Record<string, string> = {
  GOOD: "好评",
  NEUTRAL: "中评",
  BAD: "差评",
  UNKNOWN: "通用",
};

const parseStatusText: Record<string, string> = {
  PENDING: "待解析",
  PARSING: "解析中",
  SUCCESS: "已学习",
  FAILED: "解析失败",
};

const parseStatusColor: Record<string, string> = {
  PENDING: "gold",
  PARSING: "processing",
  SUCCESS: "green",
  FAILED: "red",
};

const SHOP_SEARCH_DEBOUNCE_MS = 300;

function formatDateTime(value?: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}

function formatFileSize(value?: number) {
  if (!value) {
    return "-";
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function buildShopLabel(shop: UserShopBinding) {
  return `${shop.shopName || shop.poiId || shop.shopId}（${shop.poiId || shop.shopId}）`;
}

export default function MerchantKnowledgeManage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [qaForm] = Form.useForm<CommentReplyQaKnowledgeParam>();
  const [shops, setShops] = useState<UserShopBinding[]>([]);
  const [selectedShopId, setSelectedShopId] = useState<number | undefined>(() => {
    const rawShopId = Number(searchParams.get("shopId"));
    return Number.isFinite(rawShopId) && rawShopId > 0 ? rawShopId : undefined;
  });
  const [shopLoading, setShopLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [qaItems, setQaItems] = useState<CommentReplyKnowledgeItem[]>([]);
  const [documents, setDocuments] = useState<CommentReplyKnowledgeDocument[]>([]);
  const [qaModalOpen, setQaModalOpen] = useState(false);
  const [uploadPreviewOpen, setUploadPreviewOpen] = useState(false);
  const [previewingUpload, setPreviewingUpload] = useState(false);
  const [pendingUploadFile, setPendingUploadFile] = useState<File | null>(null);
  const [uploadPreview, setUploadPreview] = useState<CommentReplyKnowledgeDocumentPreview | null>(null);
  const [editingQa, setEditingQa] = useState<CommentReplyKnowledgeItem | null>(null);
  const [savingQa, setSavingQa] = useState(false);
  const [chunkDrawerOpen, setChunkDrawerOpen] = useState(false);
  const [selectedDocument, setSelectedDocument] = useState<CommentReplyKnowledgeDocument | null>(null);
  const [chunks, setChunks] = useState<CommentReplyKnowledgeChunk[]>([]);
  const [chunksLoading, setChunksLoading] = useState(false);
  const shopSearchTimer = useRef<number | null>(null);

  const selectedShop = useMemo(
    () => shops.find((shop) => shop.shopId === selectedShopId),
    [selectedShopId, shops],
  );

  const stats = useMemo(() => {
    const enabledQaCount = qaItems.filter((item) => item.enabled).length;
    const successDocumentCount = documents.filter((item) => item.parseStatus === "SUCCESS").length;
    const failedDocumentCount = documents.filter((item) => item.parseStatus === "FAILED").length;
    const chunkCount = documents.reduce((sum, item) => sum + (item.chunkCount || 0), 0);
    return { enabledQaCount, successDocumentCount, failedDocumentCount, chunkCount };
  }, [documents, qaItems]);

  const uploadPreviewSummary = useMemo(() => {
    const total = uploadPreview?.chunkCount || 0;
    const shown = uploadPreview?.chunks?.length || 0;
    if (total > shown && shown > 0) {
      return `共 ${total} 个片段，仅展示前 ${shown} 个`;
    }
    return `共 ${total} 个片段`;
  }, [uploadPreview]);

  const fetchShops = useCallback(
    async (keyword?: string) => {
      setShopLoading(true);
      try {
        const page = await getBoundShops({
          keyword,
          bindingDomain: "ALL",
          bindingStatus: "ALL",
          pageNum: 1,
          pageSize: 30,
        });
        const list = page.list || [];
        setShops((prev) => {
          const merged = new Map<number, UserShopBinding>();
          [...prev, ...list].forEach((shop) => merged.set(shop.shopId, shop));
          return Array.from(merged.values());
        });
        if (!selectedShopId && list.length > 0) {
          setSelectedShopId(list[0].shopId);
          setSearchParams({ shopId: String(list[0].shopId) });
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : "加载商户列表失败");
      } finally {
        setShopLoading(false);
      }
    },
    [selectedShopId, setSearchParams],
  );

  const loadKnowledge = useCallback(async () => {
    if (!selectedShopId) {
      setQaItems([]);
      setDocuments([]);
      return;
    }
    setLoading(true);
    try {
      const [qa, docs] = await Promise.all([
        listMerchantReplyQa(selectedShopId),
        listMerchantReplyDocuments(selectedShopId),
      ]);
      setQaItems(qa || []);
      setDocuments(docs || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载知识库失败");
    } finally {
      setLoading(false);
    }
  }, [selectedShopId]);

  useEffect(() => {
    void fetchShops();
  }, [fetchShops]);

  useEffect(() => {
    void loadKnowledge();
  }, [loadKnowledge]);

  const handleShopChange = (shopId: number) => {
    setSelectedShopId(shopId);
    setSearchParams({ shopId: String(shopId) });
  };

  const openCreateQa = () => {
    setEditingQa(null);
    qaForm.setFieldsValue({ sentiment: "GOOD", enabled: true } as CommentReplyQaKnowledgeParam);
    setQaModalOpen(true);
  };

  const openEditQa = (record: CommentReplyKnowledgeItem) => {
    setEditingQa(record);
    qaForm.setFieldsValue({
      title: record.title,
      sentiment: record.sentiment as Exclude<CommentReplySentiment, "UNKNOWN">,
      question: record.question || "",
      answer: record.answer,
      enabled: record.enabled,
    });
    setQaModalOpen(true);
  };

  const submitQa = async () => {
    if (!selectedShopId) {
      message.warning("请先选择商户");
      return;
    }
    const values = await qaForm.validateFields();
    setSavingQa(true);
    try {
      if (editingQa) {
        await updateMerchantReplyQa(selectedShopId, editingQa.id, values);
      } else {
        await createMerchantReplyQa(selectedShopId, values);
      }
      message.success("保存成功");
      setQaModalOpen(false);
      await loadKnowledge();
    } catch (error) {
      message.error(error instanceof Error ? error.message : "保存失败");
    } finally {
      setSavingQa(false);
    }
  };

  const handleUpload = async (file: File) => {
    if (!selectedShopId) {
      message.warning("请先选择商户");
      return false;
    }
    const lowerName = file.name.toLowerCase();
    if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".docx") && !lowerName.endsWith(".xlsx")) {
      message.error("仅支持 txt、docx、xlsx 文件");
      return false;
    }
    setPendingUploadFile(file);
    setUploadPreview(null);
    setUploadPreviewOpen(true);
    setPreviewingUpload(true);
    try {
      setUploadPreview(await previewMerchantReplyDocument(selectedShopId, file));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "解析预览失败");
    } finally {
      setPreviewingUpload(false);
    }
    return false;
  };

  const confirmPreviewUpload = async () => {
    if (!selectedShopId || !pendingUploadFile || !uploadPreview?.chunks?.length) {
      message.warning("请先完成解析预览");
      return;
    }
    setUploading(true);
    try {
      const uploaded = await uploadMerchantReplyDocument(selectedShopId, pendingUploadFile);
      message.success("上传成功，正在解析");
      setUploadPreviewOpen(false);
      setPendingUploadFile(null);
      setUploadPreview(null);
      await loadKnowledge();
      void pollDocumentParse(uploaded.id);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "上传失败");
    } finally {
      setUploading(false);
    }
  };

  const pollDocumentParse = async (documentId: number) => {
    if (!selectedShopId) {
      return;
    }
    for (let index = 0; index < 8; index += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      const docs = await listMerchantReplyDocuments(selectedShopId);
      setDocuments(docs || []);
      const latest = docs.find((doc) => doc.id === documentId);
      if (latest?.parseStatus === "SUCCESS") {
        message.success(`已学习 ${latest.chunkCount || 0} 条片段`);
        return;
      }
      if (latest?.parseStatus === "FAILED") {
        message.error(latest.parseError || "文档解析失败");
        return;
      }
    }
    message.info("文档仍在解析，可稍后刷新查看");
  };

  const openChunkDrawer = async (record: CommentReplyKnowledgeDocument) => {
    if (!selectedShopId) {
      return;
    }
    setSelectedDocument(record);
    setChunkDrawerOpen(true);
    setChunksLoading(true);
    try {
      const list = await listMerchantReplyDocumentChunks(selectedShopId, record.id);
      setChunks(list || []);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载片段失败");
    } finally {
      setChunksLoading(false);
    }
  };

  const reloadChunks = async () => {
    if (selectedShopId && selectedDocument) {
      await openChunkDrawer(selectedDocument);
      await loadKnowledge();
    }
  };

  const qaColumns: ColumnsType<CommentReplyKnowledgeItem> = [
    {
      title: "标题",
      dataIndex: "title",
      width: 180,
    },
    {
      title: "评论类型",
      dataIndex: "sentiment",
      width: 110,
      render: (value) => <Tag>{sentimentText[value] || value}</Tag>,
    },
    {
      title: "问题",
      dataIndex: "question",
      ellipsis: true,
    },
    {
      title: "回答",
      dataIndex: "answer",
      ellipsis: true,
    },
    {
      title: "状态",
      dataIndex: "enabled",
      width: 90,
      render: (enabled) => <Tag color={enabled ? "green" : "default"}>{enabled ? "启用" : "停用"}</Tag>,
    },
    {
      title: "更新时间",
      dataIndex: "updateTime",
      width: 160,
      render: formatDateTime,
    },
    {
      title: "操作",
      width: 150,
      render: (_, record) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => openEditQa(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除这条问答知识？"
            onConfirm={async () => {
              if (!selectedShopId) return;
              await deleteMerchantReplyQa(selectedShopId, record.id);
              message.success("删除成功");
              await loadKnowledge();
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

  const documentColumns: ColumnsType<CommentReplyKnowledgeDocument> = [
    {
      title: "文件名",
      dataIndex: "fileName",
      ellipsis: true,
      render: (value, record) => (
        <Button type="link" icon={<FileTextOutlined />} onClick={() => void openChunkDrawer(record)}>
          {value || "文档"}
        </Button>
      ),
    },
    {
      title: "格式",
      dataIndex: "fileType",
      width: 90,
    },
    {
      title: "大小",
      dataIndex: "fileSize",
      width: 100,
      render: formatFileSize,
    },
    {
      title: "解析状态",
      dataIndex: "parseStatus",
      width: 120,
      render: (value) => <Tag color={parseStatusColor[value] || "default"}>{parseStatusText[value] || value}</Tag>,
    },
    {
      title: "片段数",
      dataIndex: "chunkCount",
      width: 90,
      render: (value) => value || 0,
    },
    {
      title: "失败原因",
      dataIndex: "parseError",
      ellipsis: true,
      render: (value) => (value ? <Tooltip title={value}>{value}</Tooltip> : "-"),
    },
    {
      title: "上传时间",
      dataIndex: "createTime",
      width: 160,
      render: formatDateTime,
    },
    {
      title: "操作",
      width: 150,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => void openChunkDrawer(record)}>
            详情
          </Button>
          <Popconfirm
            title="确认删除这个文档？"
            description="删除后会同步删除解析片段和 COS 文件。"
            onConfirm={async () => {
              if (!selectedShopId) return;
              await deleteMerchantReplyDocument(selectedShopId, record.id);
              message.success("删除成功");
              await loadKnowledge();
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

  const chunkColumns: ColumnsType<CommentReplyKnowledgeChunk> = [
    {
      title: "类型",
      dataIndex: "sentiment",
      width: 130,
      render: (value, record) => (
        <Select
          size="small"
          style={{ width: 100 }}
          value={value}
          options={chunkSentimentOptions}
          onChange={async (nextValue) => {
            if (!selectedShopId) return;
            await updateMerchantReplyChunkSentiment(selectedShopId, record.id, nextValue);
            message.success("类型已更新");
            await reloadChunks();
          }}
        />
      ),
    },
    {
      title: "片段内容",
      dataIndex: "chunkText",
      render: (value) => <div style={{ whiteSpace: "pre-wrap" }}>{value}</div>,
    },
    {
      title: "启用",
      dataIndex: "enabled",
      width: 90,
      render: (enabled, record) => (
        <Switch
          size="small"
          checked={enabled}
          onChange={async (checked) => {
            if (!selectedShopId) return;
            await updateMerchantReplyChunkEnabled(selectedShopId, record.id, checked);
            message.success("状态已更新");
            await reloadChunks();
          }}
        />
      ),
    },
    {
      title: "操作",
      width: 90,
      render: (_, record) => (
        <Popconfirm
          title="确认删除这个片段？"
          onConfirm={async () => {
            if (!selectedShopId) return;
            await deleteMerchantReplyChunk(selectedShopId, record.id);
            message.success("删除成功");
            await reloadChunks();
          }}
        >
          <Button type="link" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <PageContainer title="知识库管理">
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Space wrap style={{ width: "100%", justifyContent: "space-between" }}>
              <Space wrap>
                <Select
                  showSearch
                  filterOption={false}
                  loading={shopLoading}
                  placeholder="选择商户"
                  style={{ minWidth: 320 }}
                  value={selectedShopId}
                  options={shops.map((shop) => ({
                    label: buildShopLabel(shop),
                    value: shop.shopId,
                  }))}
                  onChange={handleShopChange}
                  onSearch={(value) => {
                    if (shopSearchTimer.current) {
                      window.clearTimeout(shopSearchTimer.current);
                    }
                    shopSearchTimer.current = window.setTimeout(() => {
                      void fetchShops(value);
                    }, SHOP_SEARCH_DEBOUNCE_MS);
                  }}
                />
                <Button icon={<ReloadOutlined />} onClick={() => void loadKnowledge()} disabled={!selectedShopId}>
                  刷新知识库
                </Button>
              </Space>
              <Popconfirm
                title="确认清空该商户所有知识？"
                description="会删除手工问答、上传文档和解析片段。"
                onConfirm={async () => {
                  if (!selectedShopId) return;
                  await clearMerchantReplyKnowledge(selectedShopId);
                  message.success("已清空");
                  await loadKnowledge();
                }}
                disabled={!selectedShopId}
              >
                <Button danger disabled={!selectedShopId}>
                  清空知识库
                </Button>
              </Popconfirm>
            </Space>
            {selectedShop ? (
              <Descriptions size="small" column={3}>
                <Descriptions.Item label="商户">{selectedShop.shopName || "-"}</Descriptions.Item>
                <Descriptions.Item label="门店ID">{selectedShop.poiId || "-"}</Descriptions.Item>
                <Descriptions.Item label="绑定用户">{selectedShop.boundUserName || selectedShop.boundUserId || "-"}</Descriptions.Item>
              </Descriptions>
            ) : null}
          </Space>
        </Card>

        <Space size={16} wrap>
          <Card style={{ width: 180 }}>
            <Statistic title="手工问答" value={qaItems.length} suffix={`/ ${stats.enabledQaCount} 启用`} />
          </Card>
          <Card style={{ width: 180 }}>
            <Statistic title="上传文档" value={documents.length} suffix={`/ ${stats.successDocumentCount} 已学习`} />
          </Card>
          <Card style={{ width: 180 }}>
            <Statistic title="解析片段" value={stats.chunkCount} />
          </Card>
          <Card style={{ width: 180 }}>
            <Statistic title="解析失败" value={stats.failedDocumentCount} valueStyle={{ color: stats.failedDocumentCount ? "#cf1322" : undefined }} />
          </Card>
        </Space>

        <Card>
          {selectedShopId ? (
            <Tabs
              items={[
                {
                  key: "qa",
                  label: "手工问答",
                  children: (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      <Space style={{ justifyContent: "space-between", width: "100%" }}>
                        <span>最多 500 条，适合确定的顾客问题和回复话术。</span>
                        <Button type="primary" icon={<PlusOutlined />} onClick={openCreateQa}>
                          新增问答
                        </Button>
                      </Space>
                      <Table
                        rowKey="id"
                        loading={loading}
                        columns={qaColumns}
                        dataSource={qaItems}
                        pagination={{ pageSize: 10, showSizeChanger: true }}
                      />
                    </Space>
                  ),
                },
                {
                  key: "documents",
                  label: "上传文档",
                  children: (
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                      <Space style={{ justifyContent: "space-between", width: "100%" }}>
                        <span>支持 txt、docx、xlsx，每个商户最多 10 个文档，单个最大 20M。</span>
                        <Upload accept=".txt,.docx,.xlsx" showUploadList={false} beforeUpload={handleUpload}>
                          <Button type="primary" icon={<UploadOutlined />} loading={uploading}>
                            上传文档
                          </Button>
                        </Upload>
                      </Space>
                      <Table
                        rowKey="id"
                        loading={loading}
                        columns={documentColumns}
                        dataSource={documents}
                        pagination={{ pageSize: 10, showSizeChanger: true }}
                      />
                    </Space>
                  ),
                },
              ]}
            />
          ) : (
            <Empty description="请先选择一个商户" />
          )}
        </Card>
      </Space>

      <Modal
        title={editingQa ? "编辑问答知识" : "新增问答知识"}
        open={qaModalOpen}
        onCancel={() => setQaModalOpen(false)}
        onOk={submitQa}
        confirmLoading={savingQa}
        destroyOnHidden
      >
        <Form form={qaForm} layout="vertical" initialValues={{ sentiment: "GOOD", enabled: true }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: "请输入标题" }]}>
            <Input maxLength={10} showCount />
          </Form.Item>
          <Form.Item name="sentiment" label="评论类型" rules={[{ required: true, message: "请选择评论类型" }]}>
            <Select options={sentimentOptions} />
          </Form.Item>
          <Form.Item name="question" label="问题" rules={[{ required: true, message: "请输入问题" }]}>
            <Input.TextArea rows={4} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="answer" label="回答" rules={[{ required: true, message: "请输入回答" }]}>
            <Input.TextArea rows={4} maxLength={300} showCount />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="解析预览"
        open={uploadPreviewOpen}
        onCancel={() => {
          if (!uploading) {
            setUploadPreviewOpen(false);
            setPendingUploadFile(null);
            setUploadPreview(null);
          }
        }}
        onOk={() => void confirmPreviewUpload()}
        okText="确认上传"
        okButtonProps={{ disabled: previewingUpload || !uploadPreview?.chunks?.length }}
        confirmLoading={uploading}
        width={760}
        destroyOnHidden
      >
        <Space direction="vertical" size={12} style={{ width: "100%" }}>
          <Descriptions size="small" column={3} bordered>
            <Descriptions.Item label="文件名">{uploadPreview?.fileName || pendingUploadFile?.name || "-"}</Descriptions.Item>
            <Descriptions.Item label="格式">{uploadPreview?.fileType || "-"}</Descriptions.Item>
            <Descriptions.Item label="大小">{formatFileSize(uploadPreview?.fileSize || pendingUploadFile?.size)}</Descriptions.Item>
            <Descriptions.Item label="片段数">{uploadPreviewSummary}</Descriptions.Item>
          </Descriptions>
          <Table
            rowKey="index"
            loading={previewingUpload}
            columns={[
              {
                title: "片段",
                dataIndex: "index",
                width: 80,
              },
              {
                title: "类型",
                dataIndex: "sentiment",
                width: 100,
                render: (value: string) => <Tag>{sentimentText[value] || value}</Tag>,
              },
              {
                title: "内容",
                dataIndex: "chunkText",
                render: (value: string) => <div style={{ whiteSpace: "pre-wrap" }}>{value}</div>,
              },
            ]}
            dataSource={uploadPreview?.chunks || []}
            pagination={{ pageSize: 5, showSizeChanger: false }}
          />
        </Space>
      </Modal>

      <Drawer
        title={selectedDocument?.fileName || "文档详情"}
        width={760}
        open={chunkDrawerOpen}
        onClose={() => setChunkDrawerOpen(false)}
        extra={
          <Button icon={<ReloadOutlined />} onClick={() => void reloadChunks()}>
            刷新
          </Button>
        }
      >
        {selectedDocument ? (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Descriptions size="small" column={2} bordered>
              <Descriptions.Item label="解析状态">
                <Tag color={parseStatusColor[selectedDocument.parseStatus] || "default"}>
                  {parseStatusText[selectedDocument.parseStatus] || selectedDocument.parseStatus}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="片段数">{selectedDocument.chunkCount || 0}</Descriptions.Item>
              <Descriptions.Item label="文件类型">{selectedDocument.fileType || "-"}</Descriptions.Item>
              <Descriptions.Item label="文件大小">{formatFileSize(selectedDocument.fileSize)}</Descriptions.Item>
              <Descriptions.Item label="上传时间">{formatDateTime(selectedDocument.createTime)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatDateTime(selectedDocument.updateTime)}</Descriptions.Item>
              {selectedDocument.parseError ? (
                <Descriptions.Item label="失败原因" span={2}>
                  {selectedDocument.parseError}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
            <Table
              rowKey="id"
              loading={chunksLoading}
              columns={chunkColumns}
              dataSource={chunks}
              pagination={{ pageSize: 6, showSizeChanger: false }}
            />
          </Space>
        ) : null}
      </Drawer>
    </PageContainer>
  );
}
