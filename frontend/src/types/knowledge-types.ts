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

export interface TestChatMessage {
  role: "user" | "assistant";
  content: string;
}

export interface TestChatRagHit {
  chunkText: string;
  score?: number;
}

export interface TestChatDiagnosticsCandidate {
  chunkText: string;
  cosineScore?: number;
  sourceType?: string;
  category?: string;
  passed?: boolean;
}

export interface TestChatDiagnostics {
  threshold?: number;
  hasShopKnowledge?: boolean;
  candidateCount?: number;
  embeddingConfig?: string;
  topCandidates?: TestChatDiagnosticsCandidate[];
}

export interface TestChatSensitiveHit {
  categories?: string[];
  words?: string[];
  highestSeverity?: string;
  hitCount?: number;
}

export interface TestChatReply {
  reply: string;
  category?: string;
  ragUsed?: boolean;
  ragChunkCount?: number;
  ragMaxScore?: number;
  rewriteCount?: number;
  blocked?: boolean;
  promptTokens?: number;
  completionTokens?: number;
  ragHits?: TestChatRagHit[];
  diagnostics?: TestChatDiagnostics;
  sensitiveHit?: TestChatSensitiveHit;
}

export interface TestChatLog {
  id: number;
  sessionId?: string;
  operatorUserId?: string;
  operatorName?: string;
  userMessage?: string;
  aiReply?: string;
  userMessageCategory?: string;
  ragUsed?: boolean;
  ragChunkCount?: number;
  ragMaxScore?: number;
  rewriteCount?: number;
  blocked?: boolean;
  promptTokens?: number;
  completionTokens?: number;
  createTime?: string;
}
