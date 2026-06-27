export interface SystemPromptConfig {
  id: number;
  promptKey: string;
  promptName: string;
  promptType: string;
  content: string;
  defaultResourcePath?: string;
  variablesJson?: string;
  modelHint?: string;
  enabled: boolean;
  version: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SystemPromptUpdateParam {
  content: string;
  enabled: boolean;
  remark?: string;
}
