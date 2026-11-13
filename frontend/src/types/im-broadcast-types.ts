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

/** NONE=未配置或已删除；ACTIVE=生效中；ONCE_COMPLETED=「一次」已执行完成 */
export type ImBroadcastSettingStatus = "NONE" | "ACTIVE" | "ONCE_COMPLETED";

/** 门店定时群发配置，H5 与总控台共用。 */
export interface ImBroadcastSetting {
  shopId?: number;
  /** 未配置（含「一次」触发后失效）时为 false，供 H5 决定按钮形态 */
  configured?: boolean;
  enabled?: boolean;
  /** 运营视角的细分状态，用于区分「从没配过」和「刚跑完一次性群发」 */
  status?: ImBroadcastSettingStatus;
  statusName?: string;
  /** 最后一次触发日期，ONCE_COMPLETED 时即执行日期 */
  lastTriggerDate?: string;
  /** 1=周一 … 7=周日 */
  weekDays?: number[];
  repeatMode?: ImBroadcastRepeatMode;
  /** HH:mm:ss */
  sendTime?: string;
  content?: string;
  totalSentCount?: number;
}

export interface ImBroadcastSettingUpdateParam {
  weekDays: number[];
  repeatMode: ImBroadcastRepeatMode;
  sendTime: string;
  content: string;
}

export type ImBroadcastChangeType = "SAVE" | "DELETE";
export type ImBroadcastChangeSource = "H5" | "ADMIN" | "SYSTEM";

/** 群发配置变更历史，含已被删除的版本。 */
export interface ImBroadcastSettingHistory {
  id: number;
  shopId: number;
  versionNo: number;
  changeType: ImBroadcastChangeType;
  changeTypeName?: string;
  changeSource: ImBroadcastChangeSource;
  enabled?: boolean;
  /** DELETE 记录里是被删掉那一版的值 */
  weekDays?: number[];
  repeatMode?: ImBroadcastRepeatMode;
  sendTime?: string;
  content?: string;
  operatorUserId?: string;
  operatorName?: string;
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
