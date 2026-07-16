import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import type { DescriptionsProps } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { Dayjs } from "dayjs";
import { RedoOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import PageContainer from "@/components/PageContainer";
import { message, modal } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import {
  getCommentProcessingLog,
  listCommentProcessingLogs,
  retryCommentProcessing,
} from "@/services/comment-processing-log-api";
import type { CommentProcessingRetryType } from "@/services/comment-processing-log-api";
import type {
  CommentProcessingLogQuery,
  CommentProcessingLogRecord,
} from "@/types";

const { RangePicker } = DatePicker;
const { Paragraph, Text, Title } = Typography;

interface FilterValues {
  dateRange?: [Dayjs, Dayjs];
  shopId?: number;
  shopKeyword?: string;
  commentId?: number;
  keyword?: string;
  sentiment?: string;
  autoReplyStatus?: string;
  complaintStatus?: string;
  imFollowUpStatus?: string;
}

const sentimentOptions = [
  { label: "好评", value: "GOOD" },
  { label: "中评", value: "NEUTRAL" },
  { label: "差评", value: "BAD" },
  { label: "未知", value: "UNKNOWN" },
];

const commonStatusOptions = [
  { label: "待处理", value: "PENDING" },
  { label: "处理中/已生成", value: "GENERATED" },
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" },
  { label: "已跳过", value: "SKIPPED" },
];

const postStatusOptions = [
  { label: "未触发", value: "NOT_TRIGGERED" },
  { label: "不适用", value: "NOT_APPLICABLE" },
  ...commonStatusOptions,
  { label: "已建群", value: "GROUP_CREATED" },
];

const statusMeta: Record<string, { label: string; color?: string }> = {
  NOT_TRIGGERED: { label: "未触发" },
  NOT_APPLICABLE: { label: "不适用" },
  PENDING: { label: "待处理", color: "processing" },
  GENERATED: { label: "已生成", color: "cyan" },
  GROUP_CREATED: { label: "已建群", color: "blue" },
  SUCCESS: { label: "成功", color: "success" },
  FAILED: { label: "失败", color: "error" },
  SKIPPED: { label: "已跳过", color: "default" },
};

const sentimentMeta: Record<string, { label: string; color: string }> = {
  GOOD: { label: "好评", color: "green" },
  NEUTRAL: { label: "中评", color: "orange" },
  BAD: { label: "差评", color: "red" },
  UNKNOWN: { label: "未评级", color: "default" },
};

const retryMeta: Record<CommentProcessingRetryType, { label: string; success: string }> = {
  "auto-reply": { label: "自动回复", success: "自动回复已重新入队" },
  complaint: { label: "差评申诉", success: "差评申诉已重新入队" },
  "im-follow-up": { label: "群聊跟进", success: "群聊跟进已重新入队" },
};

function statusTag(status?: string) {
  const normalized = status || "NOT_TRIGGERED";
  const meta = statusMeta[normalized] || { label: normalized };
  return <Tag color={meta.color}>{meta.label}</Tag>;
}

function sentimentTag(sentiment?: string) {
  const normalized = sentiment || "UNKNOWN";
  const meta = sentimentMeta[normalized] || sentimentMeta.UNKNOWN;
  return <Tag color={meta.color}>{meta.label}</Tag>;
}

function bizLineTag(bizCode?: number) {
  if (bizCode === 1) {
    return <Tag color="blue">外卖（1）</Tag>;
  }
  if (bizCode === 4) {
    return <Tag color="purple">拼好饭（4）</Tag>;
  }
  return <Tag>{bizCode === undefined || bizCode === null ? "未知业务线" : `其他（${bizCode}）`}</Tag>;
}

function validityTag(valid?: number) {
  if (valid === 1) {
    return <Tag color="green">有效</Tag>;
  }
  if (valid === 0) {
    return <Tag color="red">无效</Tag>;
  }
  return <Tag>有效性未知</Tag>;
}

function sensitiveTypeTag(commentType?: number) {
  if (commentType === 4) {
    return <Tag color="green">展示·计分（4）</Tag>;
  }
  if (commentType === 5) {
    return <Tag color="orange">屏蔽·计分（5）</Tag>;
  }
  if (commentType === 10) {
    return <Tag color="blue">展示·不计分（10）</Tag>;
  }
  return <Tag color="red">不处理（{commentType ?? "未知"}）</Tag>;
}

function isProcessableCommentType(commentType?: number) {
  return commentType === 4 || commentType === 5 || commentType === 10;
}

function text(value?: string | number | null) {
  return value === undefined || value === null || value === "" ? "—" : String(value);
}

function errorText(value?: string) {
  return value ? <Text type="danger">{value}</Text> : "—";
}

export default function CommentProcessingLog() {
  const [form] = Form.useForm<FilterValues>();
  const [query, setQuery] = useState<CommentProcessingLogQuery>({
    pageNum: 1,
    pageSize: 20,
  });
  const [records, setRecords] = useState<CommentProcessingLogRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<CommentProcessingLogRecord>();
  const [retryingKey, setRetryingKey] = useState<string>();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listCommentProcessingLogs(query);
      setRecords(page.list || []);
      setTotal(page.total || 0);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载评价处理日志失败");
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openDetail = useCallback(async (record: CommentProcessingLogRecord) => {
    setDetailOpen(true);
    setDetail(undefined);
    setDetailLoading(true);
    try {
      setDetail(await getCommentProcessingLog(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "加载处理详情失败");
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const confirmRetry = useCallback((
    record: CommentProcessingLogRecord,
    retryType: CommentProcessingRetryType,
  ) => {
    const meta = retryMeta[retryType];
    const key = `${record.id}:${retryType}`;
    modal.confirm({
      title: `确认重试${meta.label}？`,
      content: retryType === "im-follow-up"
        ? "请先确认美团侧门店 IM 开关或上次失败原因已处理。重试可能再次发起建群或消息发送。"
        : "任务会立即重新进入异步队列，请确认失败原因已处理。",
      okText: "确认重试",
      cancelText: "取消",
      onOk: async () => {
        setRetryingKey(key);
        try {
          await retryCommentProcessing(record.id, retryType);
          message.success(meta.success);
          await loadData();
          if (detailOpen && detail?.id === record.id) {
            setDetail(await getCommentProcessingLog(record.id));
          }
        } catch (error) {
          message.error(error instanceof Error ? error.message : `${meta.label}重试失败`);
          throw error;
        } finally {
          setRetryingKey(undefined);
        }
      },
    });
  }, [detail?.id, detailOpen, loadData]);

  const columns = useMemo<ColumnsType<CommentProcessingLogRecord>>(
    () => [
      {
        title: "门店",
        width: 190,
        fixed: "left",
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Text strong>{text(record.shopName)}</Text>
            <Text type="secondary">ID {record.shopId} · {text(record.epoiId)}</Text>
          </Space>
        ),
      },
      {
        title: "评价",
        width: 300,
        render: (_, record) => (
          <Space direction="vertical" size={2} style={{ width: "100%" }}>
            <Space>{sentimentTag(record.sentiment)}<Text type="secondary">#{record.commentId}</Text></Space>
            <Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>
              {text(record.commentContent)}
            </Paragraph>
          </Space>
        ),
      },
      {
        title: "业务线",
        dataIndex: "bizCode",
        width: 120,
        render: (value) => bizLineTag(value),
      },
      {
        title: "有效性",
        dataIndex: "valid",
        width: 100,
        render: (value) => validityTag(value),
      },
      {
        title: "敏感类型",
        dataIndex: "commentType",
        width: 170,
        render: (value) => sensitiveTypeTag(value),
      },
      {
        title: "自动回复",
        dataIndex: "autoReplyStatus",
        width: 130,
        render: (value) => statusTag(value),
      },
      {
        title: "差评申诉",
        dataIndex: "complaintStatus",
        width: 130,
        render: (value) => statusTag(value),
      },
      {
        title: "群聊跟进",
        dataIndex: "imFollowUpStatus",
        width: 130,
        render: (value) => statusTag(value),
      },
      {
        title: "异常摘要",
        width: 260,
        ellipsis: true,
        render: (_, record) =>
          errorText(record.imErrorMessage || record.complaintError || record.autoReplyError),
      },
      {
        title: "评价时间",
        dataIndex: "commentTime",
        width: 180,
      },
      {
        title: "操作",
        width: 300,
        fixed: "right",
        render: (_, record) => (
          <Space size={0} wrap>
            <PermissionControl permission="comment:processing-log:query">
              <Button type="link" onClick={() => void openDetail(record)}>详情</Button>
            </PermissionControl>
            {record.valid === 1 && isProcessableCommentType(record.commentType) && <PermissionControl permission="comment:processing-log:retry">
              <Space size={0} wrap>
                {record.autoReplyStatus === "FAILED" && (
                  <Button
                    type="link"
                    icon={<RedoOutlined />}
                    loading={retryingKey === `${record.id}:auto-reply`}
                    onClick={() => confirmRetry(record, "auto-reply")}
                  >
                    重试回复
                  </Button>
                )}
                {record.complaintStatus === "FAILED" && (
                  <Button
                    type="link"
                    icon={<RedoOutlined />}
                    loading={retryingKey === `${record.id}:complaint`}
                    onClick={() => confirmRetry(record, "complaint")}
                  >
                    重试申诉
                  </Button>
                )}
                {record.imFollowUpStatus === "FAILED" && (
                  <Button
                    type="link"
                    icon={<RedoOutlined />}
                    loading={retryingKey === `${record.id}:im-follow-up`}
                    onClick={() => confirmRetry(record, "im-follow-up")}
                  >
                    重试群聊
                  </Button>
                )}
              </Space>
            </PermissionControl>}
          </Space>
        ),
      },
    ],
    [confirmRetry, openDetail, retryingKey],
  );

  const submitFilters = (values: FilterValues) => {
    setQuery((current) => ({
      pageNum: 1,
      pageSize: current.pageSize || 20,
      commentTimeFrom: values.dateRange?.[0]?.format("YYYY-MM-DD 00:00:00"),
      commentTimeTo: values.dateRange?.[1]?.format("YYYY-MM-DD 23:59:59"),
      shopId: values.shopId,
      shopKeyword: values.shopKeyword?.trim() || undefined,
      commentId: values.commentId,
      keyword: values.keyword?.trim() || undefined,
      sentiment: values.sentiment,
      autoReplyStatus: values.autoReplyStatus,
      complaintStatus: values.complaintStatus,
      imFollowUpStatus: values.imFollowUpStatus,
    }));
  };

  return (
    <PageContainer>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <div>
          <Title level={2} style={{ marginBottom: 4 }}>评价处理日志</Title>
          <Text type="secondary">统一复盘评价自动回复、差评申诉和评价匿名群聊跟进结果。</Text>
        </div>

        <Card size="small">
          <Form form={form} layout="vertical" onFinish={submitFilters}>
            <Row gutter={12}>
              <Col xs={24} md={8} xl={6}><Form.Item name="dateRange" label="评价时间"><RangePicker style={{ width: "100%" }} /></Form.Item></Col>
              <Col xs={12} md={4} xl={3}><Form.Item name="shopId" label="门店 ID"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
              <Col xs={12} md={6} xl={4}><Form.Item name="shopKeyword" label="门店"><Input allowClear placeholder="名称 / ePoiId" /></Form.Item></Col>
              <Col xs={12} md={6} xl={4}><Form.Item name="commentId" label="评价 ID"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col>
              <Col xs={12} md={6} xl={4}><Form.Item name="keyword" label="评价关键词"><Input allowClear /></Form.Item></Col>
              <Col xs={12} md={4} xl={3}><Form.Item name="sentiment" label="评价类型"><Select allowClear options={sentimentOptions} /></Form.Item></Col>
              <Col xs={12} md={5} xl={3}><Form.Item name="autoReplyStatus" label="自动回复"><Select allowClear options={commonStatusOptions} /></Form.Item></Col>
              <Col xs={12} md={5} xl={3}><Form.Item name="complaintStatus" label="差评申诉"><Select allowClear options={postStatusOptions} /></Form.Item></Col>
              <Col xs={12} md={5} xl={3}><Form.Item name="imFollowUpStatus" label="群聊跟进"><Select allowClear options={postStatusOptions} /></Form.Item></Col>
              <Col xs={24} md={9} xl={6} style={{ display: "flex", alignItems: "flex-end" }}>
                <Form.Item>
                  <Space>
                    <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
                    <Button onClick={() => { form.resetFields(); setQuery({ pageNum: 1, pageSize: query.pageSize || 20 }); }}>重置</Button>
                    <Button icon={<ReloadOutlined />} onClick={() => void loadData()}>刷新</Button>
                  </Space>
                </Form.Item>
              </Col>
            </Row>
          </Form>
        </Card>

        <Table<CommentProcessingLogRecord>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={records}
          scroll={{ x: 2020 }}
          pagination={{
            current: query.pageNum,
            pageSize: query.pageSize,
            total,
            showSizeChanger: true,
            showTotal: (value) => `共 ${value} 条`,
            onChange: (pageNum, pageSize) => setQuery((current) => ({ ...current, pageNum, pageSize })),
          }}
        />
      </Space>

      <Drawer
        title="评价处理详情"
        width={760}
        open={detailOpen}
        loading={detailLoading}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? <ProcessingDetail record={detail} /> : !detailLoading ? <Empty /> : null}
      </Drawer>
    </PageContainer>
  );
}

function ProcessingDetail({ record }: { record: CommentProcessingLogRecord }) {
  const reviewItems: DescriptionsProps["items"] = [
    { key: "shop", label: "门店", children: `${text(record.shopName)}（${record.shopId}）` },
    { key: "commentId", label: "评价 ID", children: record.commentId },
    { key: "bizCode", label: "评价业务线", children: bizLineTag(record.bizCode) },
    { key: "valid", label: "评价有效性", children: validityTag(record.valid) },
    { key: "commentType", label: "评论敏感类型", children: sensitiveTypeTag(record.commentType) },
    { key: "sentiment", label: "评价类型", children: sentimentTag(record.sentiment) },
    { key: "time", label: "评价时间", children: text(record.commentTime) },
    { key: "score", label: "商品 / 包装 / 配送 / 商家", children: `${text(record.productScore)} / ${text(record.packingScore)} / ${text(record.deliveryCommentScore)} / ${text(record.foodCommentScore)}` },
    { key: "content", label: "评价内容", span: 2, children: text(record.commentContent) },
    { key: "add", label: "追评内容", span: 2, children: text(record.addComment) },
  ];
  const replyItems: DescriptionsProps["items"] = [
    { key: "status", label: "状态", children: statusTag(record.autoReplyStatus) },
    { key: "at", label: "处理时间", children: text(record.autoReplyAt) },
    { key: "content", label: "回复内容", span: 2, children: text(record.autoReplyContent) },
    { key: "source", label: "来源 / 模型", children: `${text(record.autoReplySource)} / ${text(record.autoReplyModel)}` },
    { key: "tokens", label: "输入 / 输出 Token", children: `${text(record.autoReplyPromptTokens)} / ${text(record.autoReplyCompletionTokens)}` },
    { key: "power", label: "算力 / 耗时", children: `${text(record.autoReplyPowerAmount)} / ${text(record.autoReplyDurationMs)} ms` },
    { key: "error", label: "错误", span: 2, children: errorText(record.autoReplyError) },
  ];
  const complaintItems: DescriptionsProps["items"] = [
    { key: "status", label: "状态", children: statusTag(record.complaintStatus) },
    { key: "at", label: "提交时间", children: text(record.complaintAt) },
    { key: "type", label: "申诉类型", children: `${text(record.complaintReportTypeCode)} ${text(record.complaintReportTypeName)}` },
    { key: "power", label: "算力 / 耗时", children: `${text(record.complaintPowerAmount)} / ${text(record.complaintDurationMs)} ms` },
    { key: "content", label: "申诉内容", span: 2, children: text(record.complaintReportContent) },
    { key: "error", label: "错误", span: 2, children: errorText(record.complaintError) },
  ];
  const imItems: DescriptionsProps["items"] = [
    { key: "status", label: "状态", children: statusTag(record.imFollowUpStatus) },
    { key: "stage", label: "失败阶段", children: text(record.imFailedStage) },
    { key: "message", label: "群聊消息", span: 2, children: text(record.imMessageContent) },
    { key: "source", label: "来源 / 兜底原因", span: 2, children: `${text(record.imMessageSource)} / ${text(record.imFallbackReason)}` },
    { key: "model", label: "模型 / Prompt", span: 2, children: `${text(record.imModel)} / ${text(record.imPromptKey)} v${text(record.imPromptVersion)}` },
    { key: "tokens", label: "Token / 算力", children: `${text(record.imTotalTokens)} / ${text(record.imPowerAmount)}` },
    { key: "generated", label: "生成时间", children: text(record.imGeneratedAt) },
    { key: "groupAttempt", label: "建群尝试", children: text(record.createGroupAttemptedAt) },
    { key: "group", label: "群 ID / 状态", children: `${text(record.groupId)} / ${text(record.groupState)}` },
    { key: "sendAttempt", label: "发送尝试", children: text(record.sendAttemptedAt) },
    { key: "messageId", label: "消息 ID / cts", children: `${text(record.messageId)} / ${text(record.messageCts)}` },
    { key: "sent", label: "发送成功时间", children: text(record.sentAt) },
    { key: "duration", label: "生成 / 建群 / 发送耗时", span: 2, children: `${text(record.imGenerationDurationMs)} / ${text(record.imCreateGroupDurationMs)} / ${text(record.imSendDurationMs)} ms` },
    { key: "error", label: "错误", span: 2, children: errorText(record.imErrorMessage ? `${record.imErrorCode || ""} ${record.imErrorMessage}`.trim() : undefined) },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card title="评价内容" size="small">
        <Descriptions bordered size="small" column={2} items={reviewItems} />
        {!!record.imageUrls?.length && (
          <Image.PreviewGroup>
            <Space wrap style={{ marginTop: 12 }}>
              {record.imageUrls.map((url) => <Image key={url} src={url} width={88} height={88} style={{ objectFit: "cover" }} />)}
            </Space>
          </Image.PreviewGroup>
        )}
      </Card>
      <Card title="自动回复" size="small"><Descriptions bordered size="small" column={2} items={replyItems} /></Card>
      <Card title="差评申诉" size="small"><Descriptions bordered size="small" column={2} items={complaintItems} /></Card>
      <Card title="群聊跟进" size="small"><Descriptions bordered size="small" column={2} items={imItems} /></Card>
    </Space>
  );
}
