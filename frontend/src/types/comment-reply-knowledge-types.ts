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

export interface AdminCommentReplyOfficialKnowledgeParam {
  title: string;
  sentiment: Exclude<CommentReplySentiment, "UNKNOWN">;
  answer: string;
  enabled: boolean;
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
