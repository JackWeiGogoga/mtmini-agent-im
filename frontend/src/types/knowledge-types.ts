export interface KnowledgeItem {
  id: number;
  shopId: number;
  title: string;
  question?: string;
  answer: string;
  enabled: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeDocument {
  id: number;
  shopId: number;
  fileName: string;
  fileType: string;
  fileSize?: number;
  parseStatus: "PENDING" | "PARSING" | "SUCCESS" | "FAILED" | string;
  parseError?: string;
  chunkCount?: number;
  enabled: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeChunk {
  id: number;
  shopId: number;
  documentId: number;
  sourceType: string;
  chunkText: string;
  enabled: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface KnowledgeDocumentPreview {
  fileName: string;
  fileType: string;
  fileSize?: number;
  chunkCount: number;
  chunks: Array<{ index: number; chunkText: string }>;
}

export interface KnowledgeQaParam {
  title: string;
  question: string;
  answer: string;
  enabled: boolean;
}
