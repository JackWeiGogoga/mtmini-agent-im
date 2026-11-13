import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, DatePicker, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import type { Dayjs } from "dayjs";
import PageContainer from "@/components/PageContainer";
import { getImBroadcastTasks } from "@/services/im-broadcast-api";
import type { ImBroadcastTask, ImBroadcastTaskQuery, ImBroadcastTaskStatus } from "@/types";
import BroadcastRecordDrawer from "./components/BroadcastRecordDrawer";

const { Text, Paragraph } = Typography;

const TASK_STATUS_META: Record<ImBroadcastTaskStatus, { color: string; label: string }> = {
  PENDING: { color: "default", label: "待编排" },
  SENDING: { color: "processing", label: "发送中" },
  SUCCESS: { color: "green", label: "全部成功" },
  PARTIAL: { color: "orange", label: "部分成功" },
  FAILED: { color: "red", label: "全部失败" },
  SKIPPED: { color: "default", label: "已跳过" },
};

const WEEK_DAY_LABELS = ["", "周一", "周二", "周三", "周四", "周五", "周六", "周日"];

function formatWeekDays(weekDays?: string) {
  if (!weekDays) {
    return "-";
  }
  return weekDays
    .split(",")
    .map((day) => WEEK_DAY_LABELS[Number(day.trim())] || day)
    .join("、");
}

type BroadcastSearchForm = ImBroadcastTaskQuery & { timeRange?: [Dayjs, Dayjs] };

export default function ImBroadcastManage() {
  const [form] = Form.useForm<BroadcastSearchForm>();
  const [query, setQuery] = useState<ImBroadcastTaskQuery>({ pageNum: 1, pageSize: 20 });
  const [items, setItems] = useState<ImBroadcastTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<ImBroadcastTask>();

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await getImBroadcastTasks(query);
      setItems(page.list || []);
      setTotal(page.total || 0);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => { void fetchData(); }, [fetchData]);

  const columns = useMemo<ColumnsType<ImBroadcastTask>>(() => [
    {
      title: "门店", key: "shop", width: 220,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text strong>{row.shopName || "-"}</Text>
        <Text type="secondary">{row.poiId || row.epoiId || "-"}</Text>
      </Space>,
    },
    {
      title: "触发计划", key: "plan", width: 220,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text>{row.triggerDate || "-"} {row.planTime || ""}</Text>
        <Text type="secondary">
          {formatWeekDays(row.weekDays)} · {row.repeatMode === "ONCE" ? "一次" : "每次"}
        </Text>
      </Space>,
    },
    {
      title: "群发内容", dataIndex: "content", width: 300,
      render: (value?: string) => <Paragraph ellipsis={{ rows: 2 }} style={{ margin: 0 }}>{value || "-"}</Paragraph>,
    },
    {
      title: "状态", key: "status", width: 150,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Tag color={TASK_STATUS_META[row.status]?.color}>{TASK_STATUS_META[row.status]?.label || row.status}</Tag>
        {row.skipReason ? <Text type="secondary" style={{ fontSize: 12 }}>{row.skipReason}</Text> : null}
      </Space>,
    },
    {
      title: "发送结果", key: "counts", width: 260,
      render: (_, row) => <Space wrap size={4}>
        <Tag>目标 {row.targetCount || 0}</Tag>
        <Tag color="green">成功 {row.successCount || 0}</Tag>
        {row.failCount ? <Tag color="red">失败 {row.failCount}</Tag> : null}
        {row.truncatedCount ? <Tag color="orange">截断 {row.truncatedCount}</Tag> : null}
        <Tag color="blue">
          已处理 {(row.successCount || 0) + (row.failCount || 0)}/{row.targetCount || 0}
        </Tag>
      </Space>,
    },
    {
      title: "时间", key: "time", width: 200,
      render: (_, row) => <Space direction="vertical" size={0}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          创建 {row.createTime ? dayjs(row.createTime).format("MM-DD HH:mm:ss") : "-"}
        </Text>
        <Text type="secondary" style={{ fontSize: 12 }}>
          结束 {row.finishedAt ? dayjs(row.finishedAt).format("MM-DD HH:mm:ss") : "-"}
        </Text>
      </Space>,
    },
    {
      title: "操作", key: "action", width: 110, fixed: "right",
      render: (_, row) => <Button type="link" onClick={() => setSelected(row)}>查看明细</Button>,
    },
  ], []);

  return <PageContainer title="群发管理">
    <Form form={form} layout="inline" style={{ marginBottom: 16, rowGap: 12 }} onFinish={() => {
      const values = form.getFieldsValue();
      const { timeRange, ...filters } = values;
      setQuery((current) => ({
        ...current,
        ...filters,
        shopKeyword: values.shopKeyword?.trim() || undefined,
        contentKeyword: values.contentKeyword?.trim() || undefined,
        startTime: timeRange?.[0]?.format("YYYY-MM-DD HH:mm:ss"),
        endTime: timeRange?.[1]?.format("YYYY-MM-DD HH:mm:ss"),
        pageNum: 1,
      }));
    }}>
      <Form.Item name="shopKeyword"><Input allowClear placeholder="门店名称 / 门店ID" /></Form.Item>
      <Form.Item name="contentKeyword"><Input allowClear placeholder="群发内容关键字" /></Form.Item>
      <Form.Item name="status">
        <Select allowClear placeholder="批次状态" style={{ width: 150 }} options={[
          { value: "SENDING", label: "发送中" },
          { value: "SUCCESS", label: "全部成功" },
          { value: "PARTIAL", label: "部分成功" },
          { value: "FAILED", label: "全部失败" },
          { value: "SKIPPED", label: "已跳过" },
          { value: "PENDING", label: "待编排" },
        ]} />
      </Form.Item>
      <Form.Item name="repeatMode">
        <Select allowClear placeholder="频率" style={{ width: 120 }} options={[
          { value: "EVERY", label: "每次" },
          { value: "ONCE", label: "一次" },
        ]} />
      </Form.Item>
      <Form.Item name="timeRange"><DatePicker.RangePicker showTime placeholder={["开始时间", "结束时间"]} /></Form.Item>
      <Form.Item>
        <Space>
          <Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); setQuery({ pageNum: 1, pageSize: 20 }); }}>重置</Button>
        </Space>
      </Form.Item>
    </Form>
    <Table<ImBroadcastTask>
      rowKey="taskId"
      columns={columns}
      dataSource={items}
      loading={loading}
      scroll={{ x: 1360 }}
      pagination={{
        current: query.pageNum,
        pageSize: query.pageSize,
        total,
        showSizeChanger: true,
        showTotal: (value) => `共 ${value} 个群发批次`,
      }}
      onChange={(pagination) => setQuery((current) => ({
        ...current,
        pageNum: pagination.current || 1,
        pageSize: pagination.pageSize || 20,
      }))}
    />
    <BroadcastRecordDrawer
      open={Boolean(selected)}
      task={selected}
      onClose={() => setSelected(undefined)}
    />
  </PageContainer>;
}
