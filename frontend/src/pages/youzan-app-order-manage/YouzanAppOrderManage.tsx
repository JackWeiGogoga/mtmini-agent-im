import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import {
  CheckCircleOutlined,
  DownloadOutlined,
  EyeOutlined,
  ReloadOutlined,
  SearchOutlined,
} from "@ant-design/icons";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import type { ColumnsType } from "antd/es/table";
import { message } from "@/components/AppMessageContext";
import PageContainer, { usePageStyles } from "@/components/PageContainer";
import PermissionControl from "@/components/PermissionControl";
import { useResponsive } from "@/hooks";
import {
  exportYouzanAppOrders,
  getYouzanAppOrderDetail,
  getYouzanAppOrders,
  redeemYouzanAppOrder,
} from "@/services/youzan-app-order-api";
import { getBoundShops } from "@/services/shop-manage-api";
import type {
  UserShopBinding,
  YouzanAppOrderDetail,
  YouzanAppOrderRow,
} from "@/types";
import { getResponsiveControlStyle } from "@/utils/responsive";

const { RangePicker } = DatePicker;
const { Text } = Typography;

type SearchFormValues = {
  orderNo?: string;
  kdtId?: number;
  shopName?: string;
  buyerKeyword?: string;
  skuKeyword?: string;
  status?: number;
  useStatus?: string;
  payTimeRange?: [Dayjs, Dayjs];
};

type RedeemFormValues = {
  mtShopId?: number;
  remark?: string;
};

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

function renderPayStatus(value?: number) {
  if (value === 20) {
    return <Tag color="green">支付成功</Tag>;
  }
  if (value == null) {
    return <Text type="secondary">-</Text>;
  }
  return <Tag>{value}</Tag>;
}

function renderUseStatus(value?: string) {
  if (value === "USED") {
    return <Tag color="green">已核销</Tag>;
  }
  if (value === "UNUSED") {
    return <Tag color="orange">未核销</Tag>;
  }
  return <Text type="secondary">{value || "-"}</Text>;
}

const YouzanAppOrderManage: React.FC = () => {
  const { styles: pageStyles } = usePageStyles();
  const { isCompact } = useResponsive();
  const [searchForm] = Form.useForm<SearchFormValues>();
  const [redeemForm] = Form.useForm<RedeemFormValues>();
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [redeeming, setRedeeming] = useState(false);
  const [shopLoading, setShopLoading] = useState(false);
  const [rows, setRows] = useState<YouzanAppOrderRow[]>([]);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [queryValues, setQueryValues] = useState<SearchFormValues>({});
  const [detail, setDetail] = useState<YouzanAppOrderDetail | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [redeemTarget, setRedeemTarget] = useState<YouzanAppOrderRow | null>(null);
  const [shopOptions, setShopOptions] = useState<UserShopBinding[]>([]);

  const buildQueryParams = useCallback(
    (values: SearchFormValues, withPagination: boolean) => {
      const payStartTime = values.payTimeRange?.[0];
      const payEndTime = values.payTimeRange?.[1];
      return {
        orderNo: values.orderNo?.trim() || undefined,
        kdtId: values.kdtId,
        shopName: values.shopName?.trim() || undefined,
        buyerKeyword: values.buyerKeyword?.trim() || undefined,
        skuKeyword: values.skuKeyword?.trim() || undefined,
        status: values.status,
        useStatus: values.useStatus,
        payStartTime: payStartTime?.format("YYYY-MM-DD HH:mm:ss"),
        payEndTime: payEndTime?.format("YYYY-MM-DD HH:mm:ss"),
        ...(withPagination ? { pageNum, pageSize } : {}),
      };
    },
    [pageNum, pageSize],
  );

  const loadRows = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getYouzanAppOrders(buildQueryParams(queryValues, true));
      setRows(response.list);
      setTotal(response.total);
    } catch (error) {
      console.error("Failed to load Youzan app orders:", error);
      message.error("加载有赞订单失败");
    } finally {
      setLoading(false);
    }
  }, [buildQueryParams, queryValues]);

  const loadShopOptions = useCallback(async (keyword?: string) => {
    setShopLoading(true);
    try {
      const response = await getBoundShops({
        keyword: keyword?.trim() || undefined,
        pageNum: 1,
        pageSize: 30,
      });
      setShopOptions(response.list);
    } catch (error) {
      console.error("Failed to load shops:", error);
    } finally {
      setShopLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadRows();
  }, [loadRows]);

  const handleSearch = async () => {
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
      await exportYouzanAppOrders(buildQueryParams(queryValues, false));
    } finally {
      setExporting(false);
    }
  };

  const handleOpenDetail = async (record: YouzanAppOrderRow) => {
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      setDetail(await getYouzanAppOrderDetail(record.id));
    } catch (error) {
      console.error("Failed to load Youzan app order detail:", error);
      message.error("加载有赞订单详情失败");
    } finally {
      setDetailLoading(false);
    }
  };

  const handleOpenRedeem = (record: YouzanAppOrderRow) => {
    setRedeemTarget(record);
    redeemForm.resetFields();
    void loadShopOptions(record.shopName);
  };

  const handleRedeem = async () => {
    if (!redeemTarget) {
      return;
    }
    const values = await redeemForm.validateFields();
    setRedeeming(true);
    try {
      const result = await redeemYouzanAppOrder({
        orderId: redeemTarget.id,
        mtShopId: values.mtShopId!,
        remark: values.remark?.trim() || undefined,
      });
      message.success(`核销成功，有效期至 ${formatDateTime(result.newExpireAt)}`);
      setRedeemTarget(null);
      void loadRows();
    } catch (error) {
      console.error("Failed to redeem Youzan app order:", error);
      message.error("核销有赞订单失败");
    } finally {
      setRedeeming(false);
    }
  };

  const columns: ColumnsType<YouzanAppOrderRow> = [
    {
      title: "有赞订单",
      dataIndex: "orderNo",
      key: "orderNo",
      width: 190,
      fixed: isCompact ? undefined : "left",
      render: (value: string, record) => (
        <Space orientation="vertical" size={0}>
          <Text strong>{value || "-"}</Text>
          <Text type="secondary">应用：{record.appId ?? "-"}</Text>
        </Space>
      ),
    },
    {
      title: "有赞店铺",
      dataIndex: "shopName",
      key: "shopName",
      width: 220,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.shopName || "-"}</Text>
          <Text type="secondary">kdtId：{record.kdtId ?? "-"}</Text>
        </Space>
      ),
    },
    {
      title: "购买人",
      key: "buyer",
      width: 180,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.buyerPhone || "-"}</Text>
          <Text type="secondary">{record.openId || record.buyerId || "-"}</Text>
        </Space>
      ),
    },
    {
      title: "套餐",
      key: "sku",
      width: 180,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{record.skuVersionText || "-"}</Text>
          <Text type="secondary">天数：{record.skuIntervalText || "-"}</Text>
        </Space>
      ),
    },
    {
      title: "金额/状态",
      key: "status",
      width: 130,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          <Text>{formatFen(record.price)}</Text>
          {renderPayStatus(record.status)}
        </Space>
      ),
    },
    {
      title: "支付时间",
      dataIndex: "payTime",
      key: "payTime",
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: "核销",
      key: "useStatus",
      width: 220,
      render: (_value, record) => (
        <Space orientation="vertical" size={0}>
          {renderUseStatus(record.useStatus)}
          <Text type="secondary">
            {record.usedMtShopName || record.usedMtShopId || "-"}
          </Text>
          <Text type="secondary">{formatDateTime(record.usedAt)}</Text>
        </Space>
      ),
    },
    {
      title: "操作",
      key: "actions",
      width: 150,
      fixed: isCompact ? undefined : "right",
      render: (_value, record) => (
        <Space>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => void handleOpenDetail(record)}
          >
            详情
          </Button>
          <PermissionControl permission="youzan:app-order:redeem">
            <Button
              size="small"
              type="primary"
              icon={<CheckCircleOutlined />}
              disabled={record.useStatus === "USED" || record.status !== 20}
              onClick={() => handleOpenRedeem(record)}
            >
              核销
            </Button>
          </PermissionControl>
        </Space>
      ),
    },
  ];

  return (
    <PageContainer>
      <div className={pageStyles.toolbar}>
        <Form<SearchFormValues>
          form={searchForm}
          layout="inline"
          className={pageStyles.searchForm}
          onFinish={() => void handleSearch()}
        >
          <Form.Item name="orderNo">
            <Input
              allowClear
              placeholder="有赞订单号"
              style={getResponsiveControlStyle(isCompact, 190)}
            />
          </Form.Item>
          <Form.Item name="shopName">
            <Input
              allowClear
              placeholder="有赞店铺"
              style={getResponsiveControlStyle(isCompact, 170)}
            />
          </Form.Item>
          <Form.Item name="kdtId">
            <InputNumber
              min={1}
              precision={0}
              placeholder="kdtId"
              style={getResponsiveControlStyle(isCompact, 140)}
            />
          </Form.Item>
          <Form.Item name="buyerKeyword">
            <Input
              allowClear
              placeholder="手机号/openId"
              style={getResponsiveControlStyle(isCompact, 170)}
            />
          </Form.Item>
          <Form.Item name="skuKeyword">
            <Input
              allowClear
              placeholder="套餐"
              style={getResponsiveControlStyle(isCompact, 150)}
            />
          </Form.Item>
          <Form.Item name="status">
            <Select
              allowClear
              placeholder="支付状态"
              style={getResponsiveControlStyle(isCompact, 140)}
              options={[
                { label: "支付成功", value: 20 },
              ]}
            />
          </Form.Item>
          <Form.Item name="useStatus">
            <Select
              allowClear
              placeholder="核销状态"
              style={getResponsiveControlStyle(isCompact, 140)}
              options={[
                { label: "未核销", value: "UNUSED" },
                { label: "已核销", value: "USED" },
              ]}
            />
          </Form.Item>
          <Form.Item name="payTimeRange">
            <RangePicker
              showTime
              style={getResponsiveControlStyle(isCompact, 320)}
            />
          </Form.Item>
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
          </Space>
        </Form>
      </div>

      <Table<YouzanAppOrderRow>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        size={isCompact ? "small" : "middle"}
        scroll={{ x: 1460 }}
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

      <Modal
        title="有赞订单详情"
        open={detailOpen}
        footer={null}
        onCancel={() => setDetailOpen(false)}
        width={760}
        loading={detailLoading}
      >
        {detail ? (
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="订单号">{detail.orderNo}</Descriptions.Item>
            <Descriptions.Item label="有赞店铺">
              {detail.shopName || "-"} / kdtId: {detail.kdtId ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label="购买人">
              {detail.buyerPhone || "-"} / {detail.openId || detail.buyerId || "-"}
            </Descriptions.Item>
            <Descriptions.Item label="套餐">
              {detail.skuVersionText || "-"} / {detail.skuIntervalText || "-"} 天
            </Descriptions.Item>
            <Descriptions.Item label="金额/支付">
              {formatFen(detail.price)} / {detail.status ?? "-"} / {formatDateTime(detail.payTime)}
            </Descriptions.Item>
            <Descriptions.Item label="核销状态">
              {renderUseStatus(detail.useStatus)} {detail.usedRightCode || ""}
            </Descriptions.Item>
            <Descriptions.Item label="核销门店">
              {detail.usedMtShopId ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label="核销信息">
              {detail.usedByUsername || "-"} / {formatDateTime(detail.usedAt)}
            </Descriptions.Item>
            <Descriptions.Item label="备注">{detail.useRemark || "-"}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Modal>

      <Modal
        title="核销有赞订单"
        open={!!redeemTarget}
        confirmLoading={redeeming}
        onCancel={() => setRedeemTarget(null)}
        onOk={() => void handleRedeem()}
        okText="确认核销"
        destroyOnHidden
      >
        <Space orientation="vertical" style={{ width: "100%" }} size={12}>
          <Text>
            订单：<Text strong>{redeemTarget?.orderNo}</Text>
          </Text>
          <Text type="secondary">
            将按有赞订单天数授予美团门店 H5_PROMOTION 权益。
          </Text>
          <Form<RedeemFormValues> form={redeemForm} layout="vertical">
            <Form.Item
              name="mtShopId"
              label="美团门店"
              rules={[{ required: true, message: "请选择美团门店" }]}
            >
              <Select
                showSearch
                filterOption={false}
                placeholder="搜索并选择美团门店"
                loading={shopLoading}
                onSearch={(value) => void loadShopOptions(value)}
                onFocus={() => void loadShopOptions()}
                options={shopOptions.map((item) => ({
                  label: `${item.shopName || item.poiId} (${item.poiId || item.shopId})`,
                  value: item.shopId,
                }))}
              />
            </Form.Item>
            <Form.Item name="remark" label="备注">
              <Input.TextArea rows={3} maxLength={500} showCount />
            </Form.Item>
          </Form>
        </Space>
      </Modal>
    </PageContainer>
  );
};

export default YouzanAppOrderManage;
