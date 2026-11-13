import { useCallback, useEffect, useMemo, useState } from "react";
import { Drawer, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { getImBroadcastRecords } from "@/services/im-broadcast-api";
import type { ImBroadcastRecord, ImBroadcastRecordStatus, ImBroadcastTask } from "@/types";

const { Text } = Typography;

const RECORD_STATUS_META: Record<ImBroadcastRecordStatus, { color: string; label: string }> = {
  PENDING: { color: "default", label: "待发送" },
  SENT: { color: "green", label: "已发送" },
  FAILED: { color: "red", label: "发送失败" },
};

interface Props {
  open: boolean;
  task?: ImBroadcastTask;
  onClose: () => void;
}

export default function BroadcastRecordDrawer({ open, task, onClose }: Props) {
  const [items, setItems] = useState<ImBroadcastRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<ImBroadcastRecordStatus>();
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const fetchData = useCallback(async () => {
    if (!task?.taskId) {
      return;
    }
    setLoading(true);
    try {
      const page = await getImBroadcastRecords(task.taskId, { status, pageNum, pageSize });
      setItems(page.list || []);
      setTotal(page.total || 0);
    } finally {
      setLoading(false);
    }
  }, [task?.taskId, status, pageNum, pageSize]);

  useEffect(() => {
    if (open) {
      void fetchData();
    }
  }, [open, fetchData]);

  // 切换批次时重置筛选与分页，避免沿用上一个批次的查询条件。
  useEffect(() => {
    setStatus(undefined);
    setPageNum(1);
  }, [task?.taskId]);

  const columns = useMemo<ColumnsType<ImBroadcastRecord>>(() => [
    { title: "顾客 openUserId", dataIndex: "openUserId", width: 190 },
    { title: "会话ID", dataIndex: "conversationId", width: 120 },
    { title: "分片", dataIndex: "shardNo", width: 80 },
    {
      title: "状态", dataIndex: "status", width: 110,
      render: (value: ImBroadcastRecordStatus) => (
        <Tag color={RECORD_STATUS_META[value]?.color}>{RECORD_STATUS_META[value]?.label || value}</Tag>
      ),
    },
    {
      title: "失败原因", key: "error", width: 320,
      render: (_, row) => row.errorCode
        ? <Space direction="vertical" size={0}>
            <Text type="danger">{row.errorCode}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{row.errorSummary || "-"}</Text>
          </Space>
        : <Text type="secondary">-</Text>,
    },
    {
      title: "发送时间", dataIndex: "sentAt", width: 190,
      render: (value?: string) => (value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "-"),
    },
  ], []);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={1000}
      title={task ? `群发明细 · 批次 ${task.taskId}` : "群发明细"}
    >
      <Space style={{ marginBottom: 16 }}>
        <Select<ImBroadcastRecordStatus>
          allowClear
          placeholder="发送状态"
          style={{ width: 160 }}
          value={status}
          onChange={(value) => { setStatus(value); setPageNum(1); }}
          options={[
            { value: "SENT", label: "已发送" },
            { value: "FAILED", label: "发送失败" },
            { value: "PENDING", label: "待发送" },
          ]}
        />
      </Space>
      <Table<ImBroadcastRecord>
        rowKey="recordId"
        columns={columns}
        dataSource={items}
        loading={loading}
        scroll={{ x: 1000 }}
        pagination={{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条明细`,
        }}
        onChange={(pagination) => {
          setPageNum(pagination.current || 1);
          setPageSize(pagination.pageSize || 20);
        }}
      />
    </Drawer>
  );
}
