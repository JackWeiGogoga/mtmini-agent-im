import request from "@/utils/request";
import type {
  AdminCommentReplyOfficialKnowledgeParam,
  CommentReplyKnowledgeItem,
  CommentReplyOfficialTemplateImportResult,
  CommentReplySentiment,
  PageInfo,
} from "@/types";

const BASE_URL = "/system/comment-reply-knowledge/official";

export async function listOfficialReplyKnowledge(
  sentiment?: CommentReplySentiment,
  pageNum = 1,
  pageSize = 10,
): Promise<PageInfo<CommentReplyKnowledgeItem>> {
  const response = await request.get<unknown, { data: PageInfo<CommentReplyKnowledgeItem> }>(
    BASE_URL,
    { params: { ...(sentiment ? { sentiment } : {}), pageNum, pageSize } },
  );
  return response.data;
}

export async function createOfficialReplyKnowledge(
  payload: AdminCommentReplyOfficialKnowledgeParam,
): Promise<CommentReplyKnowledgeItem> {
  const response = await request.post<unknown, { data: CommentReplyKnowledgeItem }>(
    BASE_URL,
    payload,
  );
  return response.data;
}

export async function updateOfficialReplyKnowledge(
  id: number,
  payload: AdminCommentReplyOfficialKnowledgeParam,
): Promise<CommentReplyKnowledgeItem> {
  const response = await request.put<unknown, { data: CommentReplyKnowledgeItem }>(
    `${BASE_URL}/${id}`,
    payload,
  );
  return response.data;
}

export async function deleteOfficialReplyKnowledge(id: number): Promise<void> {
  await request.delete(`${BASE_URL}/${id}`);
}

export async function importOfficialReplyKnowledge(file: File): Promise<CommentReplyOfficialTemplateImportResult> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await request.post<unknown, { data: CommentReplyOfficialTemplateImportResult }>(
    `${BASE_URL}/import`,
    formData,
    { headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}
