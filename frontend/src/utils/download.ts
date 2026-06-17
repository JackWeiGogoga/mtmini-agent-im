import axios, { type AxiosError } from "axios";
import { message } from "@/components/AppMessageContext";
import { API_PREFIX } from "@/utils/env";

const downloadRequest = axios.create({
  baseURL: `${API_PREFIX}/api`,
  timeout: 60000,
  withCredentials: true,
});

export async function downloadXlsx(
  url: string,
  params: object,
  fallbackFilename: string,
): Promise<void> {
  try {
    const response = await downloadRequest.get<Blob>(url, {
      params,
      responseType: "blob",
    });
    const filename =
      parseFilename(response.headers["content-disposition"]) || fallbackFilename;
    saveBlob(response.data, filename);
  } catch (error) {
    const errorMessage = await resolveDownloadErrorMessage(error);
    message.error(errorMessage);
    throw error;
  }
}

function saveBlob(blob: Blob, filename: string) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(objectUrl);
}

function parseFilename(contentDisposition?: string): string | undefined {
  if (!contentDisposition) {
    return undefined;
  }
  const encodedMatch = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition);
  if (encodedMatch?.[1]) {
    return decodeURIComponent(encodedMatch[1]);
  }
  const plainMatch = /filename="?([^";]+)"?/i.exec(contentDisposition);
  return plainMatch?.[1];
}

async function resolveDownloadErrorMessage(error: unknown): Promise<string> {
  const axiosError = error as AxiosError<Blob>;
  const data = axiosError.response?.data;
  if (data instanceof Blob) {
    const text = await data.text();
    try {
      const parsed = JSON.parse(text) as { msg?: string; message?: string };
      return parsed.msg || parsed.message || "导出失败";
    } catch {
      return text || "导出失败";
    }
  }
  return axiosError.message || "导出失败";
}
