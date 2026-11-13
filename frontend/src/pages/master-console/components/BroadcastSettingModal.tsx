import React, { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Spin, Tabs, Tag, TimePicker, Typography } from "antd";
import { ThunderboltOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { message } from "@/components/AppMessageContext";
import {
  deleteMasterConsoleBroadcastSetting,
  getMasterConsoleBroadcastSetting,
  optimizeMasterConsoleBroadcastContent,
  saveMasterConsoleBroadcastSetting,
} from "@/services/master-console-api";
import type { ImBroadcastRepeatMode, ImBroadcastSetting } from "@/types";
import BroadcastSettingHistoryTable from "./BroadcastSettingHistory";

const { Text } = Typography;
const CONTENT_MAX_LENGTH = 100;
const TIME_FORMAT = "HH:mm:ss";
const DEFAULT_SEND_TIME = "10:30:00";

// 官方预设话术，与 H5 保持一致。点击只覆盖内容，不动日期/时间/频率。
const PRESET_OPTIONS: { value: string; label: string; content: string }[] = [
  {
    value: "CANVASSING",
    label: "餐前拉客",
    content: "宝子，快到饭点啦~今天大厨元气满满，已为您准备了美味佳肴，快进店里看看呗~",
  },
  {
    value: "PROMOTION",
    label: "节日促销",
    content: "宝子，小店即将迎来X周年店庆！收藏店铺即可获得丰厚大礼！联系客服咨询~",
  },
  {
    value: "CLOSURE",
    label: "歇业公告",
    content: "宝子，X月X日-X月X日小店临时歇业哟~X月X日恢复营业，届时小店一定再恭迎您的大驾光临~记得收藏店铺避免迷路哟！",
  },
];
// 首次进入（从未配置）时的默认表单。
const DEFAULT_WEEK_DAYS = [1, 2, 3, 4, 5, 6, 7];

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
  const [optimizing, setOptimizing] = useState(false);
  const [presetType, setPresetType] = useState<string>("");
  const [setting, setSetting] = useState<ImBroadcastSetting>();
  const [activeTab, setActiveTab] = useState<TabKey>("setting");

  const applySetting = useCallback((value: ImBroadcastSetting) => {
    setSetting(value);
    // 只有「从未配置或已删除」(NONE) 才套默认；「一次」已执行完(ONCE_COMPLETED)仍保留上次文案，须原样回显。
    if ((value.status || "NONE") === "NONE") {
      // 从未配置：套用官方默认（周一至周日 / 10:30 / 每次 / 首个预设话术）。
      form.setFieldsValue({
        weekDays: DEFAULT_WEEK_DAYS,
        repeatMode: "EVERY",
        sendTime: dayjs(DEFAULT_SEND_TIME, TIME_FORMAT),
        content: PRESET_OPTIONS[0].content,
      });
      setPresetType(PRESET_OPTIONS[0].value);
      return;
    }
    const content = value.content || "";
    form.setFieldsValue({
      weekDays: value.weekDays || [],
      repeatMode: value.repeatMode || "EVERY",
      sendTime: dayjs(value.sendTime || DEFAULT_SEND_TIME, TIME_FORMAT),
      content,
    });
    setPresetType(PRESET_OPTIONS.find((item) => item.content === content)?.value || "");
  }, [form]);

  const handleSelectPreset = useCallback((option: (typeof PRESET_OPTIONS)[number]) => {
    // 只覆盖内容，日期/时间/频率保持不变。
    form.setFieldsValue({ content: option.content });
    setPresetType(option.value);
  }, [form]);

  const handleOptimize = useCallback(async () => {
    if (!shopId) return;
    const content = (form.getFieldValue("content") || "").trim();
    if (!content) {
      message.warning("请先填写要优化的内容");
      return;
    }
    setOptimizing(true);
    try {
      const optimized = await optimizeMasterConsoleBroadcastContent(shopId, content);
      form.setFieldsValue({ content: optimized });
      // 优化后不再是任何预设原文，取消高亮。
      setPresetType("");
    } finally {
      setOptimizing(false);
    }
  }, [form, shopId]);

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

  // 弹窗「忙」态：AI优化中或保存/删除提交中，此时禁用其余交互元素。
  const busy = submitting || optimizing;

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
        disabled={busy}
      >
        <Button danger loading={submitting} disabled={optimizing}>删除</Button>
      </Popconfirm> : null}
      <Button onClick={onClose} disabled={busy}>取消</Button>
      <Button type="primary" loading={submitting} disabled={optimizing} onClick={() => void handleSubmit()}>保存</Button>
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
      <Form form={form} layout="vertical" initialValues={{ repeatMode: "EVERY" }} disabled={busy}>
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
        <Space size={8} wrap style={{ display: "flex", marginBottom: 16 }}>
          {PRESET_OPTIONS.map((option) => (
            <Tag.CheckableTag
              key={option.value}
              checked={presetType === option.value}
              // CheckableTag 不消费 Form 的 disabled 上下文，忙时手动拦截点击并置灰。
              onChange={() => { if (!busy) handleSelectPreset(option); }}
              style={busy ? { opacity: 0.5, pointerEvents: "none" } : undefined}
            >
              {option.label}
            </Tag.CheckableTag>
          ))}
          <Button
            size="small"
            type="link"
            icon={<ThunderboltOutlined />}
            loading={optimizing}
            disabled={submitting}
            onClick={() => void handleOptimize()}
          >
            AI优化
          </Button>
        </Space>
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
