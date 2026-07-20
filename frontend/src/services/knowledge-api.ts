import request from "@/utils/request";
import type {
  KnowledgeChunk,
  KnowledgeDocument,
  KnowledgeDocumentPreview,
  KnowledgeItem,
  KnowledgeQaParam,
  TestChatLog,
  TestChatMessage,
  TestChatReply,
} from "@/types";

const BASE_URL = "/system/knowledge/merchant";

export async function listKnowledgeQa(shopId: number): Promise<KnowledgeItem[]> {
  const response = await request.get<unknown, { data: KnowledgeItem[] }>(`${BASE_URL}/items`, {
    params: { shopId },
  });
  return response.data;
}

export async function createKnowledgeQa(shopId: number, payload: KnowledgeQaParam): Promise<KnowledgeItem> {
  const response = await request.post<unknown, { data: KnowledgeItem }>(`${BASE_URL}/items`, payload, {
    params: { shopId },
  });
  return response.data;
}

export async function updateKnowledgeQa(shopId: number, id: number, payload: KnowledgeQaParam): Promise<KnowledgeItem> {
  const response = await request.put<unknown, { data: KnowledgeItem }>(`${BASE_URL}/items/${id}`, payload, {
    params: { shopId },
  });
  return response.data;
}

export async function deleteKnowledgeQa(shopId: number, id: number): Promise<void> {
  await request.delete(`${BASE_URL}/items/${id}`, { params: { shopId } });
}

export async function clearKnowledge(shopId: number): Promise<void> {
  await request.delete(`${BASE_URL}/items/all`, { params: { shopId } });
}

export async function listKnowledgeDocuments(shopId: number): Promise<KnowledgeDocument[]> {
  const response = await request.get<unknown, { data: KnowledgeDocument[] }>(`${BASE_URL}/documents`, {
    params: { shopId },
  });
  return response.data;
}

export async function previewKnowledgeDocument(shopId: number, file: File): Promise<KnowledgeDocumentPreview> {
  const data = new FormData();
  data.append("file", file);
  const response = await request.post<unknown, { data: KnowledgeDocumentPreview }>(
    `${BASE_URL}/documents/preview`,
    data,
    { params: { shopId }, headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}

export async function uploadKnowledgeDocument(shopId: number, file: File): Promise<KnowledgeDocument> {
  const data = new FormData();
  data.append("file", file);
  const response = await request.post<unknown, { data: KnowledgeDocument }>(
    `${BASE_URL}/documents/upload`,
    data,
    { params: { shopId }, headers: { "Content-Type": "multipart/form-data" } },
  );
  return response.data;
}

export async function deleteKnowledgeDocument(shopId: number, id: number): Promise<void> {
  await request.delete(`${BASE_URL}/documents/${id}`, { params: { shopId } });
}

export async function listKnowledgeChunks(shopId: number, documentId: number): Promise<KnowledgeChunk[]> {
  const response = await request.get<unknown, { data: KnowledgeChunk[] }>(
    `${BASE_URL}/documents/${documentId}/chunks`,
    { params: { shopId } },
  );
  return response.data;
}

export async function updateKnowledgeChunkEnabled(
  shopId: number,
  chunkId: number,
  enabled: boolean,
): Promise<KnowledgeChunk> {
  const response = await request.put<unknown, { data: KnowledgeChunk }>(
    `${BASE_URL}/documents/chunks/${chunkId}/enabled`,
    undefined,
    { params: { shopId, enabled } },
  );
  return response.data;
}

export async function deleteKnowledgeChunk(shopId: number, chunkId: number): Promise<void> {
  await request.delete(`${BASE_URL}/documents/chunks/${chunkId}`, { params: { shopId } });
}

export async function sendTestChat(
  shopId: number,
  sessionId: string,
  message: string,
  history: TestChatMessage[],
): Promise<TestChatReply> {
  const response = await request.post<unknown, { data: TestChatReply }>(
    `${BASE_URL}/test-chat`,
    { sessionId, message, history },
    { params: { shopId } },
  );
  return response.data;
}

export async function listTestChatLogs(shopId: number): Promise<TestChatLog[]> {
  const response = await request.get<unknown, { data: TestChatLog[] }>(`${BASE_URL}/test-chat/logs`, {
    params: { shopId },
  });
  return response.data;
}
