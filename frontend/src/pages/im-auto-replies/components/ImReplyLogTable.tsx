import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, DatePicker, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";
import type {
  ImAutoReplyLog,
  ImAutoReplyLogQuery,
  ImConversationHistoryPage,
  PageInfo,
} from "@/types";
import ConversationHistoryDrawer from "./ConversationHistoryDrawer";
import { MessageBody } from "./MessageBody";

const { Text, Paragraph } = Typography;

const STATUS_OPTIONS = [
  "PENDING", "GENERATING", "GENERATED", "BILLED", "SENDING", "SENT", "SUPERSEDED", "SKIPPED", "DEAD",
].map((value) => ({ label: value, value }));

const STATUS_COLORS: Record<string, string> = {
  SENT: "green", SENDING: "blue", GENERATED: "cyan", BILLED: "geekblue",
  SKIPPED: "default", SUPERSEDED: "orange", DEAD: "red", PENDING: "gold", GENERATING: "purple",
};

/** 订单号与识别出的手机号：看日志时最常要复制的两项，单独成行并挂上复制按钮。 */
function ContactTags({ orderId, phones }: { orderId?: number; phones?: string[] }) {
  // 无订单的消息在库里存的是 0，这里按「缺失」处理，不能展示成「订单 0」
  const hasOrder = Boolean(orderId);
  const list = phones ?? [];
  if (!hasOrder && list.length === 0) return null;
  return (
    <Space size={[4, 4]} wrap>
      {hasOrder ? (
        <Tag color="geekblue" style={{ marginInlineEnd: 0 }}>
          订单 <Text copyable={{ text: String(orderId) }}>{orderId}</Text>
        </Tag>
      ) : null}
      {list.map((phone) => (
        <Tag key={phone} color="volcano" style={{ marginInlineEnd: 0 }}>
          手机 <Text copyable={{ text: phone }}>{phone}</Text>
        </Tag>
      ))}
    </Space>
  );
}

interface Props {
  loadPage: (query: ImAutoReplyLogQuery) => Promise<PageInfo<ImAutoReplyLog>>;
  loadHistory: (
    conversationId: number,
    params?: { cursorTime?: string; cursorId?: number; pageSize?: number },
  ) => Promise<ImConversationHistoryPage>;
  showShopFilter?: boolean;
  showRagColumn?: boolean;
  compact?: boolean;
}

type SearchForm = ImAutoReplyLogQuery & { timeRange?: [Dayjs, Dayjs] };

export default function ImReplyLogTable({ loadPage, loadHistory, showShopFilter = true, showRagColumn = true, compact = false }: Props) {
  const [form] = Form.useForm<SearchForm>();
  const [query, setQuery] = useState<ImAutoReplyLogQuery>({ pageNum: 1, pageSize: compact ? 10 : 20 });
  const [items, setItems] = useState<ImAutoReplyLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<ImAutoReplyLog>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await loadPage(query);
      setItems(page.list || []);
      setTotal(page.total || 0);
    } finally {
      setLoading(false);
    }
  }, [loadPage, query]);

  useEffect(() => { void fetchData(); }, [fetchData]);

  const columns = useMemo<ColumnsType<ImAutoReplyLog>>(() => {
    const values: ColumnsType<ImAutoReplyLog> = [];
    if (showShopFilter) values.push({
      title: "门店", key: "shop", width: 190,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text strong>{row.shopName || "-"}</Text>
        <Text type="secondary">{row.poiId || row.epoiId || "-"}</Text>
      </Space>,
    });
    values.push(
      {
        title: "顾客消息", key: "user", width: 300,
        render: (_, row) => <Space direction="vertical" size={4} style={{ width: "100%" }}>
          <Text type="secondary">顾客 {row.openUserId || "-"}</Text>
          <ContactTags orderId={row.orderId} phones={row.userPhones} />
          <MessageBody msgType={row.userMsgType} content={row.userContent} ellipsisRows={3} />
          <Text type="secondary">{row.userMessageTime ? dayjs(row.userMessageTime).format("YYYY-MM-DD HH:mm:ss") : "-"}</Text>
        </Space>,
      },
      {
        title: "AI 自动回复", key: "ai", width: 330,
        render: (_, row) => <Space direction="vertical" size={4} style={{ width: "100%" }}>
          <Space wrap><Tag color={STATUS_COLORS[row.status]}>{row.status}</Tag>{row.deliveryStatus ? <Tag>{row.deliveryStatus}</Tag> : null}</Space>
          <Paragraph ellipsis={{ rows: 3, expandable: true, symbol: "展开" }} style={{ margin: 0, whiteSpace: "pre-wrap" }}>{row.aiContent || `（${row.skipReason || "尚未生成"}）`}</Paragraph>
          <Text type="secondary">{row.aiMessageTime ? dayjs(row.aiMessageTime).format("YYYY-MM-DD HH:mm:ss") : "-"}</Text>
        </Space>,
      },
    );
    if (showRagColumn) values.push({
      title: "RAG / 计费", key: "rag", width: 170,
      render: (_, row) => <Space direction="vertical" size={3}>
        <Tag color={row.ragUsed ? "blue" : "default"}>{row.ragUsed ? `RAG ${row.ragChunkCount || 0} 条` : "未使用 RAG"}</Tag>
        <Text type="secondary">Token：{(row.promptTokens || 0) + (row.completionTokens || 0)}</Text>
        <Text type="secondary">算力：{row.powerAmount ?? "-"}</Text>
      </Space>,
    });
    values.push({
      title: "操作", key: "action", width: 100, fixed: "right",
      render: (_, row) => <Button type="link" onClick={() => setSelected(row)}>完整对话</Button>,
    });
    return values;
  }, [showShopFilter, showRagColumn]);

  return <>
    <Form form={form} layout="inline" style={{ marginBottom: 16, rowGap: 12 }} onFinish={() => {
      const values = form.getFieldsValue();
      const { timeRange, ...filters } = values;
      setQuery((current) => ({
        ...current,
        ...filters,
        shopKeyword: values.shopKeyword?.trim() || undefined,
        startTime: timeRange?.[0]?.format("YYYY-MM-DD HH:mm:ss"),
        endTime: timeRange?.[1]?.format("YYYY-MM-DD HH:mm:ss"),
        pageNum: 1,
      }));
    }}>
      {showShopFilter ? <Form.Item name="shopKeyword"><Input allowClear placeholder="门店名称 / 门店ID" /></Form.Item> : null}
      <Form.Item name="openUserId"><Input allowClear placeholder="顾客 openUserId" /></Form.Item>
      <Form.Item name="orderId"><Input allowClear placeholder="订单ID" /></Form.Item>
      <Form.Item name="status"><Select allowClear placeholder="回复状态" options={STATUS_OPTIONS} style={{ width: 150 }} /></Form.Item>
      {showRagColumn ? <Form.Item name="ragUsed"><Select allowClear placeholder="RAG" style={{ width: 120 }} options={[{ label: "已使用", value: true }, { label: "未使用", value: false }]} /></Form.Item> : null}
      <Form.Item name="timeRange"><DatePicker.RangePicker showTime placeholder={["开始时间", "结束时间"]} /></Form.Item>
      <Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); setQuery({ pageNum: 1, pageSize: compact ? 10 : 20 }); }}>重置</Button></Space></Form.Item>
    </Form>
    <Table<ImAutoReplyLog>
      rowKey="autoReplyId"
      columns={columns}
      dataSource={items}
      loading={loading}
      scroll={{ x: showShopFilter ? 1100 : 900 }}
      pagination={{ current: query.pageNum, pageSize: query.pageSize, total, showSizeChanger: true, showTotal: (value) => `共 ${value} 条` }}
      onChange={(pagination) => setQuery((current) => ({ ...current, pageNum: pagination.current || 1, pageSize: pagination.pageSize || 20 }))}
    />
    <ConversationHistoryDrawer
      open={Boolean(selected)}
      conversation={selected ? {
        conversationId: selected.conversationId,
        shopName: selected.shopName,
        openUserId: selected.openUserId,
      } : undefined}
      onClose={() => setSelected(undefined)}
      loadHistory={loadHistory}
    />
  </>;
}
