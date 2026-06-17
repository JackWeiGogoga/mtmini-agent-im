import type { PageParam } from "./common-types";

export interface PricingAnalysisQueryParam extends PageParam {
  shopId?: number;
  poiId?: string;
  shopName?: string;
  keyword?: string;
  executeStatus?: string;
  decisionAction?: string;
  decisionReason?: string;
  logMarkdownStatus?: string;
  slotStartTime?: string;
  slotEndTime?: string;
}

export interface PricingAnalysisRow {
  id: number;
  shopId?: number;
  poiId?: string;
  epoiId?: string;
  shopName?: string;
  bizDate?: string;
  slotTime?: string;
  windowKey?: string;
  windowStartAt?: string;
  windowEndAt?: string;
  previousBidAmount?: number;
  nextBidAmount?: number;
  decisionAction?: string;
  decisionReason?: string;
  decisionReasonText?: string;
  totalCost?: number;
  totalIncome?: number;
  totalCostRatio?: number;
  costDelta?: number;
  costCounterValue?: number;
  executeStatus?: string;
  logMarkdownStatus?: string;
  logTaskId?: number;
  errorMsg?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PricingAnalysisDetail {
  recordSummary?: Record<string, unknown>;
  shopContext?: Record<string, unknown>;
  strategyContext?: {
    executionStrategyConfigJson?: string;
    executionStrategyConfigHash?: string;
    executionStrategySnapshotMissing?: boolean;
    currentStrategyConfigJson?: string;
    currentStrategyConfigHash?: string;
    currentPromotionEnabled?: boolean;
    currentStrategyConfigured?: boolean;
    sameAsCurrent?: boolean;
    histories?: Record<string, unknown>[];
  };
  metricsContext?: Record<string, unknown>;
  peakProfileContext?: Record<string, unknown>;
  statusCheckContext?: Record<string, unknown>;
  bidConstraintContext?: Record<string, unknown>;
  decisionContext?: Record<string, unknown>;
  executionContext?: Record<string, unknown>;
  rawPayloads?: Record<string, string | undefined>;
}
