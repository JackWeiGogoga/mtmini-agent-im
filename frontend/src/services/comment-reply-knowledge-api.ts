import request from "@/utils/request";
import type {
  AdminCommentReplyOfficialKnowledgeParam,
  CommentReplyKnowledgeChunk,
  CommentReplyKnowledgeDocument,
  CommentReplyKnowledgeDocumentPreview,
  CommentReplyKnowledgeItem,
  CommentReplyImageUploadResult,
  CommentReplyOfficialTemplateImportResult,
  CommentReplyQaKnowledgeParam,
  CommentReplySentiment,
  CommentReplyTestParam,
  CommentReplyTestResult,
  PageInfo,
} from "@/types";

const BASE_URL = "/system/comment-reply-knowledge/official";
const MERCHANT_BASE_URL = "/system/comment-reply-knowledge/merchant";

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

export async function listMerchantReplyQa(
  shopId: number,
): Promise<CommentReplyKnowledgeItem[]> {
  const response = await request.get<unknown, { data: CommentReplyKnowledgeItem[] }>(
    `${MERCHANT_BASE_URL}/items`,
    { params: { shopId } },
  );
  return response.data;
}

export async function createMerchantReplyQa(
  shopId: number,
  payload: CommentReplyQaKnowledgeParam,
): Promise<CommentReplyKnowledgeItem> {
  const response = await request.post<unknown, { data: CommentReplyKnowledgeItem }>(
    `${MERCHANT_BASE_URL}/items`,
    payload,
    { params: { shopId } },
  );
  return response.data;
}

export async function updateMerchantReplyQa(
  shopId: number,
  id: number,
  payload: CommentReplyQaKnowledgeParam,
): Promise<CommentReplyKnowledgeItem> {
  const response = await request.put<unknown, { data: CommentReplyKnowledgeItem }>(
    `${MERCHANT_BASE_URL}/items/${id}`,
    payload,
    { params: { shopId } },
  );
  return response.data;
}

export async function deleteMerchantReplyQa(shopId: number, id: number): Promise<void> {
  await request.delete(`${MERCHANT_BASE_URL}/items/${id}`, { params: { shopId } });
}

export async function clearMerchantReplyKnowledge(shopId: number): Promise<void> {
  await request.delete(`${MERCHANT_BASE_URL}/items/all`, { params: { shopId } });
}

export async function listMerchantReplyDocuments(
  shopId: number,
): Promise<CommentReplyKnowledgeDocument[]> {
  const response = await request.get<unknown, { data: CommentReplyKnowledgeDocument[] }>(
    `${MERCHANT_BASE_URL}/documents`,
    { params: { shopId } },
  );
  return response.data;
}

export async function uploadMerchantReplyDocument(
  shopId: number,
  file: File,
): Promise<CommentReplyKnowledgeDocument> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await request.post<unknown, { data: CommentReplyKnowledgeDocument }>(
    `${MERCHANT_BASE_URL}/documents/upload`,
    formData,
    { params: { shopId }, headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}

export async function previewMerchantReplyDocument(
  shopId: number,
  file: File,
): Promise<CommentReplyKnowledgeDocumentPreview> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await request.post<unknown, { data: CommentReplyKnowledgeDocumentPreview }>(
    `${MERCHANT_BASE_URL}/documents/preview`,
    formData,
    { params: { shopId }, headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}

export async function deleteMerchantReplyDocument(shopId: number, id: number): Promise<void> {
  await request.delete(`${MERCHANT_BASE_URL}/documents/${id}`, { params: { shopId } });
}

export async function listMerchantReplyDocumentChunks(
  shopId: number,
  documentId: number,
): Promise<CommentReplyKnowledgeChunk[]> {
  const response = await request.get<unknown, { data: CommentReplyKnowledgeChunk[] }>(
    `${MERCHANT_BASE_URL}/documents/${documentId}/chunks`,
    { params: { shopId } },
  );
  return response.data;
}

export async function updateMerchantReplyChunkSentiment(
  shopId: number,
  chunkId: number,
  sentiment: CommentReplySentiment,
): Promise<CommentReplyKnowledgeChunk> {
  const response = await request.put<unknown, { data: CommentReplyKnowledgeChunk }>(
    `${MERCHANT_BASE_URL}/documents/chunks/${chunkId}/sentiment`,
    { sentiment },
    { params: { shopId } },
  );
  return response.data;
}

export async function updateMerchantReplyChunkEnabled(
  shopId: number,
  chunkId: number,
  enabled: boolean,
): Promise<CommentReplyKnowledgeChunk> {
  const response = await request.put<unknown, { data: CommentReplyKnowledgeChunk }>(
    `${MERCHANT_BASE_URL}/documents/chunks/${chunkId}/enabled`,
    undefined,
    { params: { shopId, enabled } },
  );
  return response.data;
}

export async function deleteMerchantReplyChunk(shopId: number, chunkId: number): Promise<void> {
  await request.delete(`${MERCHANT_BASE_URL}/documents/chunks/${chunkId}`, {
    params: { shopId },
  });
}

export async function testMerchantReply(
  shopId: number,
  payload: CommentReplyTestParam,
): Promise<CommentReplyTestResult> {
  const response = await request.post<unknown, { data: CommentReplyTestResult }>(
    `${MERCHANT_BASE_URL}/reply-test`,
    payload,
    { params: { shopId }, timeout: 90000 },
  );
  return response.data;
}

export async function uploadMerchantReplyTestImages(
  shopId: number,
  files: File[],
): Promise<CommentReplyImageUploadResult[]> {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));
  const response = await request.post<unknown, { data: CommentReplyImageUploadResult[] }>(
    `${MERCHANT_BASE_URL}/reply-test/images/upload`,
    formData,
    { params: { shopId }, headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}
