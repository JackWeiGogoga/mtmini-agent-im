import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from "antd";
import {
  DeleteOutlined,
  DownloadOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
  ShopOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import type { SortOrder } from "antd/es/table/interface";
import dayjs from "dayjs";
import * as XLSX from "xlsx";
import { message } from "@/components/AppMessageContext";
import { PermissionControl } from "@/components/PermissionControl";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { useCurrentUser } from "@/contexts/AuthContext";
import { useResponsive } from "@/hooks";
import { usePermission } from "@/hooks/usePermission";
import {
  batchBindUserShops,
  bindUserShop,
  getBoundShops,
  getVisibleShopUsers,
  unbindUserShop,
} from "@/services/shop-manage-api";
import type {
  BatchBindFailureItem,
  BatchBindUserShopResult,
  BindUserShopParam,
  UserShopBinding,
  ShopBindingDomain,
  ShopBindingStatus,
  VisibleUserOption,
} from "@/types";
import {
  getResponsiveControlStyle,
  getResponsiveOverlayWidth,
} from "@/utils/responsive";
import { formatPowerAmount, formatPowerWithUnit } from "@/utils/power-format";

const { Text } = Typography;
const compactLinkButtonStyle = { paddingInline: 4 } as const;
const EXCEL_TEMPLATE_NAME = "门店批量绑定模板.xlsx";
const USER_SEARCH_DEBOUNCE_MS = 300;
const ADMIN_BINDING_DOMAIN_OPTIONS: { label: string; value: ShopBindingDomain }[] =
  [
    { label: "全部", value: "ALL" },
    { label: "普通商户", value: "ORDINARY" },
    { label: "网销用户", value: "SALES" },
  ];
const MUTATION_BINDING_DOMAIN_OPTIONS = ADMIN_BINDING_DOMAIN_OPTIONS.filter(
  (item) => item.value !== "ALL",
);
const BINDING_STATUS_OPTIONS: { label: string; value: ShopBindingStatus }[] = [
  { label: "全部状态", value: "ALL" },
  { label: "未绑定", value: "UNBOUND" },
  { label: "已绑定", value: "BOUND" },
  { label: "双绑定", value: "BOTH" },
];

function renderMerchantBindingStatus(record: UserShopBinding) {
  if (record.bindingStatus === "UNBOUND") {
    return <Tag>未绑定</Tag>;
  }
  if (record.bindingStatus === "BOTH") {
    return <Tag color="blue">双绑定</Tag>;
  }
  if (record.salesBindingId && !record.ordinaryBindingId) {
    return <Tag color="purple">网销用户</Tag>;
  }
  if (record.ordinaryBindingId && !record.salesBindingId) {
    return <Tag color="green">普通商户</Tag>;
  }
  if (record.bindingDomain === "SALES") {
    return <Tag color="purple">{record.bindingDomainName || "网销用户"}</Tag>;
  }
  if (record.bindingDomain === "ORDINARY") {
    return <Tag color="green">{record.bindingDomainName || "普通商户"}</Tag>;
  }
  return <Tag>{record.bindingStatusName || record.bindingDomainName || "未知"}</Tag>;
}

function renderMerchantBoundUsers(record: UserShopBinding) {
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

function renderRightExpireAt(value?: string) {
  if (!value) {
    return <Text type="secondary">-</Text>;
  }

  const expireAt = dayjs(value);
  if (!expireAt.isValid()) {
    return value;
  }

  const now = dayjs();
  const daysLeft = expireAt.endOf("day").diff(now, "day");
  let color: "default" | "orange" | "red" = "default";
  let label = `剩余 ${Math.max(daysLeft, 0)} 天`;

  if (expireAt.isBefore(now)) {
    color = "red";
    label = "已过期";
  } else if (daysLeft <= 7) {
    color = "orange";
  }

  return (
    <Space size={6} wrap>
      <Text>{expireAt.format("YYYY-MM-DD HH:mm:ss")}</Text>
      <Tag color={color}>{label}</Tag>
    </Space>
  );
}

function buildPowerDetailText(record: UserShopBinding) {
  return `实充 ${formatPowerAmount(record.totalRecharge)} / 赠送 ${formatPowerAmount(record.totalGrant)} / 消耗 ${formatPowerAmount(record.totalConsume)}`;
}

function downloadBindTemplate() {
  const workbook = XLSX.utils.book_new();
  const worksheet = XLSX.utils.aoa_to_sheet([["门店ID"]]);
  XLSX.utils.book_append_sheet(workbook, worksheet, "门店ID模板");
  XLSX.writeFileXLSX(workbook, EXCEL_TEMPLATE_NAME);
}

async function readPoiIdsFromExcel(file: File): Promise<string[]> {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, { type: "array" });
  const sheetName = workbook.SheetNames[0];
  if (!sheetName) {
    return [];
  }

  const worksheet = workbook.Sheets[sheetName];
  const rows = XLSX.utils.sheet_to_json<(string | number | null)[]>(worksheet, {
    header: 1,
    raw: false,
    blankrows: false,
  });

  return rows
    .map((row, index) => {
      const firstCell = row?.[0];
      const normalized = firstCell == null ? "" : String(firstCell).trim();
      if (!normalized) {
        return null;
      }
      if (index === 0 && normalized === "门店ID") {
        return null;
      }
      return normalized;
    })
    .filter((item): item is string => Boolean(item));
}

const ShopManage: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const { isSuperAdmin, hasAnyPermission } = usePermission();
  const currentUser = useCurrentUser();
  const isAdminPortal = currentUser?.activePortal === "ADMIN";
  const canUseBindingDomain = isSuperAdmin && isAdminPortal;
  const isYouzanPortal = currentUser?.activePortal === "YOUZAN";
  const canBindShop = hasAnyPermission([
    "shop:binding:add",
    "sales:binding:add",
    "youzan:binding:add",
  ]);
  const [searchForm] = Form.useForm<{
    keyword?: string;
    targetUserId?: string;
    bindingDomain?: ShopBindingDomain;
    bindingStatus?: ShopBindingStatus;
  }>();
  const [singleBindForm] = Form.useForm<BindUserShopParam>();

  const [loading, setLoading] = useState(false);
  const [singleBindSubmitting, setSingleBindSubmitting] = useState(false);
  const [batchBindSubmitting, setBatchBindSubmitting] = useState(false);
  const [records, setRecords] = useState<UserShopBinding[]>([]);
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindTabKey, setBindTabKey] = useState("single");
  const [bindTargetUserId, setBindTargetUserId] = useState<string>();
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [queryKeyword, setQueryKeyword] = useState("");
  const [queryTargetUserId, setQueryTargetUserId] = useState<string>();
  const [queryBindingDomain, setQueryBindingDomain] =
    useState<ShopBindingDomain>("ALL");
  const [queryBindingStatus, setQueryBindingStatus] =
    useState<ShopBindingStatus>("ALL");
  const [powerBalanceSortOrder, setPowerBalanceSortOrder] =
    useState<SortOrder>(null);
  const [bindBindingDomain, setBindBindingDomain] =
    useState<ShopBindingDomain>();
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [visibleUserLoading, setVisibleUserLoading] = useState(false);
  const [hasExpandedScope, setHasExpandedScope] = useState(false);
  const [batchPoiIds, setBatchPoiIds] = useState<string[]>([]);
  const [batchFileName, setBatchFileName] = useState<string>("");
  const [batchBindResult, setBatchBindResult] =
    useState<BatchBindUserShopResult | null>(null);
  const visibleUserSearchTimerRef = useRef<number | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getBoundShops({
        keyword: queryKeyword.trim() || undefined,
        targetUserId: queryTargetUserId,
        bindingDomain: canUseBindingDomain ? queryBindingDomain : undefined,
        bindingStatus: canUseBindingDomain ? queryBindingStatus : undefined,
        sortField: powerBalanceSortOrder ? "powerBalance" : undefined,
        sortOrder: powerBalanceSortOrder || undefined,
        pageNum: current,
        pageSize,
      });
      setRecords(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load bound shops:", error);
    } finally {
      setLoading(false);
    }
  }, [
    canUseBindingDomain,
    current,
    pageSize,
    powerBalanceSortOrder,
    queryBindingDomain,
    queryBindingStatus,
    queryKeyword,
    queryTargetUserId,
  ]);

  const loadVisibleUsers = useCallback(async (
    keyword?: string,
    bindingDomainOverride?: ShopBindingDomain,
  ) => {
    setVisibleUserLoading(true);
    try {
      const response = await getVisibleShopUsers(
        keyword,
        canUseBindingDomain
          ? bindingDomainOverride || bindBindingDomain || queryBindingDomain
          : undefined,
      );
      setVisibleUsers(response);
      if (!keyword) {
        setHasExpandedScope(response.length > 1);
      }
    } catch (error) {
      console.error("Failed to load visible shop users:", error);
    } finally {
      setVisibleUserLoading(false);
    }
  }, [bindBindingDomain, canUseBindingDomain, queryBindingDomain]);

  useEffect(() => {
    loadData();
  }, [loadData]);

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

  const handleVisibleUserSearch = useCallback(
    (value: string) => {
      if (visibleUserSearchTimerRef.current != null) {
        window.clearTimeout(visibleUserSearchTimerRef.current);
      }
      visibleUserSearchTimerRef.current = window.setTimeout(() => {
        void loadVisibleUsers(value.trim() || undefined);
      }, USER_SEARCH_DEBOUNCE_MS);
    },
    [loadVisibleUsers],
  );

  const resetBindModalState = () => {
    setBindModalOpen(false);
    setBindTabKey("single");
    setBindTargetUserId(undefined);
    setBindBindingDomain(undefined);
    setBatchPoiIds([]);
    setBatchFileName("");
    setBatchBindResult(null);
    singleBindForm.resetFields();
  };

  const handleSingleBindSubmit = async () => {
    try {
      const values = await singleBindForm.validateFields();
      if (canUseBindingDomain && !bindBindingDomain) {
        message.warning("请选择绑定类型");
        return;
      }
      setSingleBindSubmitting(true);
      await bindUserShop({
        poiId: values.poiId.trim(),
        targetUserId: bindTargetUserId,
        bindingDomain: canUseBindingDomain ? bindBindingDomain : undefined,
      });
      message.success("门店绑定成功");
      resetBindModalState();
      await loadData();
    } catch (error) {
      if (error instanceof Error) {
        console.error("Failed to bind shop:", error);
      }
    } finally {
      setSingleBindSubmitting(false);
    }
  };

  const handleBatchBindSubmit = async () => {
    if (batchPoiIds.length === 0) {
      message.warning("请先上传包含门店ID的 Excel 文件");
      return;
    }

    try {
      if (canUseBindingDomain && !bindBindingDomain) {
        message.warning("请选择绑定类型");
        return;
      }
      setBatchBindSubmitting(true);
      const result = await batchBindUserShops({
        poiIds: batchPoiIds,
        targetUserId: bindTargetUserId,
        bindingDomain: canUseBindingDomain ? bindBindingDomain : undefined,
      });
      setBatchBindResult(result);
      if (result.successCount > 0) {
        await loadData();
      }
      if (result.failureCount > 0) {
        message.warning(
          `批量绑定完成，成功 ${result.successCount} 家，失败 ${result.failureCount} 家`,
        );
      } else {
        message.success(`批量绑定成功，共绑定 ${result.successCount} 家门店`);
      }
    } catch (error) {
      if (error instanceof Error) {
        console.error("Failed to batch bind shops:", error);
      }
    } finally {
      setBatchBindSubmitting(false);
    }
  };

  const handleUploadExcel = useCallback(async (file: File) => {
    const lowerFileName = file.name.toLowerCase();
    if (!lowerFileName.endsWith(".xlsx") && !lowerFileName.endsWith(".xls")) {
      message.error("请上传 .xlsx 或 .xls 格式的 Excel 文件");
      return false;
    }

    try {
      const poiIds = await readPoiIdsFromExcel(file);
      if (poiIds.length === 0) {
        message.error("Excel 中未读取到有效的门店ID，请检查模板内容");
        return false;
      }
      setBatchPoiIds(Array.from(new Set(poiIds)));
      setBatchFileName(file.name);
      setBatchBindResult(null);
      message.success(`已读取 ${poiIds.length} 个门店ID`);
    } catch (error) {
      console.error("Failed to parse excel:", error);
      message.error("Excel 解析失败，请检查文件格式");
    }
    return false;
  }, []);

  const handleUnbind = async (record: UserShopBinding) => {
    try {
      await unbindUserShop(
        record.bindingId ?? 0,
        canUseBindingDomain ? record.bindingDomain : undefined,
      );
      message.success("门店解绑成功");
      if (records.length === 1 && current > 1) {
        setCurrent((prev) => prev - 1);
        return;
      }
      await loadData();
    } catch (error) {
      console.error("Failed to unbind shop:", error);
    }
  };

  const columns: ColumnsType<UserShopBinding> = [
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
    ...(isSuperAdmin
      ? [
          {
            title: "绑定类型",
            dataIndex: "bindingDomainName",
            key: "bindingDomainName",
            width: 120,
            render: (_: string | undefined, record: UserShopBinding) => (
              renderMerchantBindingStatus(record)
            ),
          },
        ]
      : []),
    ...(isSuperAdmin && hasExpandedScope
      ? [
          {
            title: "所属用户",
            dataIndex: "boundUserName",
            key: "boundUserName",
            width: 180,
            render: (_: string | undefined, record: UserShopBinding) =>
              renderMerchantBoundUsers(record),
          },
        ]
      : []),
    {
      title: "地址",
      dataIndex: "address",
      key: "address",
      width: 300,
      ellipsis: true,
      render: (value: string | undefined, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{value || "-"}</Text>
          {record.contactPhone ? (
            <Text type="secondary">联系方式：{record.contactPhone}</Text>
          ) : null}
        </Space>
      ),
    },
    ...(isYouzanPortal
      ? []
      : [
          {
            title: "算力信息",
            dataIndex: "powerBalance",
            key: "powerBalance",
            width: 200,
            sorter: true,
            sortOrder: powerBalanceSortOrder,
            render: (_: unknown, record: UserShopBinding) => (
              <Space size={6} wrap={false}>
                <Text strong>{formatPowerWithUnit(record.powerBalance)}</Text>
                <Tooltip title={buildPowerDetailText(record)}>
                  <Text
                    type="secondary"
                    style={{ fontSize: 12, cursor: "help" }}
                  >
                    详情
                    <InfoCircleOutlined style={{ marginInlineStart: 4 }} />
                  </Text>
                </Tooltip>
              </Space>
            ),
          },
        ]),
    {
      title: "权益过期时间",
      dataIndex: "rightExpireAt",
      key: "rightExpireAt",
      width: 280,
      render: (value?: string) => renderRightExpireAt(value),
    },
    {
      title: "绑定时间",
      dataIndex: "bindTime",
      key: "bindTime",
      width: 180,
    },
    {
      title: "操作",
      key: "action",
      width: 100,
            render: (_, record) => {
              const actions: React.ReactNode[] = [];
              const addUnbindAction = (
                bindingId: number | null | undefined,
                bindingDomain?: ShopBindingDomain,
                label = "解绑",
              ) => {
                if (!bindingId) {
                  return;
                }
                actions.push(
                  <Popconfirm
                    key={`${bindingDomain || "DEFAULT"}-${bindingId}`}
                    title={`确认${label}该门店吗？`}
                    okText="解绑"
                    cancelText="取消"
                    onConfirm={() =>
                      handleUnbind({
                        ...record,
                        bindingId,
                        bindingDomain: bindingDomain || record.bindingDomain,
                      })
                    }
                  >
                    <Button
                      danger
                      type="link"
                      icon={<DeleteOutlined />}
                      style={compactLinkButtonStyle}
                    >
                      {label}
                    </Button>
                  </Popconfirm>,
                );
              };
              if (canUseBindingDomain) {
                addUnbindAction(record.ordinaryBindingId, "ORDINARY", "解绑普通");
                addUnbindAction(record.salesBindingId, "SALES", "解绑网销");
              } else {
                addUnbindAction(record.bindingId, record.bindingDomain, "解绑");
              }
              if (actions.length === 0) {
                return <Text type="secondary">-</Text>;
              }
              return (
                <PermissionControl
                  anyPermissions={[
                    "shop:binding:delete",
                    "sales:binding:delete",
                    "youzan:binding:delete",
                  ]}
                >
                  <Space direction={actions.length > 1 ? "vertical" : "horizontal"} size={4}>
                    {actions}
                  </Space>
                </PermissionControl>
              );
            },
    },
  ];

  const batchFailureColumns: ColumnsType<BatchBindFailureItem> = [
    {
      title: "失败门店 ID",
      dataIndex: "poiId",
      key: "poiId",
      width: 180,
    },
    {
      title: "失败原因",
      dataIndex: "errorMessage",
      key: "errorMessage",
    },
  ];

  const handleSearch = () => {
    const values = searchForm.getFieldsValue();
    setCurrent(1);
    setQueryKeyword((values.keyword ?? "").trim());
    setQueryTargetUserId(values.targetUserId || undefined);
    setQueryBindingDomain(values.bindingDomain || "ALL");
    setQueryBindingStatus(values.bindingStatus || "ALL");
  };

  const handleResetSearch = () => {
    searchForm.resetFields();
    setCurrent(1);
    setQueryKeyword("");
    setQueryTargetUserId(undefined);
    setQueryBindingDomain("ALL");
    setQueryBindingStatus("ALL");
  };

  return (
    <PageContainer>
      <div className={pageStyles.toolbar}>
        <Form
          form={searchForm}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={handleSearch}
        >
          <Form.Item name="keyword">
            <Input
              placeholder="搜索门店名称或店铺 ID"
              prefix={<ShopOutlined />}
              style={getResponsiveControlStyle(isCompact, 320)}
              allowClear
            />
          </Form.Item>
          {canUseBindingDomain ? (
            <Form.Item name="bindingDomain" initialValue="ALL">
              <Select
                style={getResponsiveControlStyle(isCompact, 160)}
                options={ADMIN_BINDING_DOMAIN_OPTIONS}
                onChange={() => {
                  searchForm.setFieldValue("targetUserId", undefined);
                  setQueryTargetUserId(undefined);
                }}
              />
            </Form.Item>
          ) : null}
          {canUseBindingDomain ? (
            <Form.Item name="bindingStatus" initialValue="ALL">
              <Select
                style={getResponsiveControlStyle(isCompact, 160)}
                options={BINDING_STATUS_OPTIONS}
              />
            </Form.Item>
          ) : null}
          {isSuperAdmin && hasExpandedScope ? (
            <Form.Item name="targetUserId">
              <Select
                allowClear
                showSearch
                filterOption={false}
                placeholder="所属用户"
                style={getResponsiveControlStyle(isCompact, 220)}
                loading={visibleUserLoading}
                onSearch={handleVisibleUserSearch}
                onFocus={() => void loadVisibleUsers()}
                options={visibleUsers.map((item) => ({
                  label: item.displayName,
                  value: item.userId,
                }))}
              />
            </Form.Item>
          ) : null}
          <Space className={pageStyles.searchActions}>
            <Button type="primary" htmlType="submit">
              搜索
            </Button>
            <Button onClick={handleResetSearch}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={loadData} loading={loading}>
              刷新
            </Button>
            <PermissionControl
              anyPermissions={[
                "shop:binding:add",
                "sales:binding:add",
                "youzan:binding:add",
              ]}
            >
              <Button type="primary" onClick={() => setBindModalOpen(true)}>
                绑定门店
              </Button>
            </PermissionControl>
          </Space>
        </Form>
      </div>

      <Table<UserShopBinding>
        rowKey={(record) =>
          canUseBindingDomain
            ? `shop-${record.shopId}`
            : `${record.bindingDomain || "ORDINARY"}-${record.bindingId}`
        }
        loading={loading}
        columns={columns}
        dataSource={records}
        size={isCompact ? "small" : "middle"}
        pagination={{
          current,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: !isCompact,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (page, size) => {
            setCurrent(page);
            setPageSize(size);
          },
        }}
        onChange={(_, __, sorter, extra) => {
          if (extra.action !== "sort") {
            return;
          }
          const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
          const nextSortOrder =
            activeSorter?.columnKey === "powerBalance"
              ? activeSorter.order || null
              : null;
          setCurrent(1);
          setPowerBalanceSortOrder(nextSortOrder);
        }}
        scroll={{ x: isYouzanPortal ? 980 : canUseBindingDomain ? 1300 : 1180 }}
        locale={{
          emptyText:
            isSuperAdmin && hasExpandedScope && queryTargetUserId
              ? "该用户当前还没有绑定门店"
              : "当前还没有绑定门店，请点击“绑定门店”完成操作",
        }}
      />

      <Modal
        title="绑定门店"
        open={bindModalOpen}
        onCancel={resetBindModalState}
        onOk={
          bindTabKey === "single"
            ? handleSingleBindSubmit
            : handleBatchBindSubmit
        }
        confirmLoading={
          bindTabKey === "single" ? singleBindSubmitting : batchBindSubmitting
        }
        okText={bindTabKey === "single" ? "确认绑定" : "开始批量绑定"}
        okButtonProps={{
          disabled:
            !canBindShop || (bindTabKey === "batch" && batchPoiIds.length === 0),
        }}
        width={getResponsiveOverlayWidth(isCompact, 720, 720)}
        destroyOnHidden
      >
        {canUseBindingDomain || (isSuperAdmin && hasExpandedScope) ? (
          <Form layout="vertical" style={{ marginBottom: 16 }}>
            {canUseBindingDomain ? (
              <Form.Item
                label="绑定类型"
                required
                extra="普通商户和网销用户是两套独立绑定关系。"
              >
                <Select
                  placeholder="请选择绑定类型"
                  value={bindBindingDomain}
                  style={{ width: "100%" }}
                  options={MUTATION_BINDING_DOMAIN_OPTIONS}
                  onChange={(value) => {
                    setBindBindingDomain(value);
                    setBindTargetUserId(undefined);
                    void loadVisibleUsers(undefined, value);
                  }}
                />
              </Form.Item>
            ) : null}
            {isSuperAdmin && hasExpandedScope ? (
              <Form.Item
                label="所属用户"
                extra="不选择时默认绑定给当前登录用户。"
              >
                <Select
                  allowClear
                  showSearch
                  filterOption={false}
                  placeholder="请选择所属用户"
                  value={bindTargetUserId}
                  style={{ width: "100%" }}
                  loading={visibleUserLoading}
                  onChange={(value) => setBindTargetUserId(value || undefined)}
                  onSearch={handleVisibleUserSearch}
                  onFocus={() => void loadVisibleUsers(undefined, bindBindingDomain)}
                  options={visibleUsers.map((item) => ({
                    label: item.displayName,
                    value: item.userId,
                  }))}
                />
              </Form.Item>
            ) : null}
          </Form>
        ) : null}

        <Tabs
          activeKey={bindTabKey}
          onChange={setBindTabKey}
          items={[
            {
              key: "single",
              label: "单个绑定",
              children: (
                <Form form={singleBindForm} layout="vertical">
                  <Form.Item
                    label="门店 ID"
                    name="poiId"
                    rules={[
                      { required: true, message: "请输入门店 ID" },
                      { max: 64, message: "门店 ID 不能超过 64 个字符" },
                    ]}
                  >
                    <Input placeholder="请输入单个门店 ID" allowClear />
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: "batch",
              label: "批量绑定",
              children: (
                <Space
                  orientation="vertical"
                  size={16}
                  style={{ width: "100%" }}
                >
                  <Space>
                    <Button
                      icon={<DownloadOutlined />}
                      onClick={downloadBindTemplate}
                    >
                      下载 Excel 模板
                    </Button>
                    <Upload
                      accept=".xlsx,.xls"
                      showUploadList={false}
                      beforeUpload={handleUploadExcel}
                    >
                      <Button icon={<UploadOutlined />}>上传 Excel</Button>
                    </Upload>
                  </Space>

                  <div>
                    <Text strong>当前文件：</Text>
                    <Text type="secondary">
                      {batchFileName || "未上传文件"}
                    </Text>
                  </div>
                  <div>
                    <Text strong>待绑定数量：</Text>
                    <Text>{batchPoiIds.length}</Text>
                  </div>

                  {batchBindResult ? (
                    <Space
                      orientation="vertical"
                      size={12}
                      style={{ width: "100%" }}
                    >
                      <Alert
                        type={
                          batchBindResult.failureCount > 0
                            ? "warning"
                            : "success"
                        }
                        showIcon
                        title={`批量绑定完成：成功 ${batchBindResult.successCount} 家，失败 ${batchBindResult.failureCount} 家`}
                      />
                      {batchBindResult.failureItems.length > 0 ? (
                        <Table<BatchBindFailureItem>
                          rowKey={(record) =>
                            `${record.poiId}-${record.errorMessage}`
                          }
                          columns={batchFailureColumns}
                          dataSource={batchBindResult.failureItems}
                          pagination={false}
                          size="small"
                          scroll={{ x: 520 }}
                        />
                      ) : null}
                    </Space>
                  ) : null}

                  <Space
                    size={8}
                    style={{
                      marginTop: 8,
                      padding: "8px 12px",
                      width: "100%",
                      borderRadius: 8,
                      background: "#f6f8fb",
                    }}
                  >
                    <InfoCircleOutlined style={{ color: "#1677ff" }} />
                    <Text type="secondary">
                      模板仅需一列“门店ID”；系统会自动去重，并逐条校验门店是否存在及是否已被占用。
                    </Text>
                  </Space>
                </Space>
              ),
            },
          ]}
        />

        <Space
          size={8}
          style={{
            marginTop: 8,
            padding: "8px 12px",
            width: "100%",
            borderRadius: 8,
            background: "#f6f8fb",
          }}
        >
          <InfoCircleOutlined style={{ color: "#1677ff" }} />
          <Text type="secondary">
            提交时会根据当前工作台校验门店是否存在，以及该门店是否已在当前绑定域内被占用。
          </Text>
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default ShopManage;
