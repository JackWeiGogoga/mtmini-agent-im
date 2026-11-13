import { useEffect, useMemo, useRef, useState } from "react";
import { Button, Drawer, Empty, Input, Space, Table, Tabs, Tag, Tooltip, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ClearOutlined, HistoryOutlined, SendOutlined } from "@ant-design/icons";
import { message } from "@/components/AppMessageContext";
import { BRAND_MODEL_NAME } from "@/config/brandModel";
import { useAuth } from "@/contexts/AuthContext";
import { listTestChatLogs, sendTestChat } from "@/services/knowledge-api";
import type { TestChatLog, TestChatReply } from "@/types";

interface ChatTurn {
  role: "user" | "assistant";
  content: string;
  debug?: TestChatReply;
}

interface TestChatSession {
  sessionId: string;
  operatorName?: string;
  operatorUserId?: string;
  startTime?: string;
  messageCount: number;
  logs: TestChatLog[];
}

function newSessionId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const CATEGORY_TEXT: Record<string, string> = {
  GENERAL: "普通咨询",
  DELIVERY: "配送相关",
  AFTER_SALE: "售后相关",
};

function categoryLabel(category?: string): string {
  if (!category) return "-";
  return CATEGORY_TEXT[category] || category;
}

function DebugPanel({ debug }: { debug: TestChatReply }) {
  return (
    <div style={{ marginTop: 6, fontSize: 12, color: "rgba(0,0,0,0.55)" }}>
      <Space size={[4, 4]} wrap>
        <Tag color="blue">分类：{categoryLabel(debug.category)}</Tag>
        {debug.ragUsed ? (
          <Tag color="green">命中知识 {debug.ragChunkCount ?? 0} 片段</Tag>
        ) : (
          <Tag>未命中知识</Tag>
        )}
        {typeof debug.ragMaxScore === "number" && (
          <Tag>最高相似度 {debug.ragMaxScore.toFixed(3)}</Tag>
        )}
        {(debug.rewriteCount ?? 0) > 0 && <Tag color="orange">敏感词改写 {debug.rewriteCount} 次</Tag>}
        {debug.blocked && <Tag color="red">已触发安全兜底</Tag>}
      </Space>
      {/* 手机号识别结果：命中与否都要显示，否则分不清「没识别到」和「没跑识别」 */}
      <div style={{ marginTop: 4 }}>
        {(debug.userPhones?.length ?? 0) > 0 ? (
          <Space size={[4, 4]} wrap>
            <span>识别到手机号：</span>
            {debug.userPhones?.map((phone) => (
              <Tag key={phone} color="volcano" style={{ marginInlineEnd: 0 }}>
                <Typography.Text copyable={{ text: phone }}>{phone}</Typography.Text>
              </Tag>
            ))}
          </Space>
        ) : (
          <span style={{ color: "rgba(0,0,0,0.45)" }}>未在本轮输入中识别到手机号</span>
        )}
      </div>
      {debug.classificationDebug && (
        <div style={{ marginTop: 4, color: "rgba(0,0,0,0.45)" }}>
          分类模型原始输出：
          <span style={{ color: "rgba(0,0,0,0.65)" }}>
            {debug.classificationDebug.error
              ? "（分类调用异常，已兜底为普通类）"
              : debug.classificationDebug.rawOutput || "（空）"}
          </span>
          {debug.classificationDebug.promptSource && (
            <span>（提示词来源：{debug.classificationDebug.promptSource}）</span>
          )}
          {debug.classificationDebug.error && (
            <div style={{ color: "#d4380d" }}>分类异常：{debug.classificationDebug.error}</div>
          )}
        </div>
      )}
      {debug.sensitiveHit && (
        <div style={{ marginTop: 4, color: "#d4380d" }}>
          命中敏感词
          {debug.sensitiveHit.highestSeverity ? `（${debug.sensitiveHit.highestSeverity}）` : ""}：
          {debug.sensitiveHit.words && debug.sensitiveHit.words.length > 0
            ? debug.sensitiveHit.words.join("、")
            : "-"}
          {debug.sensitiveHit.categories && debug.sensitiveHit.categories.length > 0 && (
            <span style={{ color: "rgba(0,0,0,0.45)" }}>
              {" "}
              [类别：{debug.sensitiveHit.categories.join("、")}]
            </span>
          )}
        </div>
      )}
      {debug.ragHits && debug.ragHits.length > 0 && (
        <details style={{ marginTop: 4 }}>
          <summary style={{ cursor: "pointer" }}>查看命中的知识片段</summary>
          <ol style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {debug.ragHits.map((hit, index) => (
              <li key={index} style={{ marginBottom: 2 }}>
                {hit.chunkText}
                {typeof hit.score === "number" && (
                  <span style={{ color: "rgba(0,0,0,0.35)" }}>（{hit.score.toFixed(3)}）</span>
                )}
              </li>
            ))}
          </ol>
        </details>
      )}
      {debug.diagnostics && <RecallDiagnostics diagnostics={debug.diagnostics} />}
    </div>
  );
}

function RecallDiagnostics({ diagnostics }: { diagnostics: NonNullable<TestChatReply["diagnostics"]> }) {
  const threshold = diagnostics.threshold;
  const candidates = diagnostics.topCandidates ?? [];
  const noCandidateButHasKnowledge = diagnostics.hasShopKnowledge && candidates.length === 0;
  return (
    <details style={{ marginTop: 4 }}>
      <summary style={{ cursor: "pointer" }}>召回诊断（忽略阈值的 Top 候选）</summary>
      <div style={{ margin: "4px 0 0", color: "rgba(0,0,0,0.55)" }}>
        <div>
          阈值 cosine ≥ {typeof threshold === "number" ? threshold.toFixed(2) : "-"}
          {"，"}门店有可用知识：{diagnostics.hasShopKnowledge ? "是" : "否"}
          {"，"}匹配当前配置候选：{diagnostics.candidateCount ?? 0}
        </div>
        <div style={{ color: "rgba(0,0,0,0.35)" }}>向量模型：{BRAND_MODEL_NAME}</div>
        {noCandidateButHasKnowledge && (
          <div style={{ color: "#d4380d" }}>
            门店有知识但当前 embedding 配置无匹配候选，可能是配置变更后未重算向量，请到「知识问答」重新保存该条 QA。
          </div>
        )}
        {candidates.length > 0 && (
          <ol style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {candidates.map((item, index) => (
              <li key={index} style={{ marginBottom: 2 }}>
                <span style={{ color: item.passed ? "#389e0d" : "#bfbfbf" }}>
                  {typeof item.cosineScore === "number" ? item.cosineScore.toFixed(3) : "-"}
                  {item.passed ? " ✓" : " ✗"}
                </span>{" "}
                <span style={{ color: "rgba(0,0,0,0.25)" }}>[{item.sourceType || "-"}]</span> {item.chunkText}
              </li>
            ))}
          </ol>
        )}
      </div>
    </details>
  );
}

export default function TestChatPanel({ shopId }: { shopId?: number }) {
  const { currentUser } = useAuth();
  // 仅管理员工作台（或总后台超级管理员）展示召回诊断/分类原始输出等调试信息；普通商户只看回复。
  const isAdminView =
    currentUser?.activePortal === "ADMIN" ||
    (!currentUser?.activePortal && (currentUser?.roles?.includes("super_admin") ?? false));
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [sessionId, setSessionId] = useState<string>(() => newSessionId());
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [logsOpen, setLogsOpen] = useState(false);
  const [logs, setLogs] = useState<TestChatLog[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);

  // 切换门店时清空当前测试对话并开启新会话，天然隔离不同门店/不同轮次的会话。
  useEffect(() => {
    setTurns([]);
    setInput("");
    setSessionId(newSessionId());
  }, [shopId]);

  const clearConversation = () => {
    setTurns([]);
    setSessionId(newSessionId());
  };

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" });
  }, [turns]);

  const history = useMemo(
    () => turns.map((turn) => ({ role: turn.role, content: turn.content })),
    [turns],
  );

  const send = async () => {
    const text = input.trim();
    if (!shopId || !text || sending) return;
    setInput("");
    setTurns((prev) => [...prev, { role: "user", content: text }]);
    setSending(true);
    try {
      const reply = await sendTestChat(shopId, sessionId, text, history);
      setTurns((prev) => [...prev, { role: "assistant", content: reply.reply, debug: reply }]);
    } catch {
      // 请求拦截器已提示错误信息（含每日上限），此处仅结束发送态。
    } finally {
      setSending(false);
    }
  };

  const openLogs = async () => {
    if (!shopId) return;
    setLogsOpen(true);
    setLogsLoading(true);
    try {
      setLogs(await listTestChatLogs(shopId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : "测试记录加载失败");
    } finally {
      setLogsLoading(false);
    }
  };

  const logColumns: ColumnsType<TestChatLog> = [
    { title: "时间", dataIndex: "createTime", width: 170 },
    { title: "操作人", dataIndex: "operatorName", width: 120, render: (v, r) => v || r.operatorUserId || "-" },
    { title: "测试输入", dataIndex: "userMessage" },
    { title: "AI回复", dataIndex: "aiReply" },
    {
      title: "模型",
      key: "brandModel",
      width: 110,
      render: () => <Tag color="purple">{BRAND_MODEL_NAME}</Tag>,
    },
    {
      title: "分类",
      dataIndex: "userMessageCategory",
      width: 90,
      render: (v) => categoryLabel(v),
    },
    {
      title: "知识",
      key: "rag",
      width: 90,
      render: (_, r) => (r.ragUsed ? <Tag color="green">{r.ragChunkCount ?? 0} 片段</Tag> : <Tag>未命中</Tag>),
    },
    {
      title: "安全",
      key: "safety",
      width: 120,
      render: (_, r) => (
        <Space size={4} wrap>
          {(r.rewriteCount ?? 0) > 0 && <Tag color="orange">改写{r.rewriteCount}</Tag>}
          {r.blocked && <Tag color="red">兜底</Tag>}
        </Space>
      ),
    },
  ];

  // 按会话ID分组：同一次对话（含清空前的多轮）聚成一条会话记录。
  const sessions = useMemo<TestChatSession[]>(() => {
    const map = new Map<string, TestChatSession>();
    for (const log of logs) {
      const key = log.sessionId || "(无会话ID)";
      let session = map.get(key);
      if (!session) {
        session = {
          sessionId: key,
          operatorName: log.operatorName,
          operatorUserId: log.operatorUserId,
          messageCount: 0,
          logs: [],
        };
        map.set(key, session);
      }
      session.logs.push(log);
      session.messageCount += 1;
    }
    const result = Array.from(map.values());
    for (const session of result) {
      session.logs.sort((a, b) => a.id - b.id); // 会话内按时间正序
      session.startTime = session.logs[0]?.createTime;
    }
    // 会话之间按最近活动倒序
    result.sort((a, b) => (b.logs[b.logs.length - 1]?.id ?? 0) - (a.logs[a.logs.length - 1]?.id ?? 0));
    return result;
  }, [logs]);

  const sessionColumns: ColumnsType<TestChatSession> = [
    {
      title: "会话ID",
      dataIndex: "sessionId",
      width: 240,
      render: (v: string) => (
        <Typography.Text code copyable={{ text: v }}>
          {v.length > 14 ? `${v.slice(0, 8)}…${v.slice(-4)}` : v}
        </Typography.Text>
      ),
    },
    { title: "操作人", width: 120, render: (_, r) => r.operatorName || r.operatorUserId || "-" },
    { title: "开始时间", dataIndex: "startTime", width: 170 },
    { title: "消息数", dataIndex: "messageCount", width: 90 },
  ];

  const renderSessionTurns = (session: TestChatSession) => (
    <div>
      {session.logs.map((log) => (
        <div key={log.id} style={{ marginBottom: 10 }}>
          <div style={{ marginBottom: 2 }}>
            <b>顾客：</b>
            {log.userMessage}
          </div>
          <div>
            <b>AI：</b>
            {log.aiReply}
            <Space size={4} wrap style={{ marginLeft: 8 }}>
              <Tag color="purple">{BRAND_MODEL_NAME}</Tag>
              <Tag>{categoryLabel(log.userMessageCategory)}</Tag>
              {log.ragUsed ? <Tag color="green">{log.ragChunkCount ?? 0} 片段</Tag> : <Tag>未命中</Tag>}
              {(log.rewriteCount ?? 0) > 0 && <Tag color="orange">改写{log.rewriteCount}</Tag>}
              {log.blocked && <Tag color="red">兜底</Tag>}
            </Space>
          </div>
        </div>
      ))}
    </div>
  );

  return (
    <>
      <Space style={{ marginBottom: 12 }}>
        {isAdminView && (
          <Button icon={<HistoryOutlined />} onClick={() => void openLogs()} disabled={!shopId}>
            查看测试记录
          </Button>
        )}
        <Button icon={<ClearOutlined />} onClick={clearConversation} disabled={turns.length === 0}>
          清空对话
        </Button>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          模拟顾客与所选门店 AI 客服对话，仅供测试；每个门店每天最多 100 条。
        </Typography.Text>
      </Space>

      <div
        ref={listRef}
        style={{
          height: 440,
          overflowY: "auto",
          padding: 16,
          background: "#f5f5f5",
          borderRadius: 8,
          marginBottom: 12,
        }}
      >
        {turns.length === 0 ? (
          <Empty description="输入消息开始测试" style={{ marginTop: 140 }} />
        ) : (
          turns.map((turn, index) => (
            <div
              key={index}
              style={{
                display: "flex",
                justifyContent: turn.role === "user" ? "flex-end" : "flex-start",
                marginBottom: 14,
              }}
            >
              <div style={{ maxWidth: "78%" }}>
                <div
                  style={{
                    padding: "8px 12px",
                    borderRadius: 8,
                    background: turn.role === "user" ? "#1677ff" : "#fff",
                    color: turn.role === "user" ? "#fff" : "rgba(0,0,0,0.88)",
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                    boxShadow: "0 1px 2px rgba(0,0,0,0.06)",
                  }}
                >
                  {turn.content}
                </div>
                {turn.role === "assistant" && (
                  <div style={{ marginTop: 4 }}>
                    <Tag color="purple" style={{ marginInlineEnd: 0 }}>模型：{BRAND_MODEL_NAME}</Tag>
                  </div>
                )}
                {turn.role === "assistant" && turn.debug && isAdminView && <DebugPanel debug={turn.debug} />}
              </div>
            </div>
          ))
        )}
        {sending && (
          <div style={{ display: "flex", justifyContent: "flex-start" }}>
            <div style={{ padding: "8px 12px", borderRadius: 8, background: "#fff", color: "rgba(0,0,0,0.45)" }}>
              AI 客服正在输入…
            </div>
          </div>
        )}
      </div>

      <Space.Compact style={{ width: "100%" }}>
        <Input.TextArea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder={shopId ? "以顾客身份输入消息，Enter 发送，Shift+Enter 换行" : "请先选择门店"}
          disabled={!shopId || sending}
          autoSize={{ minRows: 1, maxRows: 4 }}
          maxLength={2000}
          onPressEnter={(event) => {
            if (!event.shiftKey) {
              event.preventDefault();
              void send();
            }
          }}
        />
        <Tooltip title="发送">
          <Button
            type="primary"
            icon={<SendOutlined />}
            loading={sending}
            disabled={!shopId || !input.trim()}
            onClick={() => void send()}
          >
            发送
          </Button>
        </Tooltip>
      </Space.Compact>

      <Drawer title="测试记录（仅管理员可见）" open={logsOpen} width={960} onClose={() => setLogsOpen(false)}>
        <Tabs
          items={[
            {
              key: "messages",
              label: `消息列表（${logs.length}）`,
              children: (
                <Table<TestChatLog>
                  rowKey="id"
                  size="small"
                  loading={logsLoading}
                  dataSource={logs}
                  columns={logColumns}
                  pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 条` }}
                />
              ),
            },
            {
              key: "sessions",
              label: `会话列表（${sessions.length}）`,
              children: (
                <Table<TestChatSession>
                  rowKey="sessionId"
                  size="small"
                  loading={logsLoading}
                  dataSource={sessions}
                  columns={sessionColumns}
                  expandable={{ expandedRowRender: renderSessionTurns }}
                  pagination={{ pageSize: 20, showTotal: (total) => `共 ${total} 个会话` }}
                />
              ),
            },
          ]}
        />
      </Drawer>
    </>
  );
}
