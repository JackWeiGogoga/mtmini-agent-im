# OpenAI 兼容模型服务接入

## 目标

后端新增了一层尽量轻量的 OpenAI 兼容调用能力，优先直接使用官方 `openai-java` SDK 的 DTO 和 builder，不额外设计完整项目内请求/响应模型。

## 当前实现原则

- 底层 SDK：`com.openai:openai-java`
- 当前接口范围：仅非流式 `chat/completions`
- 调用参数：支持显式传入 `baseUrl`、`apiKey`
- DTO 使用：业务侧可直接使用 SDK 的 `ChatCompletionCreateParams` 与 `ChatCompletion`
- 项目内额外能力：
  - 默认配置回退
  - `OpenAIClient` 复用
  - 异常统一转为项目内部异常
  - 日志脱敏

## 默认配置

配置前缀：`llm.openai-compatible`

- `default-base-url`
- `default-api-key`

示例：

```yaml
llm:
  openai-compatible:
    default-base-url: https://api.openai.com
    default-api-key: ${LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY:}
```

## 业务调用方式

推荐直接构造 SDK 参数：

```java
ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
        .model("gpt-4o-mini")
        .addSystemMessage("你是一个推广策略分析助手")
        .addUserMessage("请分析这家门店最近7日推广表现")
        .build();

ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(
        "https://api.openai.com",
        "your-api-key",
        params
);
```

如果已配置默认值，也可以直接：

```java
ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(params);
```

## 本地手动联调

仓库里已提供手动测试类：[OpenAiCompatibleManualTest.java](/Users/jackwei/data/outsourcing/takeout-master-ai/mtmini-agent/backend/src/test/java/com/gogoga/backend/service/OpenAiCompatibleManualTest.java)

如果你想直接查看“推广日志 Markdown”在真实模型上的输出效果，可以运行：

- [PromotionLogMarkdownManualTest.java](/Users/jackwei/data/outsourcing/takeout-master-ai/mtmini-agent/backend/src/test/java/com/gogoga/backend/service/promotion/PromotionLogMarkdownManualTest.java)

执行前请设置环境变量：

- `LLM_TEST_ENABLE_LIVE_API=true`
- `LLM_OPENAI_COMPATIBLE_DEFAULT_BASE_URL`
- `LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY`
- `LLM_TEST_MODEL`

腾讯云 OpenAI 兼容服务示例：

```bash
cd backend
LLM_TEST_ENABLE_LIVE_API=true \
LLM_OPENAI_COMPATIBLE_DEFAULT_BASE_URL='https://api.lkeap.cloud.tencent.com/v1' \
LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY='你的腾讯云 API Key' \
LLM_TEST_MODEL='你的模型名' \
/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -q -Dtest=OpenAiCompatibleManualTest test
```

日志 Markdown 手动联调：

```bash
LLM_TEST_ENABLE_LIVE_API=true \
LLM_OPENAI_COMPATIBLE_DEFAULT_BASE_URL='https://api.lkeap.cloud.tencent.com/v1' \
LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY='你的 API Key' \
LLM_TEST_MODEL='你的模型名' \
/Users/jackwei/data/apache-maven-3.9.14/bin/mvn -f backend/pom.xml -Dtest=PromotionLogMarkdownManualTest test
```

测试会输出多组典型日志样本的：

- `caseName`
- `fallback`
- `tokens`
- `failureSummary`
- `markdown`

注意：

- `baseUrl` 传根地址即可，不要自己再拼 `/chat/completions`
- `model` 以服务商控制台实际开通模型名为准
- 这里沿用 `application.yml` 里的默认配置环境变量，手动测试不再单独定义一套 `baseUrl/apiKey` 变量

## 为什么不额外包装 DTO

当前场景很简单：业务只需要构造参数并获取模型返回。官方 SDK 已经把请求与响应模型封装得足够完整，因此首版刻意避免重复造一套项目内 DTO，减少维护成本。
