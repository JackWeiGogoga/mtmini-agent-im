import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Typography,
} from "antd";
import { DownloadOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import PageContainer from "@/components/PageContainer";
import ReceiptCodePreviewLink from "@/components/ReceiptCodePreviewLink";
import { message } from "@/components/AppMessageContext";
import { usePermission } from "@/hooks/usePermission";
import {
  clearSalesReceiptCode,
  exportSalesRedemptionCodes,
  generateSalesRedemptionCode,
  getSalesRedemptionCodes,
} from "@/services/sales-api";
import type { SalesRedemptionCodeRow } from "@/types";

const { Paragraph, Text } = Typography;

const SalesRedemptionCodeManage: React.FC = () => {
  const { hasPermission } = usePermission();
  const [searchForm] = Form.useForm<{ keyword?: string }>();
  const [createForm] = Form.useForm<{ phone: string }>();
  const [rows, setRows] = useState<SalesRedemptionCodeRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [pageNum, setPageNum] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [keyword, setKeyword] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await getSalesRedemptionCodes({
        keyword: keyword || undefined,
        pageNum,
        pageSize,
      });
      setRows(response.list);
      setTotal(response.total);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNum, pageSize]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleExport = async () => {
    setExporting(true);
    try {
      await exportSalesRedemptionCodes({
        keyword: keyword || undefined,
      });
    } finally {
      setExporting(false);
    }
  };

  const columns: ColumnsType<SalesRedemptionCodeRow> = [
    { title: "兑换码", dataIndex: "code", key: "code", width: 120 },
    {
      title: "网销用户",
      dataIndex: "salesUserName",
      key: "salesUserName",
      width: 160,
    },
    {
      title: "手机号",
      dataIndex: "salesUserPhone",
      key: "salesUserPhone",
      width: 150,
    },
    {
      title: "收款码",
      dataIndex: "receiptCodeUrl",
      key: "receiptCodeUrl",
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
      title: "生成时间",
      dataIndex: "createTime",
      key: "createTime",
      width: 180,
    },
    {
      title: "操作",
      key: "actions",
      width: 140,
      render: (_, record) =>
        hasPermission("sales:redemption-code:receipt-clear") ? (
          <Popconfirm
            title="确认删除该网销用户的收款码？"
            onConfirm={async () => {
              await clearSalesReceiptCode(record.id);
              message.success("收款码已清空");
              await loadData();
            }}
          >
            <Button type="link" disabled={!record.receiptCodeUrl}>
              清空收款码
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <PageContainer>
      <Card title="兑换码">
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Paragraph style={{ marginBottom: 0 }}>
            超管可按网销手机号生成唯一兑换码，并查看或清空对应的当前收款码。
          </Paragraph>
          <Form
            form={searchForm}
            layout="inline"
            onFinish={(values) => {
              setPageNum(1);
              setKeyword((values.keyword || "").trim());
            }}
          >
            <Form.Item name="keyword">
              <Input
                allowClear
                placeholder="搜索兑换码 / 网销手机号 / 用户名"
                style={{ width: 280 }}
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
                    setKeyword("");
                  }}
                >
                  重置
                </Button>
                {hasPermission("sales:redemption-code:create") ? (
                  <Button type="primary" onClick={() => setModalOpen(true)}>
                    生成兑换码
                  </Button>
                ) : null}
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
          <Table<SalesRedemptionCodeRow>
            rowKey="id"
            loading={loading}
            columns={columns}
            dataSource={rows}
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
        title="生成兑换码"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          createForm.resetFields();
        }}
        onOk={async () => {
          try {
            const values = await createForm.validateFields();
            setSubmitting(true);
            await generateSalesRedemptionCode({ phone: values.phone.trim() });
            message.success("兑换码生成成功");
            setModalOpen(false);
            createForm.resetFields();
            await loadData();
          } finally {
            setSubmitting(false);
          }
        }}
        confirmLoading={submitting}
      >
        <Form form={createForm} layout="vertical">
          <Form.Item
            name="phone"
            label="网销手机号"
            rules={[{ required: true, message: "请输入网销手机号" }]}
          >
            <Input placeholder="输入 PC 端注册手机号" />
          </Form.Item>
        </Form>
      </Modal>
    </PageContainer>
  );
};

export default SalesRedemptionCodeManage;
