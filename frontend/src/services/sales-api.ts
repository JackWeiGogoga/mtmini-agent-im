import request from "@/utils/request";
import { downloadXlsx } from "@/utils/download";
import type {
  SalesCommissionPage,
  SalesCommissionQueryParam,
  SalesCommissionRatioUpdateParam,
  SalesCommissionSettlementUpdateParam,
  SalesRedemptionCodeGenerateParam,
  SalesRedemptionCodePage,
  SalesRedemptionCodeQueryParam,
  SalesRedemptionCodeRow,
  SalesSelfConfig,
} from "@/types";

const SALES_REDEMPTION_BASE_URL = "/system/sales/redemption-codes";
const SALES_SELF_CONFIG_BASE_URL = "/system/sales/self-config";
const SALES_COMMISSION_BASE_URL = "/system/sales/commissions";

export async function getSalesRedemptionCodes(
  params: SalesRedemptionCodeQueryParam,
): Promise<SalesRedemptionCodePage> {
  const response = await request.get<unknown, { data: SalesRedemptionCodePage }>(
    SALES_REDEMPTION_BASE_URL,
    { params },
  );
  return response.data;
}

export async function exportSalesRedemptionCodes(
  params: SalesRedemptionCodeQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${SALES_REDEMPTION_BASE_URL}/export`,
    params,
    "销售兑换码.xlsx",
  );
}

export async function generateSalesRedemptionCode(
  params: SalesRedemptionCodeGenerateParam,
): Promise<SalesRedemptionCodeRow> {
  const response = await request.post<unknown, { data: SalesRedemptionCodeRow }>(
    SALES_REDEMPTION_BASE_URL,
    params,
  );
  return response.data;
}

export async function clearSalesReceiptCode(redemptionCodeId: number): Promise<void> {
  await request.delete(`${SALES_REDEMPTION_BASE_URL}/${redemptionCodeId}/receipt-code`);
}

export async function getSalesSelfConfig(): Promise<SalesSelfConfig> {
  const response = await request.get<unknown, { data: SalesSelfConfig }>(
    SALES_SELF_CONFIG_BASE_URL,
  );
  return response.data;
}

export async function uploadSalesReceiptCode(file: File): Promise<SalesSelfConfig> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await request.post<unknown, { data: SalesSelfConfig }>(
    `${SALES_SELF_CONFIG_BASE_URL}/receipt-code`,
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    },
  );
  return response.data;
}

export async function getSalesCommissionRows(
  params: SalesCommissionQueryParam,
): Promise<SalesCommissionPage> {
  const response = await request.get<unknown, { data: SalesCommissionPage }>(
    SALES_COMMISSION_BASE_URL,
    { params },
  );
  return response.data;
}

export async function exportSalesCommissionRows(
  params: SalesCommissionQueryParam,
): Promise<void> {
  await downloadXlsx(
    `${SALES_COMMISSION_BASE_URL}/export`,
    params,
    "销售分佣.xlsx",
  );
}

export async function updateSalesCommissionRatio(
  id: number,
  params: SalesCommissionRatioUpdateParam,
): Promise<void> {
  await request.put(`${SALES_COMMISSION_BASE_URL}/${id}/ratio`, params);
}

export async function updateSalesCommissionSettlementStatus(
  id: number,
  params: SalesCommissionSettlementUpdateParam,
): Promise<void> {
  await request.put(`${SALES_COMMISSION_BASE_URL}/${id}/settlement-status`, params);
}
