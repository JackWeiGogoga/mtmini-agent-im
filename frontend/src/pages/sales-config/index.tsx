import React, { useEffect, useState } from "react";
import { Button, Card, Descriptions, Space, Typography, Upload } from "antd";
import type { UploadFile } from "antd/es/upload/interface";
import PageContainer from "@/components/PageContainer";
import ReceiptCodePreviewLink from "@/components/ReceiptCodePreviewLink";
import { message } from "@/components/AppMessageContext";
import {
  getSalesSelfConfig,
  uploadSalesReceiptCode,
} from "@/services/sales-api";
import type { SalesSelfConfig as SalesSelfConfigData } from "@/types";

const { Text } = Typography;

const SalesConfig: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [config, setConfig] = useState<SalesSelfConfigData | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const response = await getSalesSelfConfig();
        setConfig(response);
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  return (
    <PageContainer>
      <Card title="网销配置" loading={loading}>
        <Space orientation="vertical" size={16} style={{ width: "100%" }}>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="网销用户">
              {config?.salesUserName || "-"}
            </Descriptions.Item>
            <Descriptions.Item label="手机号">
              {config?.salesUserPhone || "-"}
            </Descriptions.Item>
            <Descriptions.Item label="兑换码">
              {config?.redemptionCode || "-"}
            </Descriptions.Item>
            <Descriptions.Item label="当前收款码">
              {config?.receiptCodeUrl ? (
                <ReceiptCodePreviewLink
                  src={config.receiptCodeUrl}
                  thumbnailWidth={72}
                  useThumbnail
                />
              ) : (
                <Text type="secondary">未上传</Text>
              )}
            </Descriptions.Item>
          </Descriptions>
          <Space orientation="vertical" size={8} style={{ width: "100%" }}>
            <Text type="secondary">支持 PNG、JPG、WEBP，单张不超过 2MB。</Text>
            {!config?.receiptCodeUploadAllowed ? (
              <Text type="secondary">
                如需修改已上传收款码，请联系管理员清空后重新上传。
              </Text>
            ) : null}
            <Upload
              accept="image/png,image/jpeg,image/webp"
              beforeUpload={() => false}
              disabled={!config?.receiptCodeUploadAllowed}
              fileList={fileList}
              maxCount={1}
              onChange={({ fileList: nextFileList }) => {
                setFileList(nextFileList.slice(-1));
              }}
              onRemove={() => {
                setFileList([]);
              }}
            >
              <Button disabled={!config?.receiptCodeUploadAllowed}>
                选择收款码图片
              </Button>
            </Upload>
            <Button
              type="primary"
              disabled={
                !config?.receiptCodeUploadAllowed || fileList.length === 0
              }
              loading={submitting}
              onClick={async () => {
                const selectedFile = fileList[0]?.originFileObj;
                if (!selectedFile) {
                  message.warning("请先选择收款码图片");
                  return;
                }
                try {
                  setSubmitting(true);
                  const response = await uploadSalesReceiptCode(selectedFile);
                  setConfig(response);
                  setFileList([]);
                  message.success("收款码上传成功");
                } finally {
                  setSubmitting(false);
                }
              }}
            >
              上传收款码
            </Button>
          </Space>
        </Space>
      </Card>
    </PageContainer>
  );
};

export default SalesConfig;
