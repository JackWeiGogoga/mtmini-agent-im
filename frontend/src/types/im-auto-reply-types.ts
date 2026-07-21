import type { PageParam } from "./common-types";

export type ImAutoReplyStatus =
  | "PENDING" | "GENERATING" | "GENERATED" | "BILLED" | "SENDING"
  | "SENT" | "SUPERSEDED" | "SKIPPED" | "DEAD";

export interface ImAutoReplyLogQuery extends PageParam {
  shopId?: number;
  shopKeyword?: string;
  openUserId?: number;
  orderId?: number;
  status?: ImAutoReplyStatus;
  ragUsed?: boolean;
  startTime?: string;
  endTime?: string;
}

export interface ImAutoReplyLog {
  autoReplyId: number;
  shopId: number;
  shopName?: string;
  poiId?: string;
  epoiId?: string;
  conversationId: number;
  sessionId: number;
  openUserId?: number;
  orderId?: number;
  userMessageId: number;
  userContent?: string;
  /** 后端从顾客消息里识别出的手机号（已去分隔符） */
  userPhones?: string[];
  userMessageTime?: string;
  aiMessageId?: number;
  aiContent?: string;
  aiMessageTime?: string;
  deliveryStatus?: string;
  status: ImAutoReplyStatus;
  skipReason?: string;
  ragUsed?: boolean;
  ragChunkCount?: number;
  ragMaxScore?: number;
  modelName?: string;
  promptTokens?: number;
  completionTokens?: number;
  powerAmount?: number;
  rewriteCount?: number;
  sentAt?: string;
  lastErrorCode?: string;
  createTime?: string;
}

export interface ImConversationQuery extends PageParam {
  shopId?: number;
  shopKeyword?: string;
  openUserId?: number;
  orderId?: number;
  startTime?: string;
  endTime?: string;
}

export interface ImConversation {
  conversationId: number;
  shopId: number;
  shopName?: string;
  poiId?: string;
  epoiId?: string;
  openUserId: number;
  messageCount: number;
  userMessageCount: number;
  aiMessageCount: number;
  merchantMessageCount: number;
  sessionCount: number;
  lastMessageRole?: string;
  lastMessageContent?: string;
  firstMessageAt?: string;
  lastMessageAt?: string;
  lastAiReplyAt?: string;
}

export interface ImConversationMessage {
  id: number;
  conversationId: number;
  sessionId: number;
  sessionNo?: number;
  sessionBoundaryReason?: string;
  sessionStartedAt?: string;
  senderRole: "USER" | "MERCHANT" | "AI";
  recordSource?: string;
  content?: string;
  deliveryStatus?: string;
  autoReplyStatus?: string;
  replyToMessageId?: number;
  orderId?: number;
  eventTime: string;
}

export interface ImConversationHistoryPage {
  conversationId: number;
  list: ImConversationMessage[];
  hasMore: boolean;
  nextCursorTime?: string;
  nextCursorId?: number;
}
