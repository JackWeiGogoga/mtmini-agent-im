import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import PageContainer from "@/components/PageContainer";
import ReceiptCodePreviewLink from "@/components/ReceiptCodePreviewLink";
import { message } from "@/components/AppMessageContext";
import { useCurrentUser } from "@/contexts/AuthContext";
import { usePermission } from "@/hooks/usePermission";
import {
  exportSalesCommissionRows,
  getSalesCommissionRows,
  updateSalesCommissionRatio,
  updateSalesCommissionSettlementStatus,
} from "@/services/sales-api";
import type { SalesCommissionRow } from "@/types";
import { formatPowerWithUnit } from "@/utils/power-format";

const { Text } = Typography;

const SalesCommissionManage: React.FC = () => {
  const { hasPermission } = usePermission();
  const currentUser = useCurrentUser();
  const isAdminPortal = currentUser?.activePortal === "ADMIN";
  const canEditCommissionRatio =
    isAdminPortal && hasPermission("sales:commission:ratio-edit");
  const canSettleCommission =
    isAdminPortal && hasPermission("sales:commission:settle");
  const showActionColumn = canEditCommissionRatio || canSettleCommission;
  const [searchForm] = Form.useForm<{
    shopKeyword?: string;
    redemptionCode?: string;
    rechargeNo?: string;
  }>();
  const [ratioForm] = Form.useForm<{ commissionRatio: number }>();
  const [rows, setRows] = useState<SalesCommissionRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState({
    shopKeyword: "",
    redemptionCode: "",
    rechargeNo: "",
  });
  const [editingRow, setEditingRow] = useState<SalesCommissionRow | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getSalesCommissionRows({
        ...query,
        shopKeyword: query.shopKeyword || undefined,
        redemptionCode: query.redemptionCode || undefined,
        rechargeNo: query.rechargeNo || undefined,
        pageNum,
        pageSize,
      });
      setRows(response.list);
      setTotal(response.total);
    } finally {
      setLoading(false);
    }
  }, [pageNum, pageSize, query]);

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportSalesCommissionRows({
        shopKeyword: query.shopKeyword || undefined,
        redemptionCode: query.redemptionCode || undefined,
        rechargeNo: query.rechargeNo || undefined,
      });
    } finally {
      setExporting(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const columns: ColumnsType<SalesCommissionRow> = [
    {
      title: "兑换码",
      dataIndex: "redemptionCode",
      key: "redemptionCode",
      width: 120,
    },
    {
      title: "充值订单号",
      dataIndex: "rechargeNo",
      key: "rechargeNo",
      width: 220,
    },
    { title: "充值时间", dataIndex: "paidAt", key: "paidAt", width: 180 },
    { title: "商家名称", dataIndex: "shopName", key: "shopName", width: 180 },
    { title: "门店 ID", dataIndex: "poiId", key: "poiId", width: 160 },
    {
      title: "商家手机号",
      dataIndex: "shopPhone",
      key: "shopPhone",
      width: 150,
    },
    {
      title: "充值算力",
      dataIndex: "powerAmount",
      key: "powerAmount",
      width: 130,
      render: (value: number) => formatPowerWithUnit(value),
    },
    {
      title: "充值金额",
      dataIndex: "paidAmountFen",
      key: "paidAmountFen",
      width: 120,
      render: (value: number) => `¥${((value || 0) / 100).toFixed(2)}`,
    },
    {
      title: "佣金比例",
      dataIndex: "commissionRatio",
      key: "commissionRatio",
      width: 120,
      render: (value: number) => `${value}%`,
    },
    {
      title: "提成金额",
      dataIndex: "commissionAmountFen",
      key: "commissionAmountFen",
      width: 120,
      render: (value: number) => `¥${((value || 0) / 100).toFixed(2)}`,
    },
    {
      title: "网销用户",
      dataIndex: "salesUserPhone",
      key: "salesUserPhone",
      width: 150,
    },
    {
      title: "收款码",
      dataIndex: "receiptCodeUrl",
      key: "receiptCodeUrl",
      width: 120,
      render: (value?: string | null) =>
        value ? (
          <ReceiptCodePreviewLink
            src={value}
            thumbnailWidth={64}
            useThumbnail
          />
        ) : (
          <Text type="secondary">未上传</Text>
        ),
    },
    {
      title: "结算状态",
      dataIndex: "settlementStatus",
      key: "settlementStatus",
      width: 110,
      render: (value: string) =>
        value === "SETTLED" ? (
          <Tag color="green">已结算</Tag>
        ) : (
          <Tag color="orange">未结算</Tag>
        ),
    },
  ];

  if (showActionColumn) {
    columns.push({
      title: "操作",
      key: "actions",
      width: 180,
      render: (_, record) => (
        <Space size={4}>
          {canEditCommissionRatio ? (
            <Button
              type="link"
              disabled={record.settlementStatus === "SETTLED"}
              onClick={() => {
                setEditingRow(record);
                ratioForm.setFieldsValue({
                  commissionRatio: record.commissionRatio,
                });
              }}
            >
              改比例
            </Button>
          ) : null}
          {canSettleCommission ? (
            <Popconfirm
              title="确认标记为已结算？"
              disabled={record.settlementStatus === "SETTLED"}
              onConfirm={async () => {
                await updateSalesCommissionSettlementStatus(record.id, {
                  settlementStatus: "SETTLED",
                });
                message.success("结算状态已更新");
                await loadData();
              }}
            >
              <Button
                type="link"
                disabled={record.settlementStatus === "SETTLED"}
              >
                标记已结算
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    });
  }

  return (
    <PageContainer>
      <Card title="提成明细">
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Form
            form={searchForm}
            layout="inline"
            onFinish={(values) => {
              setPageNum(1);
              setQuery({
                shopKeyword: (values.shopKeyword || "").trim(),
                redemptionCode: (values.redemptionCode || "").trim(),
                rechargeNo: (values.rechargeNo || "").trim(),
              });
            }}
          >
            <Form.Item name="shopKeyword">
              <Input
                allowClear
                placeholder="搜索商家名称 / 门店 ID / 商家手机号"
                style={{ width: 240 }}
              />
            </Form.Item>
            <Form.Item name="redemptionCode">
              <Input allowClear placeholder="兑换码" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="rechargeNo">
              <Input
                allowClear
                placeholder="充值订单号"
                style={{ width: 220 }}
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
                    setQuery({
                      shopKeyword: "",
                      redemptionCode: "",
                      rechargeNo: "",
                    });
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
          <Table<SalesCommissionRow>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
            scroll={{ x: 1700 }}
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
      <Modal
        title="修改佣金比例"
        open={!!editingRow}
        onCancel={() => {
          setEditingRow(null);
          ratioForm.resetFields();
        }}
        onOk={async () => {
          if (!editingRow) {
            return;
          }
          const values = await ratioForm.validateFields();
          try {
            setSubmitting(true);
            await updateSalesCommissionRatio(editingRow.id, {
              commissionRatio: values.commissionRatio,
            });
            message.success("佣金比例已更新");
            setEditingRow(null);
            ratioForm.resetFields();
            await loadData();
          } finally {
            setSubmitting(false);
          }
        }}
        confirmLoading={submitting}
      >
        <Form form={ratioForm} layout="vertical">
          <Form.Item
            name="commissionRatio"
            label="佣金比例"
            rules={[{ required: true, message: "请输入佣金比例" }]}
          >
            <InputNumber
              min={0}
              max={100}
              precision={0}
              style={{ width: "100%" }}
              addonAfter="%"
            />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default SalesCommissionManage;
