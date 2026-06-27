import request from "@/utils/request";
import type { PageInfo, SystemPromptConfig, SystemPromptUpdateParam } from "@/types";

const BASE_URL = "/system/prompts";

export async function listSystemPrompts(params: {
  promptType?: string;
  enabled?: boolean;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}): Promise<PageInfo<SystemPromptConfig>> {
  const response = await request.get<unknown, { data: PageInfo<SystemPromptConfig> }>(
    BASE_URL,
    { params },
  );
  return response.data;
}

export async function getSystemPrompt(id: number): Promise<SystemPromptConfig> {
  const response = await request.get<unknown, { data: SystemPromptConfig }>(
    `${BASE_URL}/${id}`,
  );
  return response.data;
}

export async function updateSystemPrompt(
  id: number,
  payload: SystemPromptUpdateParam,
): Promise<SystemPromptConfig> {
  const response = await request.put<unknown, { data: SystemPromptConfig }>(
    `${BASE_URL}/${id}`,
    payload,
  );
  return response.data;
}

export async function updateSystemPromptEnabled(
  id: number,
  enabled: boolean,
): Promise<SystemPromptConfig> {
  const response = await request.patch<unknown, { data: SystemPromptConfig }>(
    `${BASE_URL}/${id}/enabled`,
    undefined,
    { params: { enabled } },
  );
  return response.data;
}

export async function restoreSystemPromptDefault(id: number): Promise<SystemPromptConfig> {
  const response = await request.post<unknown, { data: SystemPromptConfig }>(
    `${BASE_URL}/${id}/restore-default`,
  );
  return response.data;
}
