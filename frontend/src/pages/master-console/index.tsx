import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button, Drawer, Form, Input, Select, Segmented, Space, Switch, Table, Tag, Tooltip, Typography } from "antd";
import { createStyles } from "antd-style";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import { DatabaseOutlined, DownloadOutlined, MessageOutlined, NotificationOutlined, ReloadOutlined, ShopOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { useAuth } from "@/contexts/AuthContext";
import { useResponsive } from "@/hooks";
import {
  exportMasterConsoleShops,
  getMasterConsoleShops,
  getMasterConsoleVisibleUsers,
  updateMasterConsoleShopOperationSettings,
  getMasterConsoleImAutoReplyLogs,
  getMasterConsoleImConversationHistory,
} from "@/services/master-console-api";
import { reportAdminBehaviorEvent } from "@/services/user-behavior-analytics-api";
import type {
  DashboardStatsRange,
  MasterConsoleQueryParam,
  MasterConsoleShopRow,
  ShopBindingDomain,
  ShopBindingStatus,
  VisibleUserOption,
} from "@/types";
import type { ImAutoReplyLogQuery, ImBroadcastSetting } from "@/types";
import ImReplyLogTable from "@/pages/im-auto-replies/components/ImReplyLogTable";
import BroadcastSettingModal, { WEEK_DAY_OPTIONS } from "./components/BroadcastSettingModal";

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
const RANGE_OPTIONS: { label: string; value: DashboardStatsRange }[] = [
  { label: "今日", value: "TODAY" },
  { label: "近7天", value: "LAST_7_DAYS" },
  { label: "近30天", value: "LAST_30_DAYS" },
];
const DEFAULT_QUERY: MasterConsoleQueryParam = {
  pageNum: 1,
  pageSize: 10,
  bindingDomain: "ALL",
  bindingStatus: "ALL",
  statsRange: "LAST_7_DAYS",
};

const useMasterConsoleStyles = createStyles(() => ({
  toolbar: { alignItems: "center", gap: 16 },
  searchForm: { alignItems: "center", gap: 16, "& .ant-form-item": { marginBottom: 0 } },
  toolbarActions: { alignItems: "center", marginLeft: "auto" },
}));

function formatDateTime(value?: string) {
  if (!value) return "-";
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format("YYYY-MM-DD HH:mm") : value;
}

function formatWeekDays(weekDays?: number[]) {
  if (!weekDays?.length) return "-";
  return weekDays
    .map((day) => WEEK_DAY_OPTIONS.find((option) => option.value === day)?.label)
    .filter(Boolean)
    .join("、");
}

function buildUserLabel(user: VisibleUserOption) {
  return user.displayName || user.nickname || user.username || user.userId;
}

const MasterConsole: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<MasterConsoleQueryParam>();
  const { styles } = usePageStyles();
  const { styles: pageStyles, cx } = useMasterConsoleStyles();
  const { isCompact } = useResponsive();
  const { currentUser } = useAuth();
  const canUseBindingDomain = currentUser?.activePortal === "ADMIN"
    && (currentUser.roles?.includes("super_admin") ?? false);
  const [query, setQuery] = useState<MasterConsoleQueryParam>(DEFAULT_QUERY);
  const [data, setData] = useState<MasterConsoleShopRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [userLoading, setUserLoading] = useState(false);
  const [savingShopIds, setSavingShopIds] = useState<number[]>([]);
  const [logShop, setLogShop] = useState<MasterConsoleShopRow>();
  const [broadcastShop, setBroadcastShop] = useState<MasterConsoleShopRow>();
  const userSearchTimer = useRef<number | null>(null);

  const buildRequestQuery = useCallback((value: MasterConsoleQueryParam) => {
    if (canUseBindingDomain) return value;
    const normalized = { ...value };
    delete normalized.bindingDomain;
    delete normalized.bindingStatus;
    delete normalized.targetUserId;
    return normalized;
  }, [canUseBindingDomain]);

  const fetchData = useCallback(async (value: MasterConsoleQueryParam) => {
    setLoading(true);
    try {
      const page = await getMasterConsoleShops(buildRequestQuery(value));
      setData(page.list || []);
      setTotal(page.total || 0);
    } catch {
      message.error("加载总控台失败");
    } finally {
      setLoading(false);
    }
  }, [buildRequestQuery]);

  const fetchVisibleUsers = useCallback(async (keyword?: string, bindingDomain?: string) => {
    if (!canUseBindingDomain) return;
    setUserLoading(true);
    try {
      setVisibleUsers(await getMasterConsoleVisibleUsers(keyword, bindingDomain));
    } finally {
      setUserLoading(false);
    }
  }, [canUseBindingDomain]);

  useEffect(() => { void fetchData(query); }, [fetchData, query]);
  useEffect(() => {
    if (canUseBindingDomain) void fetchVisibleUsers(undefined, query.bindingDomain);
  }, [canUseBindingDomain, fetchVisibleUsers, query.bindingDomain]);

  const updateQuery = useCallback((patch: Partial<MasterConsoleQueryParam>) => {
    setQuery((previous) => ({ ...previous, ...patch, pageNum: patch.pageNum ?? 1 }));
  }, []);

  const saveImSwitch = useCallback(async (record: MasterConsoleShopRow, enabled: boolean) => {
    setSavingShopIds((ids) => ids.includes(record.shopId) ? ids : [...ids, record.shopId]);
    try {
      const saved = await updateMasterConsoleShopOperationSettings(record.shopId, { autoReplyEnabled: enabled });
      setData((rows) => rows.map((row) => row.shopId === record.shopId
        ? { ...row, autoReplyEnabled: saved.autoReplyEnabled }
        : row));
      message.success("IM 自动回复开关已保存");
    } finally {
      setSavingShopIds((ids) => ids.filter((id) => id !== record.shopId));
    }
  }, []);

  const loadShopReplyLogs = useCallback((params: ImAutoReplyLogQuery) => {
    if (!logShop) throw new Error("未选择门店");
    return getMasterConsoleImAutoReplyLogs(logShop.shopId, params);
  }, [logShop]);

  const loadShopConversationHistory = useCallback((
    conversationId: number,
    params?: { cursorTime?: string; cursorId?: number; pageSize?: number },
  ) => {
    if (!logShop) throw new Error("未选择门店");
    return getMasterConsoleImConversationHistory(logShop.shopId, conversationId, params);
  }, [logShop]);

  const columns = useMemo<ColumnsType<MasterConsoleShopRow>>(() => [
    {
      title: "门店", key: "shop", width: 260, fixed: isCompact ? undefined : "left",
      render: (_, record) => <Space direction="vertical" size={2}>
        <Space size={6}><ShopOutlined /><Text strong>{record.shopName || "-"}</Text></Space>
        <Text type="secondary">门店ID：{record.poiId || "-"}</Text>
        {record.address ? <Tooltip title={record.address}><Text type="secondary" ellipsis style={{ maxWidth: 220 }}>{record.address}</Text></Tooltip> : null}
      </Space>,
    },
    {
      title: "绑定", key: "binding", width: 180,
      render: (_, record) => <Space direction="vertical" size={4}>
        <Space size={6} wrap><Tag color={record.bindingDomain === "SALES" ? "blue" : "green"}>{record.bindingDomainName || "-"}</Tag><Tag>{record.bindingStatusName || "-"}</Tag></Space>
        <Text type="secondary">用户：{record.boundUserName || record.boundUserId || "-"}</Text>
      </Space>,
    },
    {
      title: "所属网销", key: "sales", width: 170,
      render: (_, record) => <Space direction="vertical" size={2}><Text>{record.salesBoundUserName || record.salesBoundUserId || "-"}</Text>{record.salesBoundUserPhone ? <Text type="secondary">{record.salesBoundUserPhone}</Text> : null}</Space>,
    },
    {
      title: "授权", key: "auth", width: 130,
      render: (_, record) => <Tag color={record.reauthRequired ? "red" : "green"}>{record.authStatusText || "有效"}</Tag>,
    },
    {
      title: "算力", key: "powerBalance", width: 160, sorter: true,
      sortOrder: query.sortField === "powerBalance" ? (query.sortOrder as SortOrder) : null,
      render: (_, record) => <Space direction="vertical" size={0}><Text>余额：{record.powerBalance ?? 0}</Text><Text type="secondary">本期消耗：{record.currentConsumedPower ?? 0}</Text></Space>,
    },
    { title: "权益到期", dataIndex: "rightExpireAt", width: 160, render: formatDateTime },
    {
      title: "IM 自动回复", key: "autoReplyEnabled", width: 150,
      render: (_, record) => <Switch
        checked={Boolean(record.autoReplyEnabled)}
        loading={savingShopIds.includes(record.shopId)}
        onChange={(checked) => void saveImSwitch(record, checked)}
      />,
    },
    {
      title: "消息群发", key: "broadcast", width: 230,
      render: (_, record) => {
        const setting = record.broadcastSetting;
        const status = setting?.status ?? "NONE";
        if (status === "NONE") {
          return <Space direction="vertical" size={2}>
            <Tag>{setting?.statusName || "未配置"}</Tag>
            <Text type="secondary">累计 {setting?.totalSentCount ?? 0} 条</Text>
          </Space>;
        }
        // ONCE_COMPLETED 的配置内容仍保留，一并展示，运营才能看出这家店刚跑完什么。
        const completed = status === "ONCE_COMPLETED";
        return <Space direction="vertical" size={2}>
          <Space size={6} wrap>
            {completed
              ? <Tag color="blue">{setting?.statusName}</Tag>
              : <Tag color="gold">{formatWeekDays(setting?.weekDays)}</Tag>}
            <Tag>{setting?.repeatMode === "ONCE" ? "一次" : "每次"}</Tag>
          </Space>
          <Text type="secondary">
            {completed
              ? `执行于 ${setting?.lastTriggerDate || "-"} ${setting?.sendTime || ""}`
              : `${setting?.sendTime || "-"}`} · 累计 {setting?.totalSentCount ?? 0} 条
          </Text>
          <Tooltip title={setting?.content}>
            <Text type="secondary" ellipsis style={{ maxWidth: 210 }}>{setting?.content || "-"}</Text>
          </Tooltip>
        </Space>;
      },
    },
    { title: "绑定时间", dataIndex: "bindTime", width: 160, render: formatDateTime },
    {
      title: "操作", key: "actions", width: 280, fixed: isCompact ? undefined : "right",
      render: (_, record) => <Space size={0}>
        <Button type="link" icon={<MessageOutlined />} onClick={() => setLogShop(record)}>回复日志</Button>
        <Button type="link" icon={<NotificationOutlined />} onClick={() => setBroadcastShop(record)}>群发配置</Button>
        <Button type="link" icon={<DatabaseOutlined />} onClick={() => {
          reportAdminBehaviorEvent("PC_KNOWLEDGE_VIEW", record.shopId, { portalType: currentUser?.activePortal, entrySource: "MASTER_CONSOLE" });
          navigate(`/merchant-knowledge?shopId=${record.shopId}`);
        }}>知识库</Button>
      </Space>,
    },
  ], [currentUser?.activePortal, isCompact, navigate, query.sortField, query.sortOrder, saveImSwitch, savingShopIds]);

  const applyBroadcastSetting = useCallback((shopId: number, setting: ImBroadcastSetting) => {
    setData((rows) => rows.map((row) => row.shopId === shopId
      ? { ...row, broadcastSetting: setting }
      : row));
  }, []);

  return <PageContainer title="总控台">
    <div className={cx(styles.toolbar, pageStyles.toolbar)}>
      <Form form={form} className={cx(styles.searchForm, pageStyles.searchForm)} initialValues={DEFAULT_QUERY} onFinish={() => {
        const values = form.getFieldsValue();
        updateQuery({
          keyword: values.keyword?.trim() || undefined,
          targetUserId: canUseBindingDomain ? values.targetUserId : undefined,
          bindingDomain: canUseBindingDomain ? values.bindingDomain || "ALL" : undefined,
          bindingStatus: canUseBindingDomain ? values.bindingStatus || "ALL" : undefined,
        });
      }}>
        <Form.Item name="keyword"><Input allowClear placeholder="搜索门店名称 / 门店ID" /></Form.Item>
        {canUseBindingDomain && <Form.Item name="bindingDomain"><Select options={BINDING_DOMAIN_OPTIONS} style={{ width: 130 }} onChange={(value) => { form.setFieldValue("targetUserId", undefined); void fetchVisibleUsers(undefined, value); }} /></Form.Item>}
        {canUseBindingDomain && <Form.Item name="bindingStatus"><Select options={BINDING_STATUS_OPTIONS} style={{ width: 130 }} /></Form.Item>}
        {canUseBindingDomain && <Form.Item name="targetUserId"><Select allowClear showSearch filterOption={false} loading={userLoading} placeholder="所属用户" style={{ width: 180 }} options={visibleUsers.map((user) => ({ label: buildUserLabel(user), value: user.userId }))} onSearch={(value) => {
          if (userSearchTimer.current) window.clearTimeout(userSearchTimer.current);
          userSearchTimer.current = window.setTimeout(() => void fetchVisibleUsers(value, form.getFieldValue("bindingDomain")), USER_SEARCH_DEBOUNCE_MS);
        }} /></Form.Item>}
        <Space className={styles.searchActions}><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.setFieldsValue(DEFAULT_QUERY); setQuery(DEFAULT_QUERY); }}>重置</Button></Space>
      </Form>
      <Space wrap className={pageStyles.toolbarActions}>
        <Segmented value={query.statsRange} options={RANGE_OPTIONS} onChange={(value) => updateQuery({ statsRange: value as DashboardStatsRange })} />
        <Button icon={<ReloadOutlined />} onClick={() => void fetchData(query)}>刷新</Button>
        <Button icon={<DownloadOutlined />} loading={exporting} onClick={async () => { setExporting(true); try { await exportMasterConsoleShops(buildRequestQuery(query)); } finally { setExporting(false); } }}>导出</Button>
      </Space>
    </div>
    <Table<MasterConsoleShopRow>
      rowKey={(record) => `${record.bindingDomain || "UNKNOWN"}-${record.shopId}`}
      columns={columns}
      dataSource={data}
      loading={loading}
      scroll={{ x: 1500 }}
      pagination={{ current: query.pageNum, pageSize: query.pageSize, total, showSizeChanger: true, showTotal: (value) => `共 ${value} 条` }}
      onChange={(pagination, _filters, sorter) => {
        const value = Array.isArray(sorter) ? sorter[0] : sorter;
        updateQuery({ pageNum: pagination.current || 1, pageSize: pagination.pageSize || 10, sortField: value?.field === "powerBalance" ? "powerBalance" : undefined, sortOrder: value?.order || undefined });
      }}
    />
    <Drawer
      title={`${logShop?.shopName || "门店"} · 回复日志`}
      open={Boolean(logShop)}
      onClose={() => setLogShop(undefined)}
      width="88vw"
      destroyOnHidden
    >
      {logShop ? <ImReplyLogTable
        compact
        showShopFilter={false}
        showRagColumn={false}
        loadPage={loadShopReplyLogs}
        loadHistory={loadShopConversationHistory}
      /> : null}
    </Drawer>
    <BroadcastSettingModal
      open={Boolean(broadcastShop)}
      shopId={broadcastShop?.shopId}
      shopName={broadcastShop?.shopName}
      onClose={() => setBroadcastShop(undefined)}
      onSaved={applyBroadcastSetting}
    />
  </PageContainer>;
};

export default MasterConsole;
