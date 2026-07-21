import request from "@/utils/request";
import type {
  ImAutoReplyLog,
  ImAutoReplyLogQuery,
  ImConversation,
  ImConversationHistoryPage,
  ImConversationQuery,
  PageInfo,
} from "@/types";

const BASE_URL = "/system/im-auto-replies";

export async function getImAutoReplyLogs(params: ImAutoReplyLogQuery): Promise<PageInfo<ImAutoReplyLog>> {
  const response = await request.get<unknown, { data: PageInfo<ImAutoReplyLog> }>(`${BASE_URL}/messages`, { params });
  return response.data;
}

export async function getImConversations(params: ImConversationQuery): Promise<PageInfo<ImConversation>> {
  const response = await request.get<unknown, { data: PageInfo<ImConversation> }>(`${BASE_URL}/conversations`, { params });
  return response.data;
}

export async function getImConversationHistory(
  conversationId: number,
  params?: { cursorTime?: string; cursorId?: number; pageSize?: number },
): Promise<ImConversationHistoryPage> {
  const response = await request.get<unknown, { data: ImConversationHistoryPage }>(
    `${BASE_URL}/conversations/${conversationId}/messages`, { params },
  );
  return response.data;
}
