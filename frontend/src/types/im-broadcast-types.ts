import type { PageParam } from "./common-types";

export type ImBroadcastTaskStatus =
  | "PENDING" | "SENDING" | "SUCCESS" | "PARTIAL" | "FAILED" | "SKIPPED";

export type ImBroadcastRecordStatus = "PENDING" | "SENT" | "FAILED";

export type ImBroadcastRepeatMode = "EVERY" | "ONCE";

export interface ImBroadcastTaskQuery extends PageParam {
  shopId?: number;
  shopKeyword?: string;
  status?: ImBroadcastTaskStatus;
  repeatMode?: ImBroadcastRepeatMode;
  contentKeyword?: string;
  startTime?: string;
  endTime?: string;
}

export interface ImBroadcastTask {
  taskId: number;
  shopId: number;
  shopName?: string;
  poiId?: string;
  epoiId?: string;
  triggerDate?: string;
  planTime?: string;
  content?: string;
  weekDays?: string;
  repeatMode?: ImBroadcastRepeatMode;
  status: ImBroadcastTaskStatus;
  targetCount?: number;
  truncatedCount?: number;
  shardCount?: number;
  successCount?: number;
  failCount?: number;
  skipReason?: string;
  plannedAt?: string;
  startedAt?: string;
  finishedAt?: string;
  createTime?: string;
}

export interface ImBroadcastRecord {
  recordId: number;
  taskId: number;
  shopId: number;
  conversationId: number;
  openUserId: number;
  shardNo?: number;
  outboundMessageId?: number;
  status: ImBroadcastRecordStatus;
  errorCode?: string;
  errorSummary?: string;
  sentAt?: string;
  createTime?: string;
}
