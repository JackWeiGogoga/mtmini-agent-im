/**
 * LLM 提供商配置
 * 定义标准提供商的静态信息（ID、名称、默认Base URL、图标等）
 */

// 标准提供商 ID 类型
export type StandardProviderId =
  | "deepseek"
  | "openai"
  | "anthropic"
  | "openrouter"
  | "groq"
  | "mistral"
  | "gemini"
  | "perplexity"
  | "deepinfra"
  | "together"
  | "xai";

// 标准提供商显示名称
export const STANDARD_PROVIDER_LABELS: Record<StandardProviderId, string> = {
  deepseek: "DeepSeek",
  openai: "OpenAI",
  anthropic: "Claude (Anthropic)",
  openrouter: "OpenRouter",
  groq: "Groq",
  mistral: "Mistral AI",
  gemini: "Google Gemini",
  perplexity: "Perplexity AI",
  deepinfra: "DeepInfra",
  together: "Together AI",
  xai: "xAI (Grok)",
};

// 图标 Key 映射（与 LLMIcons 的 IconMap 对应）
export const STANDARD_PROVIDER_ICON_KEYS: Record<StandardProviderId, string> = {
  deepseek: "DeepSeek",
  openai: "OpenAI",
  anthropic: "Anthropic",
  openrouter: "OpenRouter",
  groq: "Groq",
  mistral: "Mistral AI",
  gemini: "Google Gemini",
  perplexity: "Perplexity AI",
  deepinfra: "DeepInfra",
  together: "Together AI",
  xai: "xAI",
};

// 提供商品牌颜色
export const STANDARD_PROVIDER_COLORS: Record<StandardProviderId, string> = {
  deepseek: "#4D6BFE",
  openai: "#10a37f",
  anthropic: "#d4a27f",
  openrouter: "#111827",
  groq: "#f55036",
  mistral: "#ff7000",
  gemini: "#8e75b2",
  perplexity: "#20808d",
  deepinfra: "#475569",
  together: "#0ea5e9",
  xai: "#000000",
};

// 标准提供商定义
export interface StandardProviderDefinition {
  id: StandardProviderId;
  name: string;
  hint: string;
  baseUrl: string;
  color: string;
  initial: string;
  iconKey: string;
  defaultModel: string;
}

// 所有标准提供商列表
export const STANDARD_PROVIDERS: StandardProviderDefinition[] = [
  {
    id: "deepseek",
    name: STANDARD_PROVIDER_LABELS.deepseek,
    hint: "DeepSeek-V3 / DeepSeek-R1",
    baseUrl: "https://api.deepseek.com/v1",
    color: STANDARD_PROVIDER_COLORS.deepseek,
    initial: "D",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.deepseek,
    defaultModel: "deepseek-chat",
  },
  {
    id: "openai",
    name: STANDARD_PROVIDER_LABELS.openai,
    hint: "GPT-4o / GPT-4.1 / o1",
    baseUrl: "https://api.openai.com/v1",
    color: STANDARD_PROVIDER_COLORS.openai,
    initial: "O",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.openai,
    defaultModel: "gpt-4o",
  },
  {
    id: "anthropic",
    name: STANDARD_PROVIDER_LABELS.anthropic,
    hint: "Claude 3.5 / Claude 3 Opus",
    baseUrl: "https://api.anthropic.com",
    color: STANDARD_PROVIDER_COLORS.anthropic,
    initial: "C",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.anthropic,
    defaultModel: "claude-3-5-sonnet-20241022",
  },
  {
    id: "openrouter",
    name: STANDARD_PROVIDER_LABELS.openrouter,
    hint: "多模型聚合 / 路由选择",
    baseUrl: "https://openrouter.ai/api/v1",
    color: STANDARD_PROVIDER_COLORS.openrouter,
    initial: "R",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.openrouter,
    defaultModel: "openai/gpt-4o",
  },
  {
    id: "groq",
    name: STANDARD_PROVIDER_LABELS.groq,
    hint: "高速推理 / Llama 3.3",
    baseUrl: "https://api.groq.com/openai/v1",
    color: STANDARD_PROVIDER_COLORS.groq,
    initial: "G",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.groq,
    defaultModel: "llama-3.3-70b-versatile",
  },
  {
    id: "mistral",
    name: STANDARD_PROVIDER_LABELS.mistral,
    hint: "Mistral Large / Codestral",
    baseUrl: "https://api.mistral.ai/v1",
    color: STANDARD_PROVIDER_COLORS.mistral,
    initial: "M",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.mistral,
    defaultModel: "mistral-large-latest",
  },
  {
    id: "gemini",
    name: STANDARD_PROVIDER_LABELS.gemini,
    hint: "Gemini 2.0 / Gemini 1.5 Pro",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta",
    color: STANDARD_PROVIDER_COLORS.gemini,
    initial: "G",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.gemini,
    defaultModel: "gemini-2.0-flash",
  },
  {
    id: "perplexity",
    name: STANDARD_PROVIDER_LABELS.perplexity,
    hint: "Sonar / 联网搜索",
    baseUrl: "https://api.perplexity.ai",
    color: STANDARD_PROVIDER_COLORS.perplexity,
    initial: "P",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.perplexity,
    defaultModel: "sonar-pro",
  },
  {
    id: "deepinfra",
    name: STANDARD_PROVIDER_LABELS.deepinfra,
    hint: "开源模型托管",
    baseUrl: "https://api.deepinfra.com/v1/openai",
    color: STANDARD_PROVIDER_COLORS.deepinfra,
    initial: "D",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.deepinfra,
    defaultModel: "meta-llama/Llama-3.3-70B-Instruct",
  },
  {
    id: "together",
    name: STANDARD_PROVIDER_LABELS.together,
    hint: "开源模型 / OpenAI 兼容",
    baseUrl: "https://api.together.xyz/v1",
    color: STANDARD_PROVIDER_COLORS.together,
    initial: "T",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.together,
    defaultModel: "meta-llama/Llama-3.3-70B-Instruct-Turbo",
  },
  {
    id: "xai",
    name: STANDARD_PROVIDER_LABELS.xai,
    hint: "Grok-2 / Grok-3",
    baseUrl: "https://api.x.ai/v1",
    color: STANDARD_PROVIDER_COLORS.xai,
    initial: "X",
    iconKey: STANDARD_PROVIDER_ICON_KEYS.xai,
    defaultModel: "grok-2-latest",
  },
];

// 根据 ID 获取提供商定义
export function getProviderDefinition(
  id: StandardProviderId,
): StandardProviderDefinition | undefined {
  return STANDARD_PROVIDERS.find((p) => p.id === id);
}
