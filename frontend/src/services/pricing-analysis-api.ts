import request from "@/utils/request";
import type {
  PageInfo,
  PricingAnalysisDetail,
  PricingAnalysisQueryParam,
  PricingAnalysisRow,
} from "@/types";

const PRICING_ANALYSIS_BASE_URL = "/system/pricing-analysis";

export async function getPricingAnalysisRows(
  params: PricingAnalysisQueryParam,
): Promise<PageInfo<PricingAnalysisRow>> {
  const response = await request.get<
    unknown,
    { data: PageInfo<PricingAnalysisRow> }
  >(PRICING_ANALYSIS_BASE_URL, { params });
  return response.data;
}

export async function getPricingAnalysisDetail(
  recordId: number,
): Promise<PricingAnalysisDetail> {
  const response = await request.get<
    unknown,
    { data: PricingAnalysisDetail }
  >(`${PRICING_ANALYSIS_BASE_URL}/${recordId}`);
  return response.data;
}
