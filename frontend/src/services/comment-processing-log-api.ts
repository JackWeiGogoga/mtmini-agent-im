import request from "@/utils/request";
import type {
  CommentProcessingLogQuery,
  CommentProcessingLogRecord,
  PageInfo,
} from "@/types";

const BASE_URL = "/system/comment-processing-logs";

export async function listCommentProcessingLogs(
  params: CommentProcessingLogQuery,
): Promise<PageInfo<CommentProcessingLogRecord>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<CommentProcessingLogRecord> }
  >(BASE_URL, { params });
  return response.data;
}

export async function getCommentProcessingLog(
  id: number,
): Promise<CommentProcessingLogRecord> {
  const response = await request.get<
    unknown,
    { data: CommentProcessingLogRecord }
  >(`${BASE_URL}/${id}`);
  return response.data;
}

export type CommentProcessingRetryType = "auto-reply" | "complaint" | "im-follow-up";

export async function retryCommentProcessing(
  id: number,
  retryType: CommentProcessingRetryType,
): Promise<void> {
  await request.post(`${BASE_URL}/${id}/retry/${retryType}`);
}
