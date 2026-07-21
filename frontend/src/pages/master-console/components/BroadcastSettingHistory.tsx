import React, { useCallback, useEffect, useState } from "react";
import { Empty, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { message } from "@/components/AppMessageContext";
import { getMasterConsoleBroadcastSettingHistory } from "@/services/master-console-api";
import type { ImBroadcastSettingHistory } from "@/types";
import { WEEK_DAY_OPTIONS } from "./BroadcastSettingModal";

const { Text, Paragraph } = Typography;
const PAGE_SIZE = 10;

function formatWeekDays(weekDays?: number[]) {
  if (!weekDays?.length) return "-";
  return weekDays
    .map((day) => WEEK_DAY_OPTIONS.find((option) => option.value === day)?.label)
    .filter(Boolean)
    .join("、");
}

function formatDateTime(value?: string) {
  if (!value) return "-";
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm:ss") : value;
}

const BroadcastSettingHistoryTable: React.FC<{ shopId?: number }> = ({ shopId }) => {
  const [rows, setRows] = useState<ImBroadcastSettingHistory[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);

  const fetchPage = useCallback(async (page: number) => {
    if (!shopId) return;
    setLoading(true);
    try {
      const result = await getMasterConsoleBroadcastSettingHistory(shopId, {
        pageNum: page,
        pageSize: PAGE_SIZE,
      });
      setRows(result.list || []);
      setTotal(result.total || 0);
    } catch {
      message.error("加载变更历史失败");
    } finally {
      setLoading(false);
    }
  }, [shopId]);

  useEffect(() => {
    setPageNum(1);
    void fetchPage(1);
  }, [fetchPage]);

  const columns: ColumnsType<ImBroadcastSettingHistory> = [
    {
      title: "变更", key: "change", width: 150,
      render: (_, record) => <Space direction="vertical" size={2}>
        <Space size={6}>
          <Tag color={record.changeType === "DELETE" ? "red" : "green"}>
            {record.changeTypeName || record.changeType}
          </Tag>
          <Tag>{record.changeSource === "H5" ? "商家H5" : record.changeSource === "ADMIN" ? "后台" : "系统"}</Tag>
        </Space>
        <Text type="secondary">v{record.versionNo}</Text>
      </Space>,
    },
    {
      title: "配置", key: "schedule", width: 210,
      render: (_, record) => <Space direction="vertical" size={2}>
        <Text>{formatWeekDays(record.weekDays)}</Text>
        <Text type="secondary">
          {record.sendTime || "-"} · {record.repeatMode === "ONCE" ? "一次" : "每次"}
        </Text>
      </Space>,
    },
    {
      title: "文案", dataIndex: "content", key: "content",
      render: (value?: string) => value
        ? <Paragraph style={{ marginBottom: 0 }} ellipsis={{ rows: 3, expandable: true, symbol: "展开" }}>{value}</Paragraph>
        : <Text type="secondary">-</Text>,
    },
    {
      title: "操作人", key: "operator", width: 130,
      render: (_, record) => <Text>{record.operatorName || record.operatorUserId || "-"}</Text>,
    },
    { title: "时间", dataIndex: "createTime", key: "createTime", width: 175, render: formatDateTime },
  ];

  return <Table<ImBroadcastSettingHistory>
    rowKey="id"
    size="small"
    columns={columns}
    dataSource={rows}
    loading={loading}
    scroll={{ x: 880 }}
    locale={{ emptyText: <Empty description="该门店还没有群发配置变更记录" /> }}
    pagination={{
      current: pageNum,
      pageSize: PAGE_SIZE,
      total,
      showSizeChanger: false,
      showTotal: (value) => `共 ${value} 条`,
      onChange: (page) => { setPageNum(page); void fetchPage(page); },
    }}
  />;
};

export default BroadcastSettingHistoryTable;
