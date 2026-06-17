import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import PageContainer from "@/components/PageContainer";
import { message } from "@/components/AppMessageContext";
import { useResponsive } from "@/hooks";
import {
  exportPowerRechargeOrders,
  getPowerRechargeOrderDetail,
  getPowerRechargeOrders,
} from "@/services/power-recharge-order-api";
import type { PowerRechargeOrderDetail, PowerRechargeOrderRow } from "@/types";
import { formatPowerWithUnit } from "@/utils/power-format";
import { getResponsiveControlStyle } from "@/utils/responsive";

const { RangePicker } = DatePicker;
const { Text } = Typography;

type SearchFormValues = {
  keyword?: string;
  payStatus?: number;
  createdTimeRange?: [Dayjs, Dayjs];
};

const payStatusOptions = [
  { label: "待支付", value: 0 },
  { label: "支付中", value: 1 },
  { label: "支付成功", value: 2 },
  { label: "支付失败", value: 3 },
  { label: "已关闭", value: 4 },
];

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

function formatDiscountRate(discountRateBasisPoints?: number) {
  if (!discountRateBasisPoints) {
    return "-";
  }
  if (discountRateBasisPoints === 10000) {
    return "原价";
  }
  const discount = discountRateBasisPoints / 1000;
  const percent = (discountRateBasisPoints / 100).toFixed(0);
  return `${discount.toFixed(discount % 1 === 0 ? 0 : 1)} 折 (${percent}%)`;
}

function renderPayStatusTag(row: PowerRechargeOrderRow) {
  const label = row.payStatusDesc || String(row.payStatus ?? "-");
  if (row.payStatus === 2) {
    return <Tag color="green">{label}</Tag>;
  }
  if (row.payStatus === 3 || row.payStatus === 4) {
    return <Tag color="red">{label}</Tag>;
  }
  if (row.payStatus === 1) {
    return <Tag color="blue">{label}</Tag>;
  }
  return <Tag color="orange">{label}</Tag>;
}

const PowerRechargeOrderManage: React.FC = () => {
  const { isCompact } = useResponsive();
  const [searchForm] = Form.useForm<SearchFormValues>();
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [rows, setRows] = useState<PowerRechargeOrderRow[]>([]);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [queryValues, setQueryValues] = useState<SearchFormValues>({});
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [activeDetail, setActiveDetail] =
    useState<PowerRechargeOrderDetail | null>(null);

  const buildQueryParams = useCallback(
    (values: SearchFormValues, withPagination: boolean) => ({
        keyword: values.keyword?.trim() || undefined,
        payStatus: values.payStatus,
        startTime: values.createdTimeRange?.[0]
          ?.startOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
        endTime: values.createdTimeRange?.[1]
          ?.endOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
        ...(withPagination ? { pageNum, pageSize } : {}),
      }),
    [pageNum, pageSize],
  );

  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getPowerRechargeOrders(
        buildQueryParams(queryValues, true),
      );
      setRows(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load power recharge orders:", error);
      message.error("加载充值订单失败");
    } finally {
      setLoading(false);
    }
  }, [buildQueryParams, queryValues]);

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  const openDetail = useCallback(async (row: PowerRechargeOrderRow) => {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const detail = await getPowerRechargeOrderDetail(row.id);
      setActiveDetail(detail);
    } catch (error) {
      console.error("Failed to load recharge order detail:", error);
      message.error("加载充值订单详情失败");
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportPowerRechargeOrders(buildQueryParams(queryValues, false));
    } finally {
      setExporting(false);
    }
  };

  const columns: ColumnsType<PowerRechargeOrderRow> = useMemo(
    () => [
      {
        title: "充值订单号",
        dataIndex: "rechargeNo",
        key: "rechargeNo",
        width: 220,
        fixed: isCompact ? undefined : "left",
        render: (value: string, record) => (
          <Space orientation="vertical" size={0}>
            <Text strong>{value}</Text>
            <Text type="secondary">
              创建：{formatDateTime(record.createTime)}
            </Text>
          </Space>
        ),
      },
      {
        title: "门店",
        key: "shop",
        width: 220,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text strong>{record.shopName || "-"}</Text>
            <Text type="secondary">店铺 ID：{record.poiId || "-"}</Text>
          </Space>
        ),
      },
      {
        title: "充值算力",
        dataIndex: "powerAmount",
        key: "powerAmount",
        width: 120,
        render: (value: number) => formatPowerWithUnit(value),
      },
      {
        title: "原价 / 实付",
        key: "amount",
        width: 140,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text>{formatFen(record.originalAmountFen)}</Text>
            <Text strong>{formatFen(record.totalAmountFen)}</Text>
          </Space>
        ),
      },
      {
        title: "优惠口径",
        key: "discount",
        width: 180,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text>{record.discountTag || "-"}</Text>
            <Text type="secondary">
              {formatDiscountRate(record.discountRateBasisPoints)}
            </Text>
          </Space>
        ),
      },
      {
        title: "网销信息",
        key: "sales",
        width: 180,
        render: (_, record) => (
          <Space orientation="vertical" size={0}>
            <Text>{record.salesUserName || "-"}</Text>
            <Text type="secondary">{record.salesUserPhone || "无网销"}</Text>
          </Space>
        ),
      },
      {
        title: "兑换码",
        dataIndex: "redemptionCode",
        key: "redemptionCode",
        width: 120,
        render: (value?: string) => value || "-",
      },
      {
        title: "冻结佣金",
        key: "commission",
        width: 120,
        render: (_, record) =>
          record.defaultCommissionRatio != null
            ? `${record.defaultCommissionRatio}%`
            : "-",
      },
      {
        title: "支付状态",
        key: "payStatus",
        width: 120,
        render: (_, record) => renderPayStatusTag(record),
      },
      {
        title: "支付侧订单号",
        dataIndex: "paymentOrderNo",
        key: "paymentOrderNo",
        width: 220,
        render: (value?: string) => value || "-",
      },
      {
        title: "微信交易号",
        dataIndex: "transactionId",
        key: "transactionId",
        width: 220,
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
        title: "操作",
        key: "actions",
        width: 100,
        fixed: isCompact ? undefined : "right",
        render: (_, record) => (
          <Button type="link" onClick={() => void openDetail(record)}>
            详情
          </Button>
        ),
      },
    ],
    [isCompact, openDetail],
  );

  return (
    <PageContainer>
      <Card title="充值订单管理">
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Form
            form={searchForm}
            layout="inline"
            onFinish={(values) => {
              setPageNum(1);
              setQueryValues(values);
            }}
          >
            <Form.Item name="keyword">
              <Input
                allowClear
                placeholder="搜索充值单号 / 支付单号 / 微信交易号 / 门店 / 网销 / 兑换码"
                style={{
                  width: 420,
                  ...getResponsiveControlStyle(isCompact, 420),
                }}
              />
            </Form.Item>
            <Form.Item name="payStatus">
              <Select
                allowClear
                options={payStatusOptions}
                placeholder="支付状态"
                style={{
                  width: 150,
                  ...getResponsiveControlStyle(isCompact, 150),
                }}
              />
            </Form.Item>
            <Form.Item name="createdTimeRange">
              <RangePicker
                showTime
                style={{ ...getResponsiveControlStyle(isCompact, 320) }}
              />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  搜索
                </Button>
                <Button
                  onClick={() => {
                    searchForm.resetFields();
                    setPageNum(1);
                    setPageSize(10);
                    setQueryValues({});
                  }}
                >
                  重置
                </Button>
                <Button
                  icon={<DownloadOutlined />}
                  loading={exporting}
                  onClick={() => void handleExport()}
                >
                  导出
                </Button>
              </Space>
            </Form.Item>
          </Form>
          <Table<PowerRechargeOrderRow>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: 2200 }}
            pagination={{
              current: pageNum,
              pageSize,
              total,
              showSizeChanger: true,
              onChange: (nextPage, nextPageSize) => {
                setPageNum(nextPage);
                setPageSize(nextPageSize);
              },
            }}
          />
        </Space>
      </Card>
      <Drawer
        title={
          activeDetail?.rechargeNo
            ? `充值订单详情 · ${activeDetail.rechargeNo}`
            : "充值订单详情"
        }
        open={detailOpen}
        size={860}
        onClose={() => {
          setDetailOpen(false);
          setActiveDetail(null);
        }}
      >
        {detailLoading ? (
          <Text type="secondary">详情加载中...</Text>
        ) : activeDetail ? (
          <Space orientation="vertical" size={20} style={{ width: "100%" }}>
            <Descriptions title="对账关键信息" bordered column={2} size="small">
              <Descriptions.Item label="充值订单号">
                {activeDetail.rechargeNo}
              </Descriptions.Item>
              <Descriptions.Item label="支付状态">
                {renderPayStatusTag(activeDetail)}
              </Descriptions.Item>
              <Descriptions.Item label="支付侧订单号">
                {activeDetail.paymentOrderNo || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="微信交易号">
                {activeDetail.transactionId || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {formatDateTime(activeDetail.createTime)}
              </Descriptions.Item>
              <Descriptions.Item label="支付成功时间">
                {formatDateTime(activeDetail.paidAt)}
              </Descriptions.Item>
            </Descriptions>

            <Descriptions
              title="门店与网销信息"
              bordered
              column={2}
              size="small"
            >
              <Descriptions.Item label="门店名称">
                {activeDetail.shopName || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="店铺 ID">
                {activeDetail.poiId || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="EPOI ID">
                {activeDetail.epoiId || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="网销用户">
                {activeDetail.salesUserName || activeDetail.salesUserId || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="网销手机号">
                {activeDetail.salesUserPhone || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="兑换码">
                {activeDetail.redemptionCode || "-"}
              </Descriptions.Item>
            </Descriptions>

            <Descriptions
              title="金额与优惠信息"
              bordered
              column={2}
              size="small"
            >
              <Descriptions.Item label="充值算力">
                {formatPowerWithUnit(activeDetail.powerAmount)}
              </Descriptions.Item>
              <Descriptions.Item label="价格单位">
                {activeDetail.unitPower && activeDetail.unitPriceFen != null
                  ? `${formatPowerWithUnit(activeDetail.unitPower)} = ${formatFen(activeDetail.unitPriceFen)}`
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="原价金额">
                {formatFen(activeDetail.originalAmountFen)}
              </Descriptions.Item>
              <Descriptions.Item label="实付金额">
                {formatFen(activeDetail.totalAmountFen)}
              </Descriptions.Item>
              <Descriptions.Item label="折扣标签">
                {activeDetail.discountTag || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="折扣比例">
                {formatDiscountRate(activeDetail.discountRateBasisPoints)}
              </Descriptions.Item>
              <Descriptions.Item label="优惠口径">
                {activeDetail.discountContextType || "-"}
              </Descriptions.Item>
              <Descriptions.Item label="冻结佣金比例">
                {activeDetail.defaultCommissionRatio != null
                  ? `${activeDetail.defaultCommissionRatio}%`
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label="佣金窗口起点">
                {formatDateTime(activeDetail.commissionWindowStartAt)}
              </Descriptions.Item>
              <Descriptions.Item label="更新时间">
                {formatDateTime(activeDetail.updateTime)}
              </Descriptions.Item>
            </Descriptions>

          </Space>
        ) : null}
      </Drawer>
    </PageContainer>
  );
};

export default PowerRechargeOrderManage;
