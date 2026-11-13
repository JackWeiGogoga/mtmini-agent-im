import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, DatePicker, Form, Input, Space, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";
import PageContainer from "@/components/PageContainer";
import {
  getImAutoReplyLogs,
  getImConversationHistory,
  getImConversations,
} from "@/services/im-auto-reply-api";
import type { ImConversation, ImConversationQuery } from "@/types";
import ConversationHistoryDrawer from "./components/ConversationHistoryDrawer";
import { isImageMessage } from "./components/MessageBody";
import ImReplyLogTable from "./components/ImReplyLogTable";

const { Text, Paragraph } = Typography;
type ConversationSearchForm = ImConversationQuery & { timeRange?: [Dayjs, Dayjs] };

function ConversationList() {
  const [form] = Form.useForm<ConversationSearchForm>();
  const [query, setQuery] = useState<ImConversationQuery>({ pageNum: 1, pageSize: 20 });
  const [items, setItems] = useState<ImConversation[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<ImConversation>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await getImConversations(query);
      setItems(page.list || []);
      setTotal(page.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => { void fetchData(); }, [fetchData]);

  const columns = useMemo<ColumnsType<ImConversation>>(() => [
    {
      title: "门店", key: "shop", width: 220,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text strong>{row.shopName || "-"}</Text>
        <Text type="secondary">{row.poiId || row.epoiId || "-"}</Text>
      </Space>,
    },
    {
      title: "顾客", key: "customer", width: 190,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text>{row.openUserId}</Text>
        <Text type="secondary">{row.sessionCount || 0} 个历史 Session</Text>
      </Space>,
    },
    {
      title: "消息数", key: "counts", width: 210,
      render: (_, row) => <Space wrap>
        <Tag>全部 {row.messageCount || 0}</Tag>
        <Tag color="blue">顾客 {row.userMessageCount || 0}</Tag>
        <Tag color="purple">AI {row.aiMessageCount || 0}</Tag>
        {row.merchantMessageCount ? <Tag color="green">人工 {row.merchantMessageCount}</Tag> : null}
      </Space>,
    },
    {
      title: "最新消息", key: "latest", width: 330,
      render: (_, row) => <Space direction="vertical" size={2} style={{ width: "100%" }}>
        <Space><Tag>{row.lastMessageRole || "-"}</Tag><Text type="secondary">{row.lastMessageAt ? dayjs(row.lastMessageAt).format("YYYY-MM-DD HH:mm:ss") : "-"}</Text></Space>
        <Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>
          {/* 列表只做摘要，一行图床 URL 既占地方又没信息量，缩成图片标记 */}
          {isImageMessage(row.lastMessageType, row.lastMessageContent) ? "[图片]" : row.lastMessageContent || "-"}
        </Paragraph>
      </Space>,
    },
    {
      title: "操作", key: "action", width: 110, fixed: "right",
      render: (_, row) => <Button type="link" onClick={() => setSelected(row)}>查看完整对话</Button>,
    },
  ], []);

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
      <Form.Item name="shopKeyword"><Input allowClear placeholder="门店名称 / 门店ID" /></Form.Item>
      <Form.Item name="openUserId"><Input allowClear placeholder="顾客 openUserId" /></Form.Item>
      <Form.Item name="orderId"><Input allowClear placeholder="历史订单ID" /></Form.Item>
      <Form.Item name="timeRange"><DatePicker.RangePicker showTime placeholder={["开始时间", "结束时间"]} /></Form.Item>
      <Form.Item><Space><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); setQuery({ pageNum: 1, pageSize: 20 }); }}>重置</Button></Space></Form.Item>
    </Form>
    <Table<ImConversation>
      rowKey="conversationId"
      columns={columns}
      dataSource={items}
      loading={loading}
      scroll={{ x: 1060 }}
      pagination={{ current: query.pageNum, pageSize: query.pageSize, total, showSizeChanger: true, showTotal: (value) => `共 ${value} 个会话` }}
      onChange={(pagination) => setQuery((current) => ({ ...current, pageNum: pagination.current || 1, pageSize: pagination.pageSize || 20 }))}
    />
    <ConversationHistoryDrawer
      open={Boolean(selected)}
      conversation={selected}
      onClose={() => setSelected(undefined)}
      loadHistory={getImConversationHistory}
    />
  </>;
}

export default function ImAutoReplyManage() {
  return <PageContainer title="自动回复记录">
    <Tabs items={[
      {
        key: "messages",
        label: "消息明细",
        children: <ImReplyLogTable loadPage={getImAutoReplyLogs} loadHistory={getImConversationHistory} />,
      },
      {
        key: "conversations",
        label: "会话列表",
        children: <ConversationList />,
      },
    ]} />
  </PageContainer>;
}
