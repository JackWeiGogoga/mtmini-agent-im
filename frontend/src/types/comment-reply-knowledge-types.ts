export type CommentReplySentiment = "GOOD" | "NEUTRAL" | "BAD" | "UNKNOWN";

export interface CommentReplyKnowledgeItem {
  id: number;
  shopId?: number;
  scope: string;
  sourceType: string;
  sentiment: CommentReplySentiment;
  title: string;
  question?: string;
  answer: string;
  enabled: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface CommentReplyKnowledgeDocument {
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

export interface CommentReplyKnowledgeChunk {
  id: number;
  shopId: number;
  documentId: number;
  sourceType: string;
  sentiment: CommentReplySentiment;
  chunkText: string;
  enabled: boolean;
  createTime?: string;
  updateTime?: string;
}

export interface CommentReplyKnowledgeDocumentPreviewChunk {
  index: number;
  sentiment: CommentReplySentiment;
  chunkText: string;
}

export interface CommentReplyKnowledgeDocumentPreview {
  fileName: string;
  fileType: string;
  fileSize?: number;
  chunkCount: number;
  chunks: CommentReplyKnowledgeDocumentPreviewChunk[];
}

export interface AdminCommentReplyOfficialKnowledgeParam {
  title: string;
  sentiment: Exclude<CommentReplySentiment, "UNKNOWN">;
  answer: string;
  enabled: boolean;
}

export interface CommentReplyQaKnowledgeParam {
  title: string;
  sentiment: Exclude<CommentReplySentiment, "UNKNOWN">;
  question: string;
  answer: string;
  enabled: boolean;
}

export interface CommentReplyTestParam {
  productScore: number;
  packingScore: number;
  orderCommentScore: number;
  deliveryCommentScore: number;
  commentLabels?: string;
  overDeliveryTimeDesc?: string;
  commentContent: string;
  addComment?: string;
  commentPictures?: string[];
  temporaryImageObjectKeys?: string[];
  imageSummaryText?: string;
}

export interface CommentReplyTestResult {
  shouldReply: boolean;
  skipReason?: string;
  replyContent?: string;
  replySource?: string;
  sentiment?: CommentReplySentiment;
  generationId?: number;
  knowledgeHitJson?: string;
  model?: string;
  powerAmount?: number;
  imageSummaryStatus?: string;
  imageSummaryText?: string;
  imageSummaryJson?: string;
  imageSummaryErrorMessage?: string;
  imagePromptTokens?: number;
  imageCompletionTokens?: number;
  imageTotalTokens?: number;
  imagePowerAmount?: number;
  replyDurationMs?: number;
  analysisText?: string;
  analysisModel?: string;
  analysisPromptTokens?: number;
  analysisCompletionTokens?: number;
  analysisTotalTokens?: number;
  analysisPowerAmount?: number;
  analysisDurationMs?: number;
  analysisErrorMessage?: string;
  totalDurationMs?: number;
}

export interface CommentReplyImageUploadResult {
  fileName: string;
  objectKey: string;
  url: string;
}

export interface CommentReplyOfficialTemplateImportResult {
  totalCount: number;
  createdCount: number;
  updatedCount: number;
  duplicateCount: number;
  skippedCount: number;
  goodCount: number;
  neutralCount: number;
  badCount: number;
}
