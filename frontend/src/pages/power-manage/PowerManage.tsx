import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  TimePicker,
  Tooltip,
  Typography,
} from "antd";
import {
  EyeOutlined,
  GiftOutlined,
  ReloadOutlined,
  ShopOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import { Navigate } from "react-router-dom";
import dayjs from "dayjs";
import { message } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import {
  getPowerAccountSummary,
  getPowerConsumeRecords,
  getPowerGrantRecords,
  getPowerManagementAccounts,
  getPowerRechargeRecords,
} from "@/services/power-manage-api";
import {
  createShopPowerGrant,
  getVisibleShopUsers,
} from "@/services/shop-manage-api";
import { useResponsive } from "@/hooks";
import { usePermission } from "@/hooks/usePermission";
import type {
  AdminPowerGrantCreateParam,
  AdminPowerGrantRecord,
  PowerAccountSummary,
  PowerConsumeRecord,
  PowerDetailRecordQueryParam,
  PowerManagementRow,
  PowerRiskStatus,
  PowerRechargeRecord,
  ShopBindingDomain,
  ShopBindingStatus,
  VisibleUserOption,
} from "@/types";
import {
  getResponsiveControlStyle,
  getResponsiveDrawerWidth,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";
import { formatPowerAmount, formatPowerWithUnit } from "@/utils/power-format";

const { Text } = Typography;
const { RangePicker } = DatePicker;
const USER_SEARCH_DEBOUNCE_MS = 300;
const DETAIL_DEFAULT_PAGE_SIZE = 10;

type SearchFormValues = {
  keyword?: string;
  targetUserId?: string;
  bindingDomain?: ShopBindingDomain;
  bindingStatus?: ShopBindingStatus;
  riskStatus?: PowerRiskStatus;
};

type DetailTabKey = "recharges" | "grants" | "consumes";

type DetailFilterValues = {
  timeRange?: [Dayjs, Dayjs];
  startDate?: Dayjs;
  startClock?: Dayjs;
  endDate?: Dayjs;
  endClock?: Dayjs;
};

type DetailPaginationState = {
  pageNum: number;
  pageSize: number;
  total: number;
};

type DetailPaginationOverrides = {
  recharges?: Pick<DetailPaginationState, "pageNum" | "pageSize">;
  grants?: Pick<DetailPaginationState, "pageNum" | "pageSize">;
  consumes?: Pick<DetailPaginationState, "pageNum" | "pageSize">;
};

function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  const result = dayjs(value);
  return result.isValid() ? result.format("YYYY-MM-DD HH:mm:ss") : value;
}

function formatFen(value?: number) {
  if (value == null) {
    return "-";
  }
  return `¥${(value / 100).toFixed(2)}`;
}

function renderRiskTag(riskStatus: PowerRiskStatus) {
  if (riskStatus === "CRITICAL") {
    return <Tag color="red">不足执行</Tag>;
  }
  if (riskStatus === "WARNING") {
    return <Tag color="orange">低于 20000</Tag>;
  }
  return <Tag color="green">正常</Tag>;
}

const riskOptions = [
  { label: "正常", value: "NORMAL" },
  { label: "低于 20000", value: "WARNING" },
  { label: "不足执行", value: "CRITICAL" },
] satisfies { label: string; value: PowerRiskStatus }[];

const bindingDomainOptions = [
  { label: "全部", value: "ALL" },
  { label: "普通商户", value: "ORDINARY" },
  { label: "网销用户", value: "SALES" },
] satisfies { label: string; value: ShopBindingDomain }[];

const bindingStatusOptions = [
  { label: "全部状态", value: "ALL" },
  { label: "未绑定", value: "UNBOUND" },
  { label: "已绑定", value: "BOUND" },
  { label: "双绑定", value: "BOTH" },
] satisfies { label: string; value: ShopBindingStatus }[];

function renderBindingDomainTag(record: {
  bindingDomain?: ShopBindingDomain;
  bindingDomainName?: string;
  bindingStatus?: ShopBindingStatus;
  bindingStatusName?: string;
  ordinaryBindingId?: number | null;
  salesBindingId?: number | null;
}) {
  if (record.bindingStatus === "UNBOUND") {
    return <Tag>未绑定</Tag>;
  }
  if (record.bindingStatus === "BOTH") {
    return <Tag color="blue">双绑定</Tag>;
  }
  if (record.ordinaryBindingId && record.salesBindingId) {
    return <Tag color="blue">双绑定</Tag>;
  }
  if (record.bindingDomain === "SALES") {
    return <Tag color="purple">{record.bindingDomainName || "网销用户"}</Tag>;
  }
  if (record.bindingDomain === "ORDINARY") {
    return <Tag color="green">{record.bindingDomainName || "普通商户"}</Tag>;
  }
  return <Tag>{record.bindingDomainName || "未知"}</Tag>;
}

function renderBoundUsers(record: PowerManagementRow) {
  const items = [
    record.ordinaryBoundUserName || record.ordinaryBoundUserId
      ? {
          label: "普通",
          value: record.ordinaryBoundUserName || record.ordinaryBoundUserId,
        }
      : null,
    record.salesBoundUserName || record.salesBoundUserId
      ? {
          label: "网销",
          value: record.salesBoundUserName || record.salesBoundUserId,
        }
      : null,
  ].filter((item): item is { label: string; value: string } => Boolean(item));

  if (items.length > 0) {
    return (
      <Space orientation="vertical" size={2}>
        {items.map((item) => (
          <Text key={item.label}>
            {item.label}：{item.value}
          </Text>
        ))}
      </Space>
    );
  }
  return record.boundUserName || record.boundUserId || "-";
}

type SummaryMetricCardProps = {
  title: string;
  value: React.ReactNode;
};

const SummaryMetricCard: React.FC<SummaryMetricCardProps> = ({
  title,
  value,
}) => (
  <div
    style={{
      height: "100%",
      padding: "12px 14px",
      borderRadius: 10,
      background: "#fafafa",
      border: "1px solid #f0f0f0",
    }}
  >
    <Space orientation="vertical" size={2} style={{ width: "100%" }}>
      <Text type="secondary">{title}</Text>
      <Text strong style={{ lineHeight: 1.2 }}>
        {value}
      </Text>
    </Space>
  </div>
);

function buildDetailPaginationState(): DetailPaginationState {
  return {
    pageNum: 1,
    pageSize: DETAIL_DEFAULT_PAGE_SIZE,
    total: 0,
  };
}

function mergeDateAndClock(
  date?: Dayjs,
  clock?: Dayjs,
  mode: "start" | "end" = "start",
) {
  if (!date) {
    return undefined;
  }

  if (!clock) {
    return mode === "start" ? date.startOf("day") : date.endOf("day");
  }

  return date.hour(clock.hour()).minute(clock.minute()).second(clock.second());
}

const PowerManage: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const { isSuperAdmin } = usePermission();
  const [searchForm] = Form.useForm<SearchFormValues>();
  const [grantForm] = Form.useForm<AdminPowerGrantCreateParam>();
  const [detailFilterForm] = Form.useForm<DetailFilterValues>();

  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [grantSubmitting, setGrantSubmitting] = useState(false);
  const [visibleUserLoading, setVisibleUserLoading] = useState(false);

  const [rows, setRows] = useState<PowerManagementRow[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [queryKeyword, setQueryKeyword] = useState("");
  const [queryTargetUserId, setQueryTargetUserId] = useState<string>();
  const [queryBindingDomain, setQueryBindingDomain] =
    useState<ShopBindingDomain>("ALL");
  const [queryBindingStatus, setQueryBindingStatus] =
    useState<ShopBindingStatus>("ALL");
  const [queryRiskStatus, setQueryRiskStatus] = useState<PowerRiskStatus>();
  const [balanceSortOrder, setBalanceSortOrder] = useState<SortOrder>(null);

  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const visibleUserSearchTimerRef = useRef<number | null>(null);

  const [detailOpen, setDetailOpen] = useState(false);
  const [activeDetailTab, setActiveDetailTab] =
    useState<DetailTabKey>("recharges");
  const [activeShop, setActiveShop] = useState<PowerManagementRow | null>(null);
  const [grantModalOpen, setGrantModalOpen] = useState(false);
  const [grantTargetShop, setGrantTargetShop] =
    useState<PowerManagementRow | null>(null);
  const [summary, setSummary] = useState<PowerAccountSummary | null>(null);
  const [recharges, setRecharges] = useState<PowerRechargeRecord[]>([]);
  const [grants, setGrants] = useState<AdminPowerGrantRecord[]>([]);
  const [consumes, setConsumes] = useState<PowerConsumeRecord[]>([]);
  const [rechargePagination, setRechargePagination] = useState(
    buildDetailPaginationState,
  );
  const [grantPagination, setGrantPagination] = useState(
    buildDetailPaginationState,
  );
  const [consumePagination, setConsumePagination] = useState(
    buildDetailPaginationState,
  );

  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getPowerManagementAccounts({
        keyword: queryKeyword.trim() || undefined,
        targetUserId: queryTargetUserId,
        bindingDomain: queryBindingDomain,
        bindingStatus: queryBindingStatus,
        riskStatus: queryRiskStatus,
        sortField: balanceSortOrder ? "balance" : undefined,
        sortOrder: balanceSortOrder || undefined,
        pageNum,
        pageSize,
      });
      setRows(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load power management rows:", error);
      message.error("加载算力列表失败");
    } finally {
      setLoading(false);
    }
  }, [
    balanceSortOrder,
    pageNum,
    pageSize,
    queryKeyword,
    queryTargetUserId,
    queryBindingDomain,
    queryBindingStatus,
    queryRiskStatus,
  ]);

  const loadVisibleUsers = useCallback(
    async (keyword?: string, bindingDomainOverride?: ShopBindingDomain) => {
      setVisibleUserLoading(true);
      try {
        const response = await getVisibleShopUsers(
          keyword,
          bindingDomainOverride || queryBindingDomain,
        );
        setVisibleUsers(response);
      } catch (error) {
        console.error("Failed to load visible users:", error);
      } finally {
        setVisibleUserLoading(false);
      }
    },
    [queryBindingDomain],
  );

  const buildDetailRecordQuery = useCallback(
    (
      shopId: number,
      pagination: Pick<DetailPaginationState, "pageNum" | "pageSize">,
    ): PowerDetailRecordQueryParam => {
      const timeRange = detailFilterForm.getFieldValue("timeRange");
      const startDate = detailFilterForm.getFieldValue("startDate");
      const startClock = detailFilterForm.getFieldValue("startClock");
      const endDate = detailFilterForm.getFieldValue("endDate");
      const endClock = detailFilterForm.getFieldValue("endClock");
      const start = timeRange?.[0] ?? mergeDateAndClock(startDate, startClock);
      const end = timeRange?.[1] ?? mergeDateAndClock(endDate, endClock, "end");
      return {
        shopId,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        startTime: start?.format("YYYY-MM-DD HH:mm:ss"),
        endTime: end?.format("YYYY-MM-DD HH:mm:ss"),
      };
    },
    [detailFilterForm],
  );

  const getPickerPopupContainer = useCallback(
    (triggerNode: HTMLElement) => triggerNode.parentElement ?? document.body,
    [],
  );

  const validateDetailFilter = useCallback(() => {
    if (!isCompact) {
      return true;
    }

    const startDate = detailFilterForm.getFieldValue("startDate");
    const startClock = detailFilterForm.getFieldValue("startClock");
    const endDate = detailFilterForm.getFieldValue("endDate");
    const endClock = detailFilterForm.getFieldValue("endClock");

    if (startClock && !startDate) {
      message.warning("请先选择开始日期，再选择开始时间");
      return false;
    }

    if (endClock && !endDate) {
      message.warning("请先选择结束日期，再选择结束时间");
      return false;
    }

    return true;
  }, [detailFilterForm, isCompact]);

  const loadDetail = useCallback(
    async (
      shop: PowerManagementRow,
      paginationOverrides?: DetailPaginationOverrides,
    ) => {
      setDetailLoading(true);
      try {
        const nextRechargePagination =
          paginationOverrides?.recharges ?? rechargePagination;
        const nextGrantPagination =
          paginationOverrides?.grants ?? grantPagination;
        const nextConsumePagination =
          paginationOverrides?.consumes ?? consumePagination;
        const [
          summaryResponse,
          rechargeResponse,
          grantResponse,
          consumeResponse,
        ] = await Promise.all([
          getPowerAccountSummary(shop.shopId),
          getPowerRechargeRecords(
            buildDetailRecordQuery(shop.shopId, nextRechargePagination),
          ),
          getPowerGrantRecords(
            buildDetailRecordQuery(shop.shopId, nextGrantPagination),
          ),
          getPowerConsumeRecords(
            buildDetailRecordQuery(shop.shopId, nextConsumePagination),
          ),
        ]);
        setSummary(summaryResponse);
        setRecharges(rechargeResponse.list);
        setRechargePagination((current) => ({
          ...current,
          total: rechargeResponse.total,
        }));
        setGrants(grantResponse.list);
        setGrantPagination((current) => ({
          ...current,
          total: grantResponse.total,
        }));
        setConsumes(consumeResponse.list);
        setConsumePagination((current) => ({
          ...current,
          total: consumeResponse.total,
        }));
      } catch (error) {
        console.error("Failed to load power detail:", error);
        message.error("加载算力详情失败");
      } finally {
        setDetailLoading(false);
      }
    },
    [
      buildDetailRecordQuery,
      consumePagination,
      grantPagination,
      rechargePagination,
    ],
  );

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  useEffect(() => {
    void loadVisibleUsers();
  }, [loadVisibleUsers]);

  useEffect(
    () => () => {
      if (visibleUserSearchTimerRef.current != null) {
        window.clearTimeout(visibleUserSearchTimerRef.current);
      }
    },
    [],
  );

  const handleSearch = useCallback((values: SearchFormValues) => {
    setPageNum(1);
    setQueryKeyword(values.keyword ?? "");
    setQueryTargetUserId(values.targetUserId);
    setQueryBindingDomain(values.bindingDomain ?? "ALL");
    setQueryBindingStatus(values.bindingStatus ?? "ALL");
    setQueryRiskStatus(values.riskStatus);
  }, []);

  const handleReset = useCallback(() => {
    searchForm.resetFields();
    setPageNum(1);
    setQueryKeyword("");
    setQueryTargetUserId(undefined);
    setQueryBindingDomain("ALL");
    setQueryBindingStatus("ALL");
    setQueryRiskStatus(undefined);
  }, [searchForm]);

  const handleVisibleUserSearch = useCallback(
    (value: string) => {
      if (visibleUserSearchTimerRef.current != null) {
        window.clearTimeout(visibleUserSearchTimerRef.current);
      }
      visibleUserSearchTimerRef.current = window.setTimeout(() => {
        void loadVisibleUsers(
          value.trim() || undefined,
          searchForm.getFieldValue("bindingDomain") ?? "ALL",
        );
      }, USER_SEARCH_DEBOUNCE_MS);
    },
    [loadVisibleUsers, searchForm],
  );

  const openDetail = useCallback(
    async (shop: PowerManagementRow) => {
      const initialPagination = buildDetailPaginationState();
      setActiveShop(shop);
      setDetailOpen(true);
      setActiveDetailTab("recharges");
      detailFilterForm.resetFields();
      setRechargePagination(initialPagination);
      setGrantPagination(initialPagination);
      setConsumePagination(initialPagination);
      await loadDetail(shop, {
        recharges: initialPagination,
        grants: initialPagination,
        consumes: initialPagination,
      });
    },
    [detailFilterForm, loadDetail],
  );

  const openGrantModal = useCallback(
    (shop: PowerManagementRow) => {
      setGrantTargetShop(shop);
      setGrantModalOpen(true);
      grantForm.setFieldsValue({
        shopId: shop.shopId,
        powerAmount: 100,
        grantReason: "",
        remark: undefined,
      });
    },
    [grantForm],
  );

  const closeGrantModal = useCallback(() => {
    setGrantModalOpen(false);
    setGrantTargetShop(null);
    grantForm.resetFields();
  }, [grantForm]);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setActiveDetailTab("recharges");
    setActiveShop(null);
    setSummary(null);
    setRecharges([]);
    setGrants([]);
    setConsumes([]);
    setRechargePagination(buildDetailPaginationState());
    setGrantPagination(buildDetailPaginationState());
    setConsumePagination(buildDetailPaginationState());
    detailFilterForm.resetFields();
  }, [detailFilterForm]);

  const handleGrant = useCallback(async () => {
    if (!grantTargetShop) {
      return;
    }
    try {
      const values = await grantForm.validateFields();
      setGrantSubmitting(true);
      await createShopPowerGrant({
        shopId: grantTargetShop.shopId,
        powerAmount: values.powerAmount,
        grantReason: values.grantReason,
        remark: values.remark,
      });
      message.success("算力发放成功");
      await loadRows();
      if (detailOpen && activeShop?.shopId === grantTargetShop.shopId) {
        await loadDetail(activeShop);
      }
      closeGrantModal();
    } catch (error) {
      if ((error as { errorFields?: unknown[] })?.errorFields) {
        return;
      }
      console.error("Failed to grant power:", error);
      message.error("算力发放失败");
    } finally {
      setGrantSubmitting(false);
    }
  }, [
    activeShop,
    closeGrantModal,
    detailOpen,
    grantForm,
    grantTargetShop,
    loadDetail,
    loadRows,
  ]);

  const handleDetailFilter = useCallback(async () => {
    if (!activeShop) {
      return;
    }
    if (!validateDetailFilter()) {
      return;
    }
    const firstPage = {
      pageNum: 1,
      pageSize: rechargePagination.pageSize,
    };
    const firstGrantPage = {
      pageNum: 1,
      pageSize: grantPagination.pageSize,
    };
    const firstConsumePage = {
      pageNum: 1,
      pageSize: consumePagination.pageSize,
    };
    setRechargePagination((current) => ({ ...current, pageNum: 1 }));
    setGrantPagination((current) => ({ ...current, pageNum: 1 }));
    setConsumePagination((current) => ({ ...current, pageNum: 1 }));
    await loadDetail(activeShop, {
      recharges: firstPage,
      grants: firstGrantPage,
      consumes: firstConsumePage,
    });
  }, [
    activeShop,
    consumePagination.pageSize,
    grantPagination.pageSize,
    loadDetail,
    rechargePagination.pageSize,
    validateDetailFilter,
  ]);

  const handleDetailFilterReset = useCallback(async () => {
    detailFilterForm.resetFields();
    if (!activeShop) {
      return;
    }
    const firstPage = {
      pageNum: 1,
      pageSize: rechargePagination.pageSize,
    };
    const firstGrantPage = {
      pageNum: 1,
      pageSize: grantPagination.pageSize,
    };
    const firstConsumePage = {
      pageNum: 1,
      pageSize: consumePagination.pageSize,
    };
    setRechargePagination((current) => ({ ...current, pageNum: 1 }));
    setGrantPagination((current) => ({ ...current, pageNum: 1 }));
    setConsumePagination((current) => ({ ...current, pageNum: 1 }));
    await loadDetail(activeShop, {
      recharges: firstPage,
      grants: firstGrantPage,
      consumes: firstConsumePage,
    });
  }, [
    activeShop,
    consumePagination.pageSize,
    detailFilterForm,
    grantPagination.pageSize,
    loadDetail,
    rechargePagination.pageSize,
  ]);

  const columns: ColumnsType<PowerManagementRow> = useMemo(
    () => [
      {
        title: "门店名称",
        dataIndex: "shopName",
        key: "shopName",
        width: 220,
        fixed: isCompact ? undefined : "left",
        render: (_: string | undefined, record) => (
          <Space orientation="vertical" size={2}>
            {record.shopName ? (
              <Text strong>{record.shopName}</Text>
            ) : (
              <Text type="secondary">未命名门店</Text>
            )}
            <Text type="secondary">{record.poiId}</Text>
          </Space>
        ),
      },
      {
        title: "绑定类型",
        dataIndex: "bindingDomainName",
        key: "bindingDomain",
        width: 120,
        render: (_: string | undefined, record) =>
          renderBindingDomainTag(record),
      },
      {
        title: "所属用户",
        dataIndex: "boundUserName",
        key: "boundUserName",
        width: 180,
        render: (_: string | undefined, record) => renderBoundUsers(record),
      },
      {
        title: "当前余额",
        dataIndex: "balance",
        key: "balance",
        width: 120,
        sorter: true,
        sortOrder: balanceSortOrder,
        render: (value: number) => (
          <Text strong>{formatPowerWithUnit(value)}</Text>
        ),
      },
      {
        title: "余额构成",
        key: "compose",
        width: 200,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>实充 {formatPowerAmount(record.rechargeBalance)}</Text>
            <Text>赠送 {formatPowerAmount(record.grantBalance)}</Text>
          </Space>
        ),
      },
      {
        title: "累计变化",
        key: "totals",
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>累计实充 {formatPowerAmount(record.totalRecharge)}</Text>
            <Text>累计赠送 {formatPowerAmount(record.totalGrant)}</Text>
            <Text>累计消耗 {formatPowerAmount(record.totalConsume)}</Text>
          </Space>
        ),
      },
      {
        title: "风险状态",
        dataIndex: "riskStatus",
        key: "riskStatus",
        width: 120,
        render: (value: PowerRiskStatus) => renderRiskTag(value),
      },
      {
        title: "操作",
        key: "action",
        width: 250,
        render: (_, record) => (
          <Space size="small">
            <PermissionControl
              permission="shop:power-grant:add"
              role="super_admin"
            >
              <Button
                type="link"
                icon={<GiftOutlined />}
                onClick={() => openGrantModal(record)}
              >
                发放算力
              </Button>
            </PermissionControl>
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => void openDetail(record)}
            >
              查看详情
            </Button>
          </Space>
        ),
      },
    ],
    [balanceSortOrder, isCompact, openDetail, openGrantModal],
  );

  const rechargeColumns: ColumnsType<PowerRechargeRecord> = useMemo(
    () => [
      {
        title: "充值单号",
        dataIndex: "rechargeNo",
        key: "rechargeNo",
        width: 220,
      },
      {
        title: "算力",
        dataIndex: "powerAmount",
        key: "powerAmount",
        width: 100,
        render: (value: number) => formatPowerWithUnit(value),
      },
      {
        title: "金额",
        dataIndex: "totalAmountFen",
        key: "totalAmountFen",
        width: 100,
        render: (value: number) => formatFen(value),
      },
      {
        title: "支付状态",
        dataIndex: "payStatusDesc",
        key: "payStatusDesc",
        width: 120,
        render: (value?: string) => value || "-",
      },
      {
        title: "支付成功时间",
        dataIndex: "paidAt",
        key: "paidAt",
        width: 180,
        render: (value?: string) => formatDateTime(value),
      },
      {
        title: "创建时间",
        dataIndex: "createTime",
        key: "createTime",
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
    ],
    [],
  );

  const grantColumns: ColumnsType<AdminPowerGrantRecord> = useMemo(
    () => [
      {
        title: "发放单号",
        dataIndex: "grantNo",
        key: "grantNo",
        width: 220,
      },
      {
        title: "算力",
        dataIndex: "powerAmount",
        key: "powerAmount",
        width: 100,
        render: (value: number) => formatPowerWithUnit(value),
      },
      {
        title: "发放原因",
        dataIndex: "grantReason",
        key: "grantReason",
        width: 180,
      },
      {
        title: "操作人",
        dataIndex: "operatorUsername",
        key: "operatorUsername",
        width: 120,
        render: (_, record) =>
          record.operatorUsername || record.operatorUserId || "-",
      },
      {
        title: "备注",
        dataIndex: "remark",
        key: "remark",
        width: 160,
        render: (value?: string) => value || "-",
      },
      {
        title: "时间",
        dataIndex: "createTime",
        key: "createTime",
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
    ],
    [],
  );

  const consumeColumns: ColumnsType<PowerConsumeRecord> = useMemo(
    () => [
      {
        title: "消费流水",
        dataIndex: "consumeNo",
        key: "consumeNo",
        width: 220,
      },
      {
        title: "来源",
        key: "source",
        width: 180,
        render: (_, record) => (
          <Space orientation="vertical" size={2}>
            <Text>{record.sourceTypeText || record.sourceType}</Text>
            <Text type="secondary">{record.sourceId || "-"}</Text>
          </Space>
        ),
      },
      {
        title: "消费算力",
        dataIndex: "powerAmount",
        key: "powerAmount",
        width: 110,
        render: (value: number) => formatPowerWithUnit(value),
      },
      {
        title: "余额变化",
        key: "balanceChange",
        width: 140,
        render: (_, record) =>
          `${formatPowerAmount(record.balanceBefore)} -> ${formatPowerAmount(record.balanceAfter)}`,
      },
      {
        title: "扣减拆分",
        dataIndex: "details",
        key: "details",
        width: 260,
        render: (details: PowerConsumeRecord["details"]) => {
          if (!details || details.length === 0) {
            return <Text type="secondary">-</Text>;
          }
          return (
            <Space orientation="vertical" size={2}>
              {details.map((detail, index) => (
                <Text key={`${detail.sourceBalanceType}-${index}`}>
                  {`${detail.sourceBalanceTypeText || detail.sourceBalanceType}：-${formatPowerAmount(detail.powerAmount)} (${formatPowerAmount(detail.balanceBefore)} -> ${formatPowerAmount(detail.balanceAfter)})`}
                </Text>
              ))}
            </Space>
          );
        },
      },
      {
        title: "备注",
        dataIndex: "remark",
        key: "remark",
        width: 220,
        render: (value?: string) => (
          <Tooltip title={value}>
            <Text ellipsis style={{ maxWidth: 180 }}>
              {value || "-"}
            </Text>
          </Tooltip>
        ),
      },
      {
        title: "时间",
        dataIndex: "createTime",
        key: "createTime",
        width: 180,
        render: (value: string) => formatDateTime(value),
      },
    ],
    [],
  );

  if (!isSuperAdmin) {
    return <Navigate to="/403" replace />;
  }

  return (
    <PageContainer>
      <Space orientation="vertical" size={16} style={{ width: "100%" }}>
        <div className={pageStyles.toolbar}>
          <Form<SearchFormValues>
            form={searchForm}
            layout="inline"
            className={pageStyles.searchForm}
            onFinish={handleSearch}
          >
            <Form.Item name="keyword">
              <Input
                allowClear
                placeholder="搜索门店名称或店铺 ID"
                prefix={<ShopOutlined />}
                style={getResponsiveControlStyle(isCompact, 320)}
              />
            </Form.Item>
            <Form.Item name="bindingDomain" initialValue="ALL">
              <Select
                placeholder="绑定类型"
                style={getResponsiveControlStyle(isCompact, 160)}
                options={bindingDomainOptions}
                onChange={(value) => {
                  searchForm.setFieldValue("targetUserId", undefined);
                  void loadVisibleUsers(undefined, value);
                }}
              />
            </Form.Item>
            <Form.Item name="bindingStatus" initialValue="ALL">
              <Select
                placeholder="绑定状态"
                style={getResponsiveControlStyle(isCompact, 160)}
                options={bindingStatusOptions}
              />
            </Form.Item>
            <Form.Item name="targetUserId">
              <Select
                allowClear
                showSearch
                placeholder="归属用户"
                style={getResponsiveControlStyle(isCompact, 220)}
                options={visibleUsers.map((item) => ({
                  label: item.displayName,
                  value: item.userId,
                }))}
                filterOption={false}
                loading={visibleUserLoading}
                onSearch={handleVisibleUserSearch}
                onFocus={() =>
                  void loadVisibleUsers(
                    undefined,
                    searchForm.getFieldValue("bindingDomain") ?? "ALL",
                  )
                }
              />
            </Form.Item>
            <Form.Item name="riskStatus">
              <Select
                allowClear
                placeholder="风险状态"
                style={getResponsiveControlStyle(isCompact, 160)}
                options={riskOptions}
              />
            </Form.Item>
            <Form.Item className={pageStyles.compactFull}>
              <Space className={pageStyles.searchActions}>
                <Button type="primary" htmlType="submit">
                  搜索
                </Button>
                <Button onClick={handleReset}>重置</Button>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => void loadRows()}
                >
                  刷新
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </div>

        <Table<PowerManagementRow>
          rowKey={(record) => `shop-${record.shopId}`}
          loading={loading}
          columns={columns}
          dataSource={rows}
          size={isCompact ? "small" : "middle"}
          scroll={{ x: 1020 }}
          pagination={{
            current: pageNum,
            pageSize,
            total,
            showSizeChanger: true,
            showQuickJumper: !isCompact,
            onChange: (nextPage, nextPageSize) => {
              setPageNum(nextPage);
              setPageSize(nextPageSize);
            },
          }}
          onChange={(_, __, sorter, extra) => {
            if (extra.action !== "sort") {
              return;
            }
            const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
            const nextSortOrder =
              activeSorter?.columnKey === "balance"
                ? activeSorter.order || null
                : null;
            setPageNum(1);
            setBalanceSortOrder(nextSortOrder);
          }}
          locale={{ emptyText: "暂无可管理的算力门店" }}
        />
      </Space>

      <Drawer
        title={
          activeShop
            ? `算力详情 · ${activeShop.shopName || activeShop.poiId}`
            : "算力详情"
        }
        open={detailOpen}
        onClose={closeDetail}
        size={getResponsiveDrawerWidth(isCompact, 1000)}
        destroyOnHidden
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => {
                if (activeShop) {
                  void loadDetail(activeShop);
                }
              }}
            >
              刷新详情
            </Button>
          </Space>
        }
      >
        <Spin spinning={detailLoading}>
          <Space orientation="vertical" size={16} style={{ width: "100%" }}>
            <Card styles={{ body: { padding: 16 } }}>
              <Space orientation="vertical" size={12} style={{ width: "100%" }}>
                <Row gutter={[12, 8]} align="middle">
                  <Col xs={24} lg={10}>
                    <Space orientation="vertical" size={4}>
                      <Text strong style={{ fontSize: 18, lineHeight: 1.2 }}>
                        {summary?.shopName || activeShop?.shopName || "-"}
                      </Text>
                      <Space size={8} wrap>
                        <Tag color="blue">
                          {summary?.poiId || activeShop?.poiId || "-"}
                        </Tag>
                        {activeShop ? renderBindingDomainTag(activeShop) : null}
                        <Text type="secondary">
                          归属用户：
                          {summary?.boundUserName ||
                            activeShop?.boundUserName ||
                            activeShop?.boundUserId ||
                            "-"}
                        </Text>
                        {summary?.riskStatus
                          ? renderRiskTag(summary.riskStatus)
                          : "-"}
                      </Space>
                    </Space>
                  </Col>
                  <Col xs={24} lg={14}>
                    <Row gutter={[12, 12]}>
                      <Col xs={12} md={6}>
                        <SummaryMetricCard
                          title="当前余额"
                          value={formatPowerWithUnit(summary?.balance)}
                        />
                      </Col>
                      <Col xs={12} md={6}>
                        <SummaryMetricCard
                          title="实充余额"
                          value={formatPowerWithUnit(summary?.rechargeBalance)}
                        />
                      </Col>
                      <Col xs={12} md={6}>
                        <SummaryMetricCard
                          title="赠送余额"
                          value={formatPowerWithUnit(summary?.grantBalance)}
                        />
                      </Col>
                      <Col xs={12} md={6}>
                        <SummaryMetricCard
                          title="累计消耗"
                          value={formatPowerWithUnit(summary?.totalConsume)}
                        />
                      </Col>
                    </Row>
                  </Col>
                </Row>
              </Space>
            </Card>

            <Card styles={{ body: { paddingBottom: 8 } }}>
              <Form<DetailFilterValues>
                form={detailFilterForm}
                layout={isCompact ? "vertical" : "inline"}
                onFinish={() => void handleDetailFilter()}
              >
                {isCompact ? (
                  <>
                    <Form.Item label="开始时间" style={{ marginBottom: 12 }}>
                      <Space style={{ width: "100%" }} size={8}>
                        <Form.Item name="startDate" noStyle>
                          <DatePicker
                            allowClear
                            format="YYYY-MM-DD"
                            style={{ width: "100%" }}
                            getPopupContainer={getPickerPopupContainer}
                            onChange={(value) => {
                              if (!value) {
                                detailFilterForm.setFieldValue(
                                  "startClock",
                                  undefined,
                                );
                              }
                            }}
                          />
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate>
                          {({ getFieldValue }) => (
                            <Form.Item name="startClock" noStyle>
                              <TimePicker
                                allowClear
                                format="HH:mm:ss"
                                style={{ width: 120, flexShrink: 0 }}
                                getPopupContainer={getPickerPopupContainer}
                                disabled={!getFieldValue("startDate")}
                                placeholder="时间"
                              />
                            </Form.Item>
                          )}
                        </Form.Item>
                      </Space>
                    </Form.Item>
                    <Form.Item label="结束时间">
                      <Space style={{ width: "100%" }} size={8}>
                        <Form.Item name="endDate" noStyle>
                          <DatePicker
                            allowClear
                            format="YYYY-MM-DD"
                            style={{ width: "100%" }}
                            getPopupContainer={getPickerPopupContainer}
                            onChange={(value) => {
                              if (!value) {
                                detailFilterForm.setFieldValue(
                                  "endClock",
                                  undefined,
                                );
                              }
                            }}
                          />
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate>
                          {({ getFieldValue }) => (
                            <Form.Item name="endClock" noStyle>
                              <TimePicker
                                allowClear
                                format="HH:mm:ss"
                                style={{ width: 120, flexShrink: 0 }}
                                getPopupContainer={getPickerPopupContainer}
                                disabled={!getFieldValue("endDate")}
                                placeholder="时间"
                              />
                            </Form.Item>
                          )}
                        </Form.Item>
                      </Space>
                    </Form.Item>
                  </>
                ) : (
                  <Form.Item name="timeRange" label="时间范围">
                    <RangePicker
                      allowClear
                      showTime
                      format="YYYY-MM-DD HH:mm:ss"
                      getPopupContainer={getPickerPopupContainer}
                    />
                  </Form.Item>
                )}
                <Form.Item
                  className={isCompact ? pageStyles.compactFull : undefined}
                >
                  <Space className={pageStyles.searchActions} wrap={isCompact}>
                    <Button type="primary" htmlType="submit">
                      筛选
                    </Button>
                    <Button onClick={() => void handleDetailFilterReset()}>
                      重置
                    </Button>
                  </Space>
                </Form.Item>
              </Form>
            </Card>

            <Tabs
              activeKey={activeDetailTab}
              onChange={(key) => setActiveDetailTab(key as DetailTabKey)}
              items={[
                {
                  key: "recharges",
                  label: `充值记录 (${rechargePagination.total})`,
                  children: (
                    <Table<PowerRechargeRecord>
                      rowKey="id"
                      size={isCompact ? "small" : "middle"}
                      columns={rechargeColumns}
                      dataSource={recharges}
                      pagination={{
                        current: rechargePagination.pageNum,
                        pageSize: rechargePagination.pageSize,
                        total: rechargePagination.total,
                        showSizeChanger: true,
                        showQuickJumper: !isCompact,
                        onChange: (nextPage, nextPageSize) => {
                          const nextPagination = {
                            pageNum: nextPage,
                            pageSize: nextPageSize,
                          };
                          setRechargePagination((current) => ({
                            ...current,
                            ...nextPagination,
                          }));
                          if (activeShop) {
                            void loadDetail(activeShop, {
                              recharges: nextPagination,
                            });
                          }
                        },
                      }}
                      scroll={{ x: 860 }}
                      locale={{ emptyText: "暂无充值记录" }}
                    />
                  ),
                },
                {
                  key: "grants",
                  label: `发放记录 (${grantPagination.total})`,
                  children: (
                    <Table<AdminPowerGrantRecord>
                      rowKey="id"
                      size={isCompact ? "small" : "middle"}
                      columns={grantColumns}
                      dataSource={grants}
                      pagination={{
                        current: grantPagination.pageNum,
                        pageSize: grantPagination.pageSize,
                        total: grantPagination.total,
                        showSizeChanger: true,
                        showQuickJumper: !isCompact,
                        onChange: (nextPage, nextPageSize) => {
                          const nextPagination = {
                            pageNum: nextPage,
                            pageSize: nextPageSize,
                          };
                          setGrantPagination((current) => ({
                            ...current,
                            ...nextPagination,
                          }));
                          if (activeShop) {
                            void loadDetail(activeShop, {
                              grants: nextPagination,
                            });
                          }
                        },
                      }}
                      scroll={{ x: 900 }}
                      locale={{ emptyText: "暂无发放记录" }}
                    />
                  ),
                },
                {
                  key: "consumes",
                  label: `消耗记录 (${consumePagination.total})`,
                  children: (
                    <Table<PowerConsumeRecord>
                      rowKey="id"
                      size={isCompact ? "small" : "middle"}
                      columns={consumeColumns}
                      dataSource={consumes}
                      pagination={{
                        current: consumePagination.pageNum,
                        pageSize: consumePagination.pageSize,
                        total: consumePagination.total,
                        showSizeChanger: true,
                        showQuickJumper: !isCompact,
                        onChange: (nextPage, nextPageSize) => {
                          const nextPagination = {
                            pageNum: nextPage,
                            pageSize: nextPageSize,
                          };
                          setConsumePagination((current) => ({
                            ...current,
                            ...nextPagination,
                          }));
                          if (activeShop) {
                            void loadDetail(activeShop, {
                              consumes: nextPagination,
                            });
                          }
                        },
                      }}
                      scroll={{ x: 1320 }}
                      locale={{ emptyText: "暂无消耗记录" }}
                    />
                  ),
                },
              ]}
            />
          </Space>
        </Spin>
      </Drawer>

      <PermissionControl permission="shop:power-grant:add" role="super_admin">
        <Modal
          title={
            grantTargetShop
              ? `发放算力 · ${grantTargetShop.shopName || grantTargetShop.poiId}`
              : "发放算力"
          }
          open={grantModalOpen}
          onCancel={closeGrantModal}
          width={getResponsiveOverlayWidth(isCompact, 520, 520)}
          destroyOnHidden
          footer={
            <Space>
              <Button onClick={closeGrantModal}>取消</Button>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={grantSubmitting}
                onClick={() => void handleGrant()}
              >
                确认发放
              </Button>
            </Space>
          }
        >
          <Form<AdminPowerGrantCreateParam>
            form={grantForm}
            layout="vertical"
            initialValues={{
              powerAmount: 100,
              shopId: grantTargetShop?.shopId,
            }}
          >
            <Form.Item name="shopId" hidden>
              <InputNumber />
            </Form.Item>
            <Form.Item label="门店">
              <Input
                value={
                  grantTargetShop?.shopName || grantTargetShop?.poiId || ""
                }
                disabled
              />
            </Form.Item>
            <Form.Item
              label="发放算力"
              name="powerAmount"
              rules={[{ required: true, message: "请输入发放算力" }]}
            >
              <InputNumber min={1} precision={0} style={{ width: "100%" }} />
            </Form.Item>
            <Form.Item
              label="发放原因"
              name="grantReason"
              rules={[{ required: true, message: "请输入发放原因" }]}
            >
              <Input placeholder="如：活动补发" />
            </Form.Item>
            <Form.Item label="备注" name="remark">
              <Input.TextArea rows={3} placeholder="选填" />
            </Form.Item>
          </Form>
        </Modal>
      </PermissionControl>
    </PageContainer>
  );
};

export default PowerManage;
