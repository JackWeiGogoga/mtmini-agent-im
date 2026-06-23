import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  Drawer,
  Form,
  Image,
  Input,
  Select,
  Segmented,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import { createStyles } from "antd-style";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import {
  DatabaseOutlined,
  DownloadOutlined,
  ReloadOutlined,
  ShopOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { useAuth } from "@/contexts/AuthContext";
import { useResponsive } from "@/hooks";
import {
  exportMasterConsoleShops,
  getMasterConsoleCommentReplyLogs,
  getMasterConsoleShops,
  getMasterConsoleVisibleUsers,
  updateMasterConsoleShopOperationSettings,
} from "@/services/master-console-api";
import type {
  CommentReplyLogRecord,
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

const useMasterConsoleStyles = createStyles(() => ({
  toolbar: {
    alignItems: "center",
    gap: 16,
  },
  searchForm: {
    alignItems: "center",
    gap: 16,
    "& .ant-form-item": {
      marginBottom: 0,
    },
  },
  toolbarActions: {
    alignItems: "center",
    marginLeft: "auto",
    "& .ant-space-item": {
      display: "flex",
      alignItems: "center",
    },
  },
  logDrawerHeader: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 16,
    marginBottom: 16,
  },
  logShopMeta: {
    display: "flex",
    flexDirection: "column",
    gap: 4,
  },
  logText: {
    marginBottom: 0,
    whiteSpace: "pre-wrap",
  },
  logImageStrip: {
    display: "flex",
    flexWrap: "wrap",
    gap: 8,
  },
  logScore: {
    color: "#c18a16",
    whiteSpace: "nowrap",
  },
}));

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

function normalizeSentiment(record: CommentReplyLogRecord) {
  const sentiment = String(record.sentiment || "").toUpperCase();
  if (sentiment === "GOOD" || sentiment === "BAD" || sentiment === "NEUTRAL") {
    return sentiment;
  }
  const score = Number(record.score);
  if (score >= 0 && score <= 2) {
    return "BAD";
  }
  if (score >= 4 && score <= 5) {
    return "GOOD";
  }
  return "NEUTRAL";
}

function renderSentimentTag(record: CommentReplyLogRecord) {
  const sentiment = normalizeSentiment(record);
  if (sentiment === "BAD") {
    return <Tag color="red">差评</Tag>;
  }
  if (sentiment === "GOOD") {
    return <Tag color="green">好评</Tag>;
  }
  return <Tag color="default">中评</Tag>;
}

function renderReplyStatus(status?: string) {
  const normalizedStatus = String(status || "").toUpperCase();
  const statusMap: Record<string, { color: string; text: string }> = {
    SUCCESS: { color: "green", text: "已回复" },
    PENDING: { color: "processing", text: "待处理" },
    FAILED: { color: "red", text: "失败" },
    SKIPPED: { color: "default", text: "已跳过" },
  };
  const config = statusMap[normalizedStatus] || {
    color: "default",
    text: status || "未知",
  };
  return <Tag color={config.color}>{config.text}</Tag>;
}

const MasterConsole: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<MasterConsoleQueryParam>();
  const { styles } = usePageStyles();
  const { styles: masterConsoleStyles, cx } = useMasterConsoleStyles();
  const { isCompact } = useResponsive();
  const { currentUser } = useAuth();
  const isSuperAdmin = currentUser?.roles?.includes("super_admin") ?? false;
  const canUseBindingDomain =
    currentUser?.activePortal === "ADMIN" && isSuperAdmin;
  const [query, setQuery] = useState<MasterConsoleQueryParam>(DEFAULT_QUERY);
  const [data, setData] = useState<MasterConsoleShopRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [operationSettingShopIds, setOperationSettingShopIds] = useState<number[]>([]);
  const [logDrawerOpen, setLogDrawerOpen] = useState(false);
  const [logShop, setLogShop] = useState<MasterConsoleShopRow | null>(null);
  const [logRecords, setLogRecords] = useState<CommentReplyLogRecord[]>([]);
  const [logTotal, setLogTotal] = useState(0);
  const [logPageNum, setLogPageNum] = useState(1);
  const [logPageSize, setLogPageSize] = useState(20);
  const [logLoading, setLogLoading] = useState(false);
  const userSearchTimer = useRef<number | null>(null);

  const activeRange = useMemo(
    () =>
      DASHBOARD_STATS_RANGE_OPTIONS.find((item) => item.value === query.statsRange) ||
      DASHBOARD_STATS_RANGE_OPTIONS[1],
    [query.statsRange],
  );

  const fetchVisibleUsers = useCallback(
    async (keyword?: string, bindingDomain?: string) => {
      if (!canUseBindingDomain) {
        setVisibleUsers([]);
        return;
      }
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
    [canUseBindingDomain],
  );

  const buildRequestQuery = useCallback(
    (nextQuery: MasterConsoleQueryParam): MasterConsoleQueryParam => {
      if (canUseBindingDomain) {
        return nextQuery;
      }
      const requestQuery = { ...nextQuery };
      delete requestQuery.bindingDomain;
      delete requestQuery.bindingStatus;
      delete requestQuery.targetUserId;
      return requestQuery;
    },
    [canUseBindingDomain],
  );

  const fetchData = useCallback(
    async (nextQuery: MasterConsoleQueryParam) => {
      setLoading(true);
      try {
        const page = await getMasterConsoleShops(buildRequestQuery(nextQuery));
        setData(page.list || []);
        setTotal(page.total || 0);
      } catch (error) {
        console.error("Failed to load master console:", error);
        message.error("加载总控台失败");
      } finally {
        setLoading(false);
      }
    },
    [buildRequestQuery],
  );

  useEffect(() => {
    void fetchData(query);
  }, [fetchData, query]);

  useEffect(() => {
    if (canUseBindingDomain) {
      void fetchVisibleUsers(undefined, query.bindingDomain);
    }
  }, [canUseBindingDomain, fetchVisibleUsers, query.bindingDomain]);

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
      targetUserId: canUseBindingDomain ? values.targetUserId : undefined,
      bindingDomain: canUseBindingDomain
        ? values.bindingDomain || "ALL"
        : undefined,
      bindingStatus: canUseBindingDomain
        ? values.bindingStatus || "ALL"
        : undefined,
    });
  }, [canUseBindingDomain, form, updateQuery]);

  const handleReset = useCallback(() => {
    form.setFieldsValue(DEFAULT_QUERY);
    setQuery(DEFAULT_QUERY);
  }, [form]);

  const handleExport = useCallback(async () => {
    setExporting(true);
    try {
      await exportMasterConsoleShops(buildRequestQuery(query));
    } finally {
      setExporting(false);
    }
  }, [buildRequestQuery, query]);

  const handleOperationSettingsChange = useCallback(
    async (
      record: MasterConsoleShopRow,
      patch: Pick<
        MasterConsoleShopRow,
        "autoReplyEnabled" | "negativeCommentAppealEnabled"
      >,
    ) => {
      if (!record.shopId) {
        return;
      }
      const nextSettings = {
        autoReplyEnabled:
          patch.autoReplyEnabled ?? (record.autoReplyEnabled !== false),
        negativeCommentAppealEnabled:
          patch.negativeCommentAppealEnabled ??
          Boolean(record.negativeCommentAppealEnabled),
      };
      setOperationSettingShopIds((prev) =>
        prev.includes(record.shopId) ? prev : [...prev, record.shopId],
      );
      try {
        const savedSettings = await updateMasterConsoleShopOperationSettings(
          record.shopId,
          nextSettings,
        );
        setData((prev) =>
          prev.map((item) =>
            item.shopId === record.shopId
              ? {
                  ...item,
                  autoReplyEnabled: savedSettings.autoReplyEnabled,
                  negativeCommentAppealEnabled:
                    savedSettings.negativeCommentAppealEnabled,
                }
              : item,
          ),
        );
        message.success("开关已保存");
      } catch (error) {
        console.error("Failed to update operation settings:", error);
      } finally {
        setOperationSettingShopIds((prev) =>
          prev.filter((shopId) => shopId !== record.shopId),
        );
      }
    },
    [],
  );

  const loadCommentReplyLogs = useCallback(
    async (
      record: MasterConsoleShopRow,
      nextPageNum = logPageNum,
      nextPageSize = logPageSize,
    ) => {
      setLogLoading(true);
      try {
        const page = await getMasterConsoleCommentReplyLogs(
          record.shopId,
          record.bindingDomain,
          nextPageNum,
          nextPageSize,
        );
        setLogRecords(page.list || []);
        setLogTotal(page.total || 0);
        setLogPageNum(page.pageNum || nextPageNum);
        setLogPageSize(page.pageSize || nextPageSize);
      } catch (error) {
        console.error("Failed to load comment reply logs:", error);
        message.error("加载回复日志失败");
      } finally {
        setLogLoading(false);
      }
    },
    [logPageNum, logPageSize],
  );

  const openLogDrawer = useCallback(
    async (record: MasterConsoleShopRow) => {
      setLogShop(record);
      setLogDrawerOpen(true);
      setLogPageNum(1);
      setLogPageSize(20);
      setLogRecords([]);
      setLogTotal(0);
      await loadCommentReplyLogs(record, 1, 20);
    },
    [loadCommentReplyLogs],
  );

  const closeLogDrawer = useCallback(() => {
    setLogDrawerOpen(false);
    setLogShop(null);
    setLogRecords([]);
    setLogTotal(0);
    setLogPageNum(1);
    setLogPageSize(20);
  }, []);

  const logColumns = useMemo<ColumnsType<CommentReplyLogRecord>>(
    () => [
      {
        title: "时间",
        key: "time",
        width: 150,
        render: (_, record) => (
          <Space direction="vertical" size={2}>
            <Text>{formatDateTime(record.replyTime || record.commentTime)}</Text>
            <Text type="secondary">评价：{formatDateTime(record.commentTime)}</Text>
          </Space>
        ),
      },
      {
        title: "评分",
        key: "score",
        width: 120,
        render: (_, record) => {
          const score = Number(record.score);
          const scoreText =
            Number.isFinite(score) && score >= 0 && score <= 5
              ? `${"★".repeat(score)}${"☆".repeat(5 - score)}`
              : "--星";
          return (
            <Space direction="vertical" size={4}>
              {renderSentimentTag(record)}
              <Text className={masterConsoleStyles.logScore}>{scoreText}</Text>
            </Space>
          );
        },
      },
      {
        title: "顾客消息",
        key: "customerMessage",
        width: 280,
        render: (_, record) => (
          <Space direction="vertical" size={8} style={{ width: "100%" }}>
            <Typography.Paragraph
              className={masterConsoleStyles.logText}
              ellipsis={{ rows: 3, expandable: true }}
            >
              {record.customerMessage || "暂无顾客消息"}
            </Typography.Paragraph>
            {record.addComment ? (
              <Typography.Paragraph
                className={masterConsoleStyles.logText}
                type="secondary"
                ellipsis={{ rows: 2, expandable: true }}
              >
                追评：{record.addComment}
              </Typography.Paragraph>
            ) : null}
          </Space>
        ),
      },
      {
        title: "图片",
        dataIndex: "imageUrls",
        key: "imageUrls",
        width: 180,
        render: (imageUrls?: string[]) =>
          imageUrls?.length ? (
            <Image.PreviewGroup>
              <div className={masterConsoleStyles.logImageStrip}>
                {imageUrls.slice(0, 6).map((url, index) => (
                  <Image
                    key={`${url}-${index}`}
                    src={url}
                    width={48}
                    height={48}
                    style={{ objectFit: "cover", borderRadius: 6 }}
                  />
                ))}
              </div>
            </Image.PreviewGroup>
          ) : (
            <Text type="secondary">无图片</Text>
          ),
      },
      {
        title: "AI 回复",
        key: "aiReplyText",
        width: 280,
        render: (_, record) => (
          <Space direction="vertical" size={6} style={{ width: "100%" }}>
            {renderReplyStatus(record.autoReplyStatus)}
            <Typography.Paragraph
              className={masterConsoleStyles.logText}
              ellipsis={{ rows: 3, expandable: true }}
            >
              {record.aiReplyText || "暂无 AI 回复"}
            </Typography.Paragraph>
          </Space>
        ),
      },
      {
        title: "AI 分析",
        dataIndex: "analysisText",
        key: "analysisText",
        width: 220,
        render: (value?: string) => (
          <Typography.Paragraph
            className={masterConsoleStyles.logText}
            ellipsis={{ rows: 3, expandable: true }}
          >
            {value || "AI 分析结果"}
          </Typography.Paragraph>
        ),
      },
    ],
    [
      masterConsoleStyles.logImageStrip,
      masterConsoleStyles.logScore,
      masterConsoleStyles.logText,
    ],
  );

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
        title: "系统操作",
        key: "operationSettings",
        width: 190,
        render: (_, record) => {
          const saving = operationSettingShopIds.includes(record.shopId);
          return (
            <Space direction="vertical" size={6}>
              <Space size={8}>
                <Text>AI回复</Text>
                <Switch
                  size="small"
                  checked={record.autoReplyEnabled !== false}
                  loading={saving}
                  onChange={(checked) =>
                    void handleOperationSettingsChange(record, {
                      autoReplyEnabled: checked,
                    })
                  }
                />
              </Space>
              <Space size={8}>
                <Text>差评处理/申诉</Text>
                <Switch
                  size="small"
                  checked={Boolean(record.negativeCommentAppealEnabled)}
                  loading={saving}
                  onChange={(checked) =>
                    void handleOperationSettingsChange(record, {
                      negativeCommentAppealEnabled: checked,
                    })
                  }
                />
              </Space>
            </Space>
          );
        },
      },
      {
        title: "绑定时间",
        dataIndex: "bindTime",
        key: "bindTime",
        width: 160,
        render: (value?: string) => formatDateTime(value),
      },
      {
        title: "操作",
        key: "actions",
        width: 210,
        fixed: isCompact ? undefined : "right",
        render: (_, record) => (
          <Space size={4}>
            <Button
              type="link"
              icon={<UnorderedListOutlined />}
              onClick={() => void openLogDrawer(record)}
            >
              回复日志
            </Button>
            <Button
              type="link"
              icon={<DatabaseOutlined />}
              onClick={() => navigate(`/merchant-knowledge?shopId=${record.shopId}`)}
            >
              知识库
            </Button>
          </Space>
        ),
      },
    ],
    [
      activeRange.compareLabel,
      handleOperationSettingsChange,
      isCompact,
      navigate,
      openLogDrawer,
      operationSettingShopIds,
      query.sortField,
      query.sortOrder,
    ],
  );

  return (
    <PageContainer title="总控台">
      <div className={cx(styles.toolbar, masterConsoleStyles.toolbar)}>
        <Form
          form={form}
          className={cx(styles.searchForm, masterConsoleStyles.searchForm)}
          initialValues={DEFAULT_QUERY}
          onFinish={handleSearch}
        >
          <Form.Item name="keyword">
            <Input allowClear placeholder="搜索门店名称 / 门店ID" />
          </Form.Item>
          {canUseBindingDomain ? (
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
          ) : null}
          {canUseBindingDomain ? (
            <Form.Item name="bindingStatus">
              <Select options={BINDING_STATUS_OPTIONS} style={{ width: 130 }} />
            </Form.Item>
          ) : null}
          {canUseBindingDomain ? (
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
          ) : null}
          <Space className={styles.searchActions}>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form>
        <Space wrap className={masterConsoleStyles.toolbarActions}>
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
        scroll={{ x: 1900 }}
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

      <Drawer
        title={logShop ? `${logShop.shopName || logShop.poiId || "门店"} 回复日志` : "回复日志"}
        open={logDrawerOpen}
        width={isCompact ? "100%" : 1040}
        onClose={closeLogDrawer}
        destroyOnHidden
      >
        <div className={masterConsoleStyles.logDrawerHeader}>
          <div className={masterConsoleStyles.logShopMeta}>
            <Text strong>{logShop?.shopName || "-"}</Text>
            <Text type="secondary">门店ID：{logShop?.poiId || "-"}</Text>
          </div>
          <Button
            icon={<ReloadOutlined />}
            loading={logLoading}
            onClick={() => {
              if (logShop) {
                void loadCommentReplyLogs(logShop);
              }
            }}
          >
            刷新
          </Button>
        </div>

        <Table<CommentReplyLogRecord>
          rowKey={(record) => `${record.id || record.commentId}`}
          loading={logLoading}
          columns={logColumns}
          dataSource={logRecords}
          size={isCompact ? "small" : "middle"}
          scroll={{ x: 1230 }}
          pagination={{
            current: logPageNum,
            pageSize: logPageSize,
            total: logTotal,
            showSizeChanger: true,
            showQuickJumper: !isCompact,
            showTotal: (value) => `共 ${value} 条`,
            onChange: (page, size) => {
              if (!logShop) {
                return;
              }
              void loadCommentReplyLogs(logShop, page, size);
            },
          }}
          locale={{ emptyText: "暂无回复日志" }}
        />
      </Drawer>
    </PageContainer>
  );
};

export default MasterConsole;
