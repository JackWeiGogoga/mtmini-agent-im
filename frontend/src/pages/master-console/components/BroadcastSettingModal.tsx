import React, { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Spin, Tabs, Tag, TimePicker, Typography } from "antd";
import dayjs from "dayjs";
import { message } from "@/components/AppMessageContext";
import {
  deleteMasterConsoleBroadcastSetting,
  getMasterConsoleBroadcastSetting,
  saveMasterConsoleBroadcastSetting,
} from "@/services/master-console-api";
import type { ImBroadcastRepeatMode, ImBroadcastSetting } from "@/types";
import BroadcastSettingHistoryTable from "./BroadcastSettingHistory";

const { Text } = Typography;
const CONTENT_MAX_LENGTH = 100;
const TIME_FORMAT = "HH:mm:ss";
const DEFAULT_SEND_TIME = "10:00:00";

export const WEEK_DAY_OPTIONS = [
  { label: "周一", value: 1 },
  { label: "周二", value: 2 },
  { label: "周三", value: 3 },
  { label: "周四", value: 4 },
  { label: "周五", value: 5 },
  { label: "周六", value: 6 },
  { label: "周日", value: 7 },
];

export const REPEAT_MODE_OPTIONS: { label: string; value: ImBroadcastRepeatMode }[] = [
  { label: "每次（每周重复）", value: "EVERY" },
  { label: "一次（发送后失效）", value: "ONCE" },
];

type TabKey = "setting" | "history";

interface FormValues {
  weekDays: number[];
  repeatMode: ImBroadcastRepeatMode;
  sendTime: dayjs.Dayjs;
  content: string;
}

interface BroadcastSettingModalProps {
  open: boolean;
  shopId?: number;
  shopName?: string;
  onClose: () => void;
  /** 保存/删除成功后回传最新配置，供列表就地刷新 */
  onSaved: (shopId: number, setting: ImBroadcastSetting) => void;
}

const BroadcastSettingModal: React.FC<BroadcastSettingModalProps> = ({
  open, shopId, shopName, onClose, onSaved,
}) => {
  const [form] = Form.useForm<FormValues>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [setting, setSetting] = useState<ImBroadcastSetting>();
  const [activeTab, setActiveTab] = useState<TabKey>("setting");

  const applySetting = useCallback((value: ImBroadcastSetting) => {
    setSetting(value);
    form.setFieldsValue({
      weekDays: value.weekDays || [],
      repeatMode: value.repeatMode || "EVERY",
      sendTime: dayjs(value.sendTime || DEFAULT_SEND_TIME, TIME_FORMAT),
      content: value.content || "",
    });
  }, [form]);

  // 弹窗本体常驻，destroyOnHidden 只销毁子节点；不重置的话换一家门店会停留在上次的 tab。
  useEffect(() => {
    if (open) setActiveTab("setting");
  }, [open, shopId]);

  useEffect(() => {
    if (!open || !shopId) return;
    setLoading(true);
    getMasterConsoleBroadcastSetting(shopId)
      .then(applySetting)
      .catch(() => message.error("加载群发配置失败"))
      .finally(() => setLoading(false));
  }, [applySetting, open, shopId]);

  const handleSubmit = useCallback(async () => {
    if (!shopId) return;
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const saved = await saveMasterConsoleBroadcastSetting(shopId, {
        weekDays: values.weekDays,
        repeatMode: values.repeatMode,
        sendTime: values.sendTime.format(TIME_FORMAT),
        content: values.content.trim(),
      });
      onSaved(shopId, saved);
      message.success("群发配置已保存");
      onClose();
    } finally {
      setSubmitting(false);
    }
  }, [form, onClose, onSaved, shopId]);

  const handleDelete = useCallback(async () => {
    if (!shopId) return;
    setSubmitting(true);
    try {
      const cleared = await deleteMasterConsoleBroadcastSetting(shopId);
      onSaved(shopId, cleared);
      message.success("已清空定时群发");
      onClose();
    } finally {
      setSubmitting(false);
    }
  }, [onClose, onSaved, shopId]);

  return <Modal
    title={`${shopName || "门店"} · 群发配置`}
    open={open}
    onCancel={onClose}
    destroyOnHidden
    width={activeTab === "history" ? 960 : 560}
    footer={activeTab === "history" ? <Button onClick={onClose}>关闭</Button> : <Space>
      {setting?.configured ? <Popconfirm
        title="清空该门店的定时群发配置？"
        description="累计群发条数会保留，仅停止后续定时发送。"
        onConfirm={handleDelete}
      >
        <Button danger loading={submitting}>删除</Button>
      </Popconfirm> : null}
      <Button onClick={onClose}>取消</Button>
      <Button type="primary" loading={submitting} onClick={() => void handleSubmit()}>保存</Button>
    </Space>}
  >
    <Tabs
      activeKey={activeTab}
      onChange={(key) => setActiveTab(key as TabKey)}
      items={[
        { key: "setting", label: "当前配置" },
        { key: "history", label: "变更历史" },
      ]}
    />
    {activeTab === "history"
      ? <BroadcastSettingHistoryTable shopId={shopId} />
      : <Spin spinning={loading}>
      <Form form={form} layout="vertical" initialValues={{ repeatMode: "EVERY" }}>
        <Form.Item
          name="weekDays"
          label="发送日期"
          rules={[{ required: true, message: "请至少选择一个发送日期" }]}
        >
          <Select mode="multiple" allowClear placeholder="请选择日期" options={WEEK_DAY_OPTIONS} />
        </Form.Item>
        <Form.Item name="repeatMode" label="发送频率" rules={[{ required: true }]}>
          <Select options={REPEAT_MODE_OPTIONS} />
        </Form.Item>
        <Form.Item name="sendTime" label="发送时间" rules={[{ required: true, message: "请选择发送时间" }]}>
          <TimePicker format={TIME_FORMAT} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item
          name="content"
          label="发送内容"
          rules={[
            { required: true, whitespace: true, message: "请填写发送的内容" },
            { max: CONTENT_MAX_LENGTH, message: `发送内容最多${CONTENT_MAX_LENGTH}字` },
          ]}
          extra="规则：禁止发送敏感词信息，保存时会做敏感词校验。"
        >
          <Input.TextArea rows={4} showCount maxLength={CONTENT_MAX_LENGTH} placeholder="请填写发送的内容…" />
        </Form.Item>
        <Space size={8}>
          <Tag color={setting?.status === "ACTIVE" ? "green" : setting?.status === "ONCE_COMPLETED" ? "blue" : undefined}>
            {setting?.statusName || "未配置"}
          </Tag>
          {setting?.status === "ONCE_COMPLETED"
            ? <Text type="secondary">执行于 {setting.lastTriggerDate || "-"}</Text>
            : null}
          <Text type="secondary">累计已群发 {setting?.totalSentCount ?? 0} 条</Text>
        </Space>
      </Form>
    </Spin>}
  </Modal>;
};

export default BroadcastSettingModal;
