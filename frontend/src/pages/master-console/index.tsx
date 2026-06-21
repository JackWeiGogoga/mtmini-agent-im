import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Form,
  Input,
  Select,
  Segmented,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import { DownloadOutlined, ReloadOutlined, ShopOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { useResponsive } from "@/hooks";
import {
  exportMasterConsoleShops,
  getMasterConsoleShops,
  getMasterConsoleVisibleUsers,
} from "@/services/master-console-api";
import type {
  DashboardStatsRange,
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  ShopBindingDomain,
  ShopBindingStatus,
  VisibleUserOption,
} from "@/types";

const { Text } = Typography;

const USER_SEARCH_DEBOUNCE_MS = 300;

const BINDING_DOMAIN_OPTIONS: { label: string; value: ShopBindingDomain }[] = [
  { label: "全部", value: "ALL" },
  { label: "普通商户", value: "ORDINARY" },
  { label: "网销用户", value: "SALES" },
];

const BINDING_STATUS_OPTIONS: { label: string; value: ShopBindingStatus }[] = [
  { label: "全部状态", value: "ALL" },
  { label: "未绑定", value: "UNBOUND" },
  { label: "已绑定", value: "BOUND" },
  { label: "双绑定", value: "BOTH" },
];

const DASHBOARD_STATS_RANGE_OPTIONS: {
  label: string;
  value: DashboardStatsRange;
  compareLabel: string;
}[] = [
  { label: "今日", value: "TODAY", compareLabel: "较昨日" },
  { label: "近7天", value: "LAST_7_DAYS", compareLabel: "较前7天" },
  { label: "近30天", value: "LAST_30_DAYS", compareLabel: "较前30天" },
];

const DEFAULT_QUERY: MasterConsoleQueryParam = {
  pageNum: 1,
  pageSize: 10,
  bindingDomain: "ALL",
  bindingStatus: "ALL",
  statsRange: "LAST_7_DAYS",
};

function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm") : value;
}

function formatCount(value?: number | null) {
  return value ?? 0;
}

function toNumber(value?: number | string | null) {
  if (value === undefined || value === null || value === "") {
    return 0;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function formatRate(value?: number | string | null) {
  return `${(toNumber(value) * 100).toFixed(1).replace(/\.0$/, "")}%`;
}

function renderDelta(current?: number, previous?: number) {
  const diff = (current ?? 0) - (previous ?? 0);
  if (diff === 0) {
    return <Text type="secondary">持平</Text>;
  }
  return (
    <Text type={diff > 0 ? "success" : "danger"}>
      {diff > 0 ? "+" : ""}
      {diff}
    </Text>
  );
}

function renderRateDelta(current?: number | string, previous?: number | string) {
  const diff = toNumber(current) - toNumber(previous);
  if (Math.abs(diff) < 0.000001) {
    return <Text type="secondary">持平</Text>;
  }
  return (
    <Text type={diff > 0 ? "success" : "danger"}>
      {diff > 0 ? "+" : ""}
      {(diff * 100).toFixed(1).replace(/\.0$/, "")}%
    </Text>
  );
}

function buildUserLabel(user?: VisibleUserOption) {
  if (!user) {
    return "";
  }
  return user.displayName || user.nickname || user.username || user.userId;
}

const MasterConsole: React.FC = () => {
  const [form] = Form.useForm<MasterConsoleQueryParam>();
  const { styles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [query, setQuery] = useState<MasterConsoleQueryParam>(DEFAULT_QUERY);
  const [data, setData] = useState<MasterConsoleShopRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const userSearchTimer = useRef<number | null>(null);

  const activeRange = useMemo(
    () =>
      DASHBOARD_STATS_RANGE_OPTIONS.find((item) => item.value === query.statsRange) ||
      DASHBOARD_STATS_RANGE_OPTIONS[1],
    [query.statsRange],
  );

  const fetchVisibleUsers = useCallback(
    async (keyword?: string, bindingDomain?: string) => {
      setUserLoading(true);
      try {
        const users = await getMasterConsoleVisibleUsers(keyword, bindingDomain);
        setVisibleUsers(users);
      } catch (error) {
        console.error("Failed to load visible users:", error);
      } finally {
        setUserLoading(false);
      }
    },
    [],
  );

  const fetchData = useCallback(async (nextQuery: MasterConsoleQueryParam) => {
    setLoading(true);
    try {
      const page = await getMasterConsoleShops(nextQuery);
      setData(page.list || []);
      setTotal(page.total || 0);
    } catch (error) {
      console.error("Failed to load master console:", error);
      message.error("加载总控台失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchData(query);
  }, [fetchData, query]);

  useEffect(() => {
    void fetchVisibleUsers(undefined, query.bindingDomain);
  }, [fetchVisibleUsers, query.bindingDomain]);

  const updateQuery = useCallback((patch: Partial<MasterConsoleQueryParam>) => {
    setQuery((prev) => ({
      ...prev,
      ...patch,
      pageNum: patch.pageNum ?? 1,
    }));
  }, []);

  const handleSearch = useCallback(() => {
    const values = form.getFieldsValue();
    updateQuery({
      keyword: values.keyword?.trim() || undefined,
      targetUserId: values.targetUserId,
      bindingDomain: values.bindingDomain || "ALL",
      bindingStatus: values.bindingStatus || "ALL",
    });
  }, [form, updateQuery]);

  const handleReset = useCallback(() => {
    form.setFieldsValue(DEFAULT_QUERY);
    setQuery(DEFAULT_QUERY);
  }, [form]);

  const handleExport = useCallback(async () => {
    setExporting(true);
    try {
      await exportMasterConsoleShops(query);
    } finally {
      setExporting(false);
    }
  }, [query]);

  const columns = useMemo<ColumnsType<MasterConsoleShopRow>>(
    () => [
      {
        title: "门店",
        key: "shop",
        width: 260,
        fixed: isCompact ? undefined : "left",
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Space size={6}>
              <ShopOutlined />
              <Text strong>{record.shopName || "-"}</Text>
            </Space>
            <Text type="secondary">门店ID：{record.poiId || "-"}</Text>
            {record.address ? (
              <Tooltip title={record.address}>
                <Text type="secondary" ellipsis style={{ maxWidth: 220 }}>
                  {record.address}
                </Text>
              </Tooltip>
            ) : null}
          </Space>
        ),
      },
      {
        title: "绑定",
        key: "binding",
        width: 180,
        render: (_, record) => (
          <Space direction="vertical" size={4}>
            <Space size={6} wrap>
              <Tag color={record.bindingDomain === "SALES" ? "blue" : "green"}>
                {record.bindingDomainName || "-"}
              </Tag>
              <Tag>{record.bindingStatusName || "-"}</Tag>
            </Space>
            <Text type="secondary">
              用户：{record.boundUserName || record.boundUserId || "-"}
            </Text>
          </Space>
        ),
      },
      {
        title: "所属网销",
        key: "sales",
        width: 170,
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Text>{record.salesBoundUserName || record.salesBoundUserId || "-"}</Text>
            {record.salesBoundUserPhone ? (
              <Text type="secondary">{record.salesBoundUserPhone}</Text>
            ) : null}
          </Space>
        ),
      },
      {
        title: "授权",
        key: "auth",
        width: 120,
        render: (_, record) => (
          <Tag color={record.reauthRequired ? "red" : "green"}>
            {record.authStatusText || "有效"}
          </Tag>
        ),
      },
      {
        title: `好评数`,
        key: "goodCommentCount",
        width: 140,
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Text strong>{formatCount(record.goodCommentCount)}</Text>
            <Text type="secondary">
              {activeRange.compareLabel} {renderDelta(record.goodCommentCount, record.previousGoodCommentCount)}
            </Text>
          </Space>
        ),
      },
      {
        title: "差评数",
        key: "badCommentCount",
        width: 140,
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Text strong>{formatCount(record.badCommentCount)}</Text>
            <Text type="secondary">
              {activeRange.compareLabel} {renderDelta(record.badCommentCount, record.previousBadCommentCount)}
            </Text>
          </Space>
        ),
      },
      {
        title: "差评回复率",
        key: "badCommentReplyRate",
        width: 150,
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Text strong>{formatRate(record.badCommentReplyRate)}</Text>
            <Text type="secondary">
              {activeRange.compareLabel}{" "}
              {renderRateDelta(record.badCommentReplyRate, record.previousBadCommentReplyRate)}
            </Text>
          </Space>
        ),
      },
      {
        title: "算力",
        key: "powerBalance",
        width: 150,
        sorter: true,
        sortOrder:
          query.sortField === "powerBalance" ? (query.sortOrder as SortOrder) : null,
        render: (_, record) => (
          <Space direction="vertical" size={0}>
            <Text>剩余：{record.powerBalance ?? 0}</Text>
            <Text type="secondary">本期消耗：{record.currentConsumedPower ?? 0}</Text>
          </Space>
        ),
      },
      {
        title: "功能到期",
        dataIndex: "rightExpireAt",
        key: "rightExpireAt",
        width: 160,
        render: (value?: string) => formatDateTime(value),
      },
      {
        title: "绑定时间",
        dataIndex: "bindTime",
        key: "bindTime",
        width: 160,
        render: (value?: string) => formatDateTime(value),
      },
    ],
    [activeRange.compareLabel, isCompact, query.sortField, query.sortOrder],
  );

  return (
    <PageContainer title="总控台">
      <div className={styles.toolbar}>
        <Form
          form={form}
          className={styles.searchForm}
          initialValues={DEFAULT_QUERY}
          onFinish={handleSearch}
        >
          <Form.Item name="keyword">
            <Input allowClear placeholder="搜索门店名称 / 门店ID" />
          </Form.Item>
          <Form.Item name="bindingDomain">
            <Select
              options={BINDING_DOMAIN_OPTIONS}
              style={{ width: 130 }}
              onChange={(value) => {
                form.setFieldValue("targetUserId", undefined);
                void fetchVisibleUsers(undefined, value);
              }}
            />
          </Form.Item>
          <Form.Item name="bindingStatus">
            <Select options={BINDING_STATUS_OPTIONS} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="targetUserId">
            <Select
              allowClear
              showSearch
              filterOption={false}
              loading={userLoading}
              placeholder="所属用户"
              style={{ width: 180 }}
              options={visibleUsers.map((user) => ({
                label: buildUserLabel(user),
                value: user.userId,
              }))}
              onSearch={(value) => {
                if (userSearchTimer.current) {
                  window.clearTimeout(userSearchTimer.current);
                }
                userSearchTimer.current = window.setTimeout(() => {
                  void fetchVisibleUsers(value, form.getFieldValue("bindingDomain"));
                }, USER_SEARCH_DEBOUNCE_MS);
              }}
            />
          </Form.Item>
          <Space className={styles.searchActions}>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form>
        <Space wrap>
          <Segmented
            value={query.statsRange}
            options={DASHBOARD_STATS_RANGE_OPTIONS.map((item) => ({
              label: item.label,
              value: item.value,
            }))}
            onChange={(value) =>
              updateQuery({ statsRange: value as DashboardStatsRange })
            }
          />
          <Button icon={<ReloadOutlined />} onClick={() => void fetchData(query)}>
            刷新
          </Button>
          <Button
            icon={<DownloadOutlined />}
            loading={exporting}
            onClick={handleExport}
          >
            导出
          </Button>
        </Space>
      </div>

      <Table<MasterConsoleShopRow>
        rowKey={(record) => `${record.bindingDomain || "UNKNOWN"}-${record.shopId}`}
        columns={columns}
        dataSource={data}
        loading={loading}
        scroll={{ x: 1500 }}
        pagination={{
          current: query.pageNum,
          pageSize: query.pageSize,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
        }}
        onChange={(pagination, _filters, sorter) => {
          const singleSorter = Array.isArray(sorter) ? sorter[0] : sorter;
          updateQuery({
            pageNum: pagination.current || 1,
            pageSize: pagination.pageSize || 10,
            sortField:
              singleSorter?.field === "powerBalance" ? "powerBalance" : undefined,
            sortOrder: singleSorter?.order || undefined,
          });
        }}
      />
    </PageContainer>
  );
};

export default MasterConsole;
