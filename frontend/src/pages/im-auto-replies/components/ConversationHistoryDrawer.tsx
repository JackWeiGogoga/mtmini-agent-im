import { useCallback, useEffect, useRef, useState } from "react";
import { Avatar, Divider, Drawer, Empty, Space, Spin, Tag, Typography } from "antd";
import { CustomerServiceOutlined, RobotOutlined, ShopOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import type { ImConversationHistoryPage, ImConversationMessage } from "@/types";

const { Text, Paragraph } = Typography;

interface ConversationSummary {
  conversationId: number;
  shopName?: string;
  openUserId?: number;
  messageCount?: number;
}

interface Props {
  open: boolean;
  conversation?: ConversationSummary;
  onClose: () => void;
  loadHistory: (
    conversationId: number,
    params?: { cursorTime?: string; cursorId?: number; pageSize?: number },
  ) => Promise<ImConversationHistoryPage>;
}

function roleMeta(role: ImConversationMessage["senderRole"]) {
  if (role === "USER") return { label: "顾客", color: "blue", icon: <CustomerServiceOutlined /> };
  if (role === "AI") return { label: "AI 自动回复", color: "purple", icon: <RobotOutlined /> };
  return { label: "商家人工", color: "green", icon: <ShopOutlined /> };
}

export default function ConversationHistoryDrawer({ open, conversation, onClose, loadHistory }: Props) {
  const [items, setItems] = useState<ImConversationMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [cursor, setCursor] = useState<{ cursorTime?: string; cursorId?: number }>({});
  const scrollRef = useRef<HTMLDivElement>(null);

  const loadInitial = useCallback(async () => {
    if (!conversation) return;
    setLoading(true);
    try {
      const page = await loadHistory(conversation.conversationId, { pageSize: 30 });
      setItems(page.list || []);
      setHasMore(Boolean(page.hasMore));
      setCursor({ cursorTime: page.nextCursorTime, cursorId: page.nextCursorId });
      window.requestAnimationFrame(() => {
        const container = scrollRef.current;
        if (container) container.scrollTop = container.scrollHeight;
      });
    } finally {
      setLoading(false);
    }
  }, [conversation, loadHistory]);

  useEffect(() => {
    if (open) void loadInitial();
    else {
      setItems([]);
      setCursor({});
      setHasMore(false);
    }
  }, [loadInitial, open]);

  const loadOlder = useCallback(async () => {
    if (!conversation || !hasMore || loadingMore || !cursor.cursorTime || !cursor.cursorId) return;
    const container = scrollRef.current;
    const previousHeight = container?.scrollHeight || 0;
    setLoadingMore(true);
    try {
      const page = await loadHistory(conversation.conversationId, { ...cursor, pageSize: 30 });
      setItems((current) => [...(page.list || []), ...current]);
      setHasMore(Boolean(page.hasMore));
      setCursor({ cursorTime: page.nextCursorTime, cursorId: page.nextCursorId });
      window.requestAnimationFrame(() => {
        if (container) container.scrollTop += container.scrollHeight - previousHeight;
      });
    } finally {
      setLoadingMore(false);
    }
  }, [conversation, cursor, hasMore, loadHistory, loadingMore]);

  return <Drawer
    title={<Space direction="vertical" size={0}>
      <Text strong>{conversation?.shopName || "完整对话"}</Text>
      <Text type="secondary" style={{ fontSize: 12 }}>
        顾客 {conversation?.openUserId || "-"} · 共 {conversation?.messageCount ?? items.length} 条消息
      </Text>
    </Space>}
    open={open}
    onClose={onClose}
    width={760}
    destroyOnHidden
  >
    <div
      ref={scrollRef}
      onScroll={(event) => {
        if (event.currentTarget.scrollTop < 80) void loadOlder();
      }}
      style={{ height: "calc(100vh - 130px)", overflowY: "auto", padding: "0 12px 24px" }}
    >
      {loading ? <div style={{ padding: 80, textAlign: "center" }}><Spin /></div> : null}
      {!loading && hasMore ? <div style={{ textAlign: "center", padding: 8 }}>
        <Text type="secondary">{loadingMore ? "正在加载更早消息…" : "向上滚动加载历史 Session"}</Text>
      </div> : null}
      {!loading && !items.length ? <Empty description="暂无会话消息" /> : null}
      {items.map((item, index) => {
        const meta = roleMeta(item.senderRole);
        const previous = items[index - 1];
        const newSession = !previous || previous.sessionId !== item.sessionId;
        const isUser = item.senderRole === "USER";
        return <div key={item.id}>
          {newSession ? <Divider plain>
            Session {item.sessionNo || "-"} · {dayjs(item.sessionStartedAt || item.eventTime).format("YYYY-MM-DD HH:mm")}
            {item.sessionBoundaryReason === "IDLE_TIMEOUT" ? "（空闲后新会话）" : ""}
          </Divider> : null}
          <div style={{ display: "flex", flexDirection: isUser ? "row" : "row-reverse", gap: 10, marginBottom: 16 }}>
            <Avatar icon={meta.icon} style={{ background: isUser ? "#1677ff" : item.senderRole === "AI" ? "#722ed1" : "#389e0d" }} />
            <div style={{ maxWidth: "76%", textAlign: isUser ? "left" : "right" }}>
              <Space size={6} wrap>
                <Tag color={meta.color}>{meta.label}</Tag>
                {item.senderRole === "AI" && item.deliveryStatus ? <Tag>{item.deliveryStatus}</Tag> : null}
                <Text type="secondary" style={{ fontSize: 12 }}>{dayjs(item.eventTime).format("MM-DD HH:mm:ss")}</Text>
              </Space>
              <div style={{ marginTop: 6, padding: "10px 12px", borderRadius: 10, background: isUser ? "#f0f5ff" : "#fafafa", textAlign: "left" }}>
                <Paragraph style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{item.content || "（无正文）"}</Paragraph>
              </div>
            </div>
          </div>
        </div>;
      })}
    </div>
  </Drawer>;
}
