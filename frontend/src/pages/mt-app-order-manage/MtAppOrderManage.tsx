import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  TimePicker,
  Typography,
} from "antd";
import {
  DownloadOutlined,
  DownOutlined,
  ReloadOutlined,
  SearchOutlined,
  UpOutlined,
} from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import { message } from "@/components/AppMessageContext";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import { useResponsive } from "@/hooks";
import {
  exportMtAppOrders,
  exportMtAppOrderSettlementStats,
  getMtAppOrders,
  getMtAppOrderSettlementStats,
  getMtAppOrderVersionOptions,
} from "@/services/mt-app-order-api";
import { getVisibleShopUsers } from "@/services/shop-manage-api";
import type {
  MtAppOrderRow,
  MtAppOrderSettlementRow,
  MtAppOrderVersionOption,
  VisibleUserOption,
} from "@/types";
import { getResponsiveControlStyle } from "@/utils/responsive";

const { RangePicker } = DatePicker;
const { Text } = Typography;
const USER_SEARCH_DEBOUNCE_MS = 300;

type SearchFormValues = {
  orderId?: string;
  orderViewId?: string;
  poiId?: string;
  shopName?: string;
  payStatus?: number;
  rightStatus?: string;
  version?: string;
  versionName?: string;
  targetUserId?: string;
  orderTimeRange?: [Dayjs, Dayjs];
  startDate?: Dayjs;
  startClock?: Dayjs;
  endDate?: Dayjs;
  endClock?: Dayjs;
};

type SettlementFormValues = {
  statsMonth?: Dayjs;
  poiId?: string;
};

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

function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  const date = dayjs(value);
  return date.isValid() ? date.format("YYYY-MM-DD HH:mm:ss") : value;
}

function formatFen(value?: number) {
  if (value == null) {
    return "-";
  }
  return `¥${(value / 100).toFixed(2)}`;
}

function formatYuan(value?: number | string | null) {
  if (value == null || value === "") {
    return "-";
  }
  const numericValue = Number(value);
  return Number.isFinite(numericValue) ? `¥${numericValue.toFixed(2)}` : String(value);
}

function renderTypeTag(value?: number) {
  if (value === 1) {
    return <Tag color="green">已累加</Tag>;
  }
  if (value === 0) {
    return <Tag color="orange">待累加</Tag>;
  }
  return <Text type="secondary">-</Text>;
}

const MtAppOrderManage: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [searchForm] = Form.useForm<SearchFormValues>();
  const [settlementForm] = Form.useForm<SettlementFormValues>();
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [settlementLoading, setSettlementLoading] = useState(false);
  const [settlementExporting, setSettlementExporting] = useState(false);
  const [visibleUserLoading, setVisibleUserLoading] = useState(false);
  const [versionOptionLoading, setVersionOptionLoading] = useState(false);
  const [rows, setRows] = useState<MtAppOrderRow[]>([]);
  const [settlementRows, setSettlementRows] = useState<
    MtAppOrderSettlementRow[]
  >([]);
  const [visibleUsers, setVisibleUsers] = useState<VisibleUserOption[]>([]);
  const [versionOptions, setVersionOptions] = useState<
    MtAppOrderVersionOption[]
  >([]);
  const [hasExpandedScope, setHasExpandedScope] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [queryValues, setQueryValues] = useState<SearchFormValues>({});
  const [settlementPageNum, setSettlementPageNum] = useState(1);
  const [settlementPageSize, setSettlementPageSize] = useState(10);
  const [settlementTotal, setSettlementTotal] = useState(0);
  const [settlementQueryValues, setSettlementQueryValues] =
    useState<SettlementFormValues>({});
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const visibleUserSearchTimerRef = useRef<number | null>(null);

  const buildQueryParams = useCallback(
    (values: SearchFormValues, withPagination: boolean) => {
      const orderStartTime =
        values.orderTimeRange?.[0] ??
        mergeDateAndClock(values.startDate, values.startClock);
      const orderEndTime =
        values.orderTimeRange?.[1] ??
        mergeDateAndClock(values.endDate, values.endClock, "end");
      return {
        orderId: values.orderId?.trim() || undefined,
        orderViewId: values.orderViewId?.trim() || undefined,
        poiId: values.poiId?.trim() || undefined,
        shopName: values.shopName?.trim() || undefined,
        payStatus: values.payStatus,
        rightStatus: values.rightStatus?.trim() || undefined,
        version: values.version?.trim() || undefined,
        versionName: values.versionName?.trim() || undefined,
        targetUserId: values.targetUserId,
        orderStartTime: orderStartTime?.format("YYYY-MM-DD HH:mm:ss"),
        orderEndTime: orderEndTime?.format("YYYY-MM-DD HH:mm:ss"),
        ...(withPagination ? { pageNum, pageSize } : {}),
      };
    },
    [pageNum, pageSize],
  );

  const buildSettlementQueryParams = useCallback(
    (values: SettlementFormValues, withPagination: boolean) => ({
      statsMonth: values.statsMonth?.format("YYYY-MM"),
      poiId: values.poiId?.trim() || undefined,
      ...(withPagination
        ? { pageNum: settlementPageNum, pageSize: settlementPageSize }
        : {}),
    }),
    [settlementPageNum, settlementPageSize],
  );

  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getMtAppOrders(
        buildQueryParams(queryValues, true),
      );
      setRows(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load Meituan app orders:", error);
      message.error("加载美团订单失败");
    } finally {
      setLoading(false);
    }
  }, [buildQueryParams, queryValues]);

  const loadSettlementRows = useCallback(async () => {
    if (!settlementQueryValues.statsMonth) {
      return;
    }
    setSettlementLoading(true);
    try {
      const response = await getMtAppOrderSettlementStats(
        buildSettlementQueryParams(settlementQueryValues, true),
      );
      setSettlementRows(response.list);
      setSettlementTotal(response.total);
    } catch (error) {
      console.error("Failed to load Meituan settlement stats:", error);
      message.error("加载结算统计失败");
    } finally {
      setSettlementLoading(false);
    }
  }, [buildSettlementQueryParams, settlementQueryValues]);

  const loadVisibleUsers = useCallback(async (keyword?: string) => {
    setVisibleUserLoading(true);
    try {
      const response = await getVisibleShopUsers(keyword);
      setVisibleUsers(response);
      if (!keyword) {
        setHasExpandedScope(response.length > 1);
      }
    } catch (error) {
      console.error("Failed to load visible users:", error);
    } finally {
      setVisibleUserLoading(false);
    }
  }, []);

  const loadVersionOptions = useCallback(async () => {
    setVersionOptionLoading(true);
    try {
      const response = await getMtAppOrderVersionOptions();
      setVersionOptions(response);
    } catch (error) {
      console.error("Failed to load Meituan app order version options:", error);
    } finally {
      setVersionOptionLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  useEffect(() => {
    void loadSettlementRows();
  }, [loadSettlementRows]);

  useEffect(() => {
    void loadVisibleUsers();
  }, [loadVisibleUsers]);

  useEffect(() => {
    void loadVersionOptions();
  }, [loadVersionOptions]);

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

  const getPickerPopupContainer = useCallback(
    (triggerNode: HTMLElement) => triggerNode.parentElement ?? document.body,
    [],
  );

  const versionNumberSelectOptions = useMemo(() => {
    const versions = new Set<string>();
    return versionOptions
      .filter((item) => {
        if (!item.version || versions.has(item.version)) {
          return false;
        }
        versions.add(item.version);
        return true;
      })
      .map((item) => ({
        label: item.version,
        value: item.version,
      }));
  }, [versionOptions]);

  const versionNameSelectOptions = useMemo(() => {
    const names = new Set<string>();
    return versionOptions
      .filter((item) => {
        if (!item.versionName || names.has(item.versionName)) {
          return false;
        }
        names.add(item.versionName);
        return true;
      })
      .map((item) => ({
        label: item.versionName,
        value: item.versionName,
      }));
  }, [versionOptions]);

  const filterSelectOption = useCallback(
    (input: string, option?: { label?: string }) => {
      return (option?.label ?? "").toLowerCase().includes(input.toLowerCase());
    },
    [],
  );

  const handleSearch = async () => {
    if (isCompact) {
      const startDate = searchForm.getFieldValue("startDate");
      const startClock = searchForm.getFieldValue("startClock");
      const endDate = searchForm.getFieldValue("endDate");
      const endClock = searchForm.getFieldValue("endClock");

      if (startClock && !startDate) {
        message.warning("请先选择开始日期，再选择开始时间");
        return;
      }

      if (endClock && !endDate) {
        message.warning("请先选择结束日期，再选择结束时间");
        return;
      }
    }

    const values = await searchForm.validateFields();
    setPageNum(1);
    setQueryValues(values);
  };

  const handleReset = () => {
    searchForm.resetFields();
    setPageNum(1);
    setPageSize(10);
    setQueryValues({});
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportMtAppOrders(buildQueryParams(queryValues, false));
    } finally {
      setExporting(false);
    }
  };

  const handleSettlementSearch = async () => {
    const values = await settlementForm.validateFields();
    setSettlementPageNum(1);
    setSettlementQueryValues(values);
  };

  const handleSettlementReset = () => {
    settlementForm.resetFields();
    setSettlementPageNum(1);
    setSettlementPageSize(10);
    setSettlementTotal(0);
    setSettlementRows([]);
    setSettlementQueryValues({});
  };

  const handleSettlementExport = async () => {
    const values =
      settlementQueryValues.statsMonth
        ? settlementQueryValues
        : await settlementForm.validateFields();
    setSettlementExporting(true);
    try {
      await exportMtAppOrderSettlementStats(
        buildSettlementQueryParams(values, false),
      );
    } finally {
      setSettlementExporting(false);
    }
  };

  const columns: ColumnsType<MtAppOrderRow> = [
    {
      title: "订单号",
      dataIndex: "orderId",
      key: "orderId",
      width: 180,
      fixed: isCompact ? undefined : "left",
      render: (value: string, record) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{value || "-"}</Text>
          <Text type="secondary">展示单号：{record.orderViewId || "-"}</Text>
        </Space>
      ),
    },
    {
      title: "门店",
      dataIndex: "shopName",
      key: "shopName",
      width: 220,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.shopName || "-"}</Text>
          <Text type="secondary">店铺 ID：{record.poiId || "-"}</Text>
        </Space>
      ),
    },
    // {
    //   title: "支付/权益",
    //   key: "status",
    //   width: 150,
    //   render: (_value, record) => (
    //     <Space orientation="vertical" size={0}>
    //       <Text>支付状态：{record.payStatus ?? "-"}</Text>
    //       <Text type="secondary">权益状态：{record.rightStatus || "-"}</Text>
    //     </Space>
    //   ),
    // },
    {
      title: "版本",
      key: "version",
      width: 220,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.versionName || record.version || "-"}</Text>
          <Text type="secondary">
            版本号：{record.version || "-"} / 天数：{record.days ?? "-"}
          </Text>
        </Space>
      ),
    },
    {
      title: "实付金额",
      dataIndex: "realPayPrice",
      key: "realPayPrice",
      width: 110,
      render: (value?: number) => formatFen(value),
    },
    {
      title: "服务周期",
      key: "serviceTime",
      width: 180,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{formatDateTime(record.startTime)}</Text>
          <Text type="secondary">{formatDateTime(record.endTime)}</Text>
        </Space>
      ),
    },
    {
      title: "下单时间",
      dataIndex: "orderTime",
      key: "orderTime",
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: "权益累加",
      dataIndex: "type",
      key: "type",
      width: 100,
      render: (value?: number) => renderTypeTag(value),
    },
    {
      title: "联系方式",
      key: "contact",
      width: 220,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.phone || "-"}</Text>
          <Text type="secondary">{record.address || "-"}</Text>
        </Space>
      ),
    },
  ];

  const settlementColumns: ColumnsType<MtAppOrderSettlementRow> = [
    {
      title: "订单号",
      dataIndex: "orderId",
      key: "orderId",
      width: 180,
      fixed: isCompact ? undefined : "left",
      render: (value?: string) => <Text strong>{value || "-"}</Text>,
    },
    {
      title: "下单时间",
      dataIndex: "orderTime",
      key: "orderTime",
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: "店铺名称",
      dataIndex: "shopName",
      key: "shopName",
      width: 220,
      render: (value: string | undefined, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{value || "-"}</Text>
          <Text type="secondary">门店 ID：{record.poiId || "-"}</Text>
        </Space>
      ),
    },
    {
      title: "手机号",
      dataIndex: "phone",
      key: "phone",
      width: 130,
      render: (value?: string) => value || "-",
    },
    {
      title: "付费规格",
      dataIndex: "paidSpec",
      key: "paidSpec",
      width: 180,
      render: (value?: string) => value || "-",
    },
    {
      title: "付费金额",
      dataIndex: "paidAmount",
      key: "paidAmount",
      width: 120,
      render: (value?: number | string) => formatYuan(value),
    },
    {
      title: "本月使用天数",
      dataIndex: "currentMonthUsedDays",
      key: "currentMonthUsedDays",
      width: 130,
      render: (value?: number) => value ?? 0,
    },
    {
      title: "上月使用天数",
      dataIndex: "previousMonthUsedDays",
      key: "previousMonthUsedDays",
      width: 130,
      render: (value?: number) => value ?? 0,
    },
    {
      title: "剩余未使用天数",
      dataIndex: "remainingUnusedDays",
      key: "remainingUnusedDays",
      width: 150,
      render: (value?: number) => value ?? 0,
    },
    {
      title: "美团扣点20%",
      dataIndex: "meituanDeductionAmount",
      key: "meituanDeductionAmount",
      width: 120,
      render: (value?: number | string) => formatYuan(value),
    },
    {
      title: "上月实际收入",
      dataIndex: "previousMonthActualIncome",
      key: "previousMonthActualIncome",
      width: 120,
      render: (value?: number | string) => formatYuan(value),
    },
    {
      title: "本月实际收入",
      dataIndex: "currentMonthActualIncome",
      key: "currentMonthActualIncome",
      width: 120,
      render: (value?: number | string) => formatYuan(value),
    },
    {
      title: "剩余未结实际收入",
      dataIndex: "remainingUnsettledActualIncome",
      key: "remainingUnsettledActualIncome",
      width: 150,
      render: (value?: number | string) => formatYuan(value),
    },
  ];

  const orderDetailContent = (
    <>
      <div className={pageStyles.toolbar}>
        <Form<SearchFormValues>
          form={searchForm}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={() => void handleSearch()}
        >
          <Form.Item name="poiId">
            <Input
              allowClear
              placeholder="门店 ID"
              style={getResponsiveControlStyle(isCompact, 180)}
            />
          </Form.Item>
          <Form.Item name="shopName">
            <Input
              allowClear
              placeholder="门店名称"
              style={getResponsiveControlStyle(isCompact, 180)}
            />
          </Form.Item>
          {isCompact ? (
            <>
              <Form.Item
                label="开始时间"
                className={pageStyles.compactFull}
                style={{ marginBottom: 0 }}
              >
                <Space style={{ width: "100%" }} size={8}>
                  <Form.Item name="startDate" noStyle>
                    <DatePicker
                      allowClear
                      format="YYYY-MM-DD"
                      style={{ width: "100%" }}
                      getPopupContainer={getPickerPopupContainer}
                      onChange={(value) => {
                        if (!value) {
                          searchForm.setFieldValue("startClock", undefined);
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
              <Form.Item
                label="结束时间"
                className={pageStyles.compactFull}
                style={{ marginBottom: 0 }}
              >
                <Space style={{ width: "100%" }} size={8}>
                  <Form.Item name="endDate" noStyle>
                    <DatePicker
                      allowClear
                      format="YYYY-MM-DD"
                      style={{ width: "100%" }}
                      getPopupContainer={getPickerPopupContainer}
                      onChange={(value) => {
                        if (!value) {
                          searchForm.setFieldValue("endClock", undefined);
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
            <>
              <Form.Item name="orderId">
                <Input
                  allowClear
                  placeholder="订单号"
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              <Form.Item name="orderViewId">
                <Input
                  allowClear
                  placeholder="展示订单号"
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              {/* <Form.Item name="payStatus">
                <InputNumber
                  min={0}
                  precision={0}
                  placeholder="支付状态码"
                  style={getResponsiveControlStyle(isCompact, 140)}
                />
              </Form.Item>
              <Form.Item name="rightStatus">
                <Input
                  allowClear
                  placeholder="权益状态"
                  style={getResponsiveControlStyle(isCompact, 160)}
                />
              </Form.Item> */}
              <Form.Item name="version">
                <Select
                  allowClear
                  showSearch
                  filterOption={filterSelectOption}
                  loading={versionOptionLoading}
                  placeholder="版本号"
                  options={versionNumberSelectOptions}
                  style={getResponsiveControlStyle(isCompact, 160)}
                />
              </Form.Item>
              <Form.Item name="versionName">
                <Select
                  allowClear
                  showSearch
                  filterOption={filterSelectOption}
                  loading={versionOptionLoading}
                  placeholder="版本名称"
                  options={versionNameSelectOptions}
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              {/* {hasExpandedScope ? (
                <Form.Item name="targetUserId">
                  <Select
                    allowClear
                    showSearch
                    filterOption={false}
                    placeholder="目标用户"
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
              ) : null} */}
              <Form.Item name="orderTimeRange">
                <RangePicker
                  showTime
                  style={getResponsiveControlStyle(isCompact, 320)}
                  getPopupContainer={getPickerPopupContainer}
                />
              </Form.Item>
            </>
          )}
          {isCompact && advancedOpen ? (
            <>
              <Form.Item name="orderViewId">
                <Input
                  allowClear
                  placeholder="展示订单号"
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              <Form.Item name="poiId">
                <Input
                  allowClear
                  placeholder="门店 ID"
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              <Form.Item name="payStatus">
                <InputNumber
                  min={0}
                  precision={0}
                  placeholder="支付状态码"
                  style={getResponsiveControlStyle(isCompact, 140)}
                />
              </Form.Item>
              <Form.Item name="rightStatus">
                <Input
                  allowClear
                  placeholder="权益状态"
                  style={getResponsiveControlStyle(isCompact, 160)}
                />
              </Form.Item>
              <Form.Item name="version">
                <Select
                  allowClear
                  showSearch
                  filterOption={filterSelectOption}
                  loading={versionOptionLoading}
                  placeholder="版本号"
                  options={versionNumberSelectOptions}
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              <Form.Item name="versionName">
                <Select
                  allowClear
                  showSearch
                  filterOption={filterSelectOption}
                  loading={versionOptionLoading}
                  placeholder="版本名称"
                  options={versionNameSelectOptions}
                  style={getResponsiveControlStyle(isCompact, 180)}
                />
              </Form.Item>
              {hasExpandedScope ? (
                <Form.Item name="targetUserId">
                  <Select
                    allowClear
                    showSearch
                    filterOption={false}
                    placeholder="目标用户"
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
            </>
          ) : null}
          <Space className={pageStyles.searchActions}>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting}
              onClick={() => void handleExport()}
            >
              导出
            </Button>
            {isCompact ? (
              <Button
                onClick={() => setAdvancedOpen((open) => !open)}
                icon={advancedOpen ? <UpOutlined /> : <DownOutlined />}
              >
                {advancedOpen ? "收起筛选" : "更多筛选"}
              </Button>
            ) : null}
          </Space>
        </Form>
      </div>

      <Table<MtAppOrderRow>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1450 }}
        pagination={{
          current: pageNum,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: !isCompact,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (nextPage, nextPageSize) => {
            setPageNum(nextPage);
            setPageSize(nextPageSize);
          },
        }}
      />
    </>
  );

  const settlementContent = (
    <>
      <div className={pageStyles.toolbar}>
        <Form<SettlementFormValues>
          form={settlementForm}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={() => void handleSettlementSearch()}
        >
          <Form.Item
            name="statsMonth"
            rules={[{ required: true, message: "请选择统计月份" }]}
          >
            <DatePicker
              picker="month"
              allowClear
              format="YYYY年MM月"
              placeholder="统计月份"
              style={getResponsiveControlStyle(isCompact, 180)}
              getPopupContainer={getPickerPopupContainer}
            />
          </Form.Item>
          <Form.Item name="poiId">
            <Input
              allowClear
              placeholder="门店 ID（可选）"
              style={getResponsiveControlStyle(isCompact, 180)}
            />
          </Form.Item>
          <Space className={pageStyles.searchActions}>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>
              查询
            </Button>
            <Button icon={<ReloadOutlined />} onClick={handleSettlementReset}>
              重置
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={settlementExporting}
              onClick={() => void handleSettlementExport()}
            >
              导出
            </Button>
          </Space>
        </Form>
      </div>

      <Table<MtAppOrderSettlementRow>
        rowKey="id"
        loading={settlementLoading}
        columns={settlementColumns}
        dataSource={settlementRows}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1850 }}
        pagination={{
          current: settlementPageNum,
          pageSize: settlementPageSize,
          total: settlementTotal,
          showSizeChanger: true,
          showQuickJumper: !isCompact,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (nextPage, nextPageSize) => {
            setSettlementPageNum(nextPage);
            setSettlementPageSize(nextPageSize);
          },
        }}
      />
    </>
  );

  return (
    <PageContainer>
      <Tabs
        defaultActiveKey="orders"
        items={[
          {
            key: "orders",
            label: "订单明细",
            children: orderDetailContent,
          },
          {
            key: "settlement",
            label: "结算统计",
            children: settlementContent,
          },
        ]}
      />
    </PageContainer>
  );
};

export default MtAppOrderManage;
