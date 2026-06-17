import AlephAlpha from "./assets/AlephAlpha";
import Anthropic from "./assets/Anthropic";
import AnyScale from "./assets/AnyScale";
import DeepInfra from "./assets/DeepInfra";
import DeepSeek from "./assets/DeepSeek";
import Gemini from "./assets/Gemini";
import Groq from "./assets/Groq";
import Mistral from "./assets/Mistral";
import OpenAi from "./assets/OpenAi";
import OpenRouter from "./assets/OpenRouter";
import Perplexity from "./assets/Perplexity";
import Together from "./assets/Together";
import XAI from "./assets/XAI";

const IconMap: Record<string, React.FC<{ className?: string }>> = {
  OpenAI: OpenAi,
  OpenRouter: OpenRouter,
  Anthropic: Anthropic,
  DeepSeek: DeepSeek,
  "Google Gemini": Gemini,
  Groq: Groq,
  "Mistral AI": Mistral,
  Cohere: AlephAlpha, // Using AlephAlpha as placeholder
  Anyscale: AnyScale,
  "Perplexity AI": Perplexity,
  DeepInfra: DeepInfra,
  "Together AI": Together,
  "Aleph Alpha": AlephAlpha,
  xAI: XAI,
};

export default IconMap;
