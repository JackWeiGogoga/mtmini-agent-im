package com.gogoga.backend.service;

import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容接口手动联调测试
 *
 * <p>运行前请先设置以下环境变量：</p>
 * <p>1. LLM_TEST_ENABLE_LIVE_API=true</p>
 * <p>2. LLM_OPENAI_COMPATIBLE_DEFAULT_BASE_URL=兼容服务 baseUrl，例如 https://api.lkeap.cloud.tencent.com/v1</p>
 * <p>3. LLM_OPENAI_COMPATIBLE_DEFAULT_API_KEY=你的服务 apiKey</p>
 * <p>4. LLM_TEST_MODEL=你的模型名</p>
 *
 * <p>执行命令：</p>
 * <p>mvn -Dtest=OpenAiCompatibleManualTest test</p>
 */
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Disabled("手动联调测试，按需单独移除 Disabled 后执行")
class OpenAiCompatibleManualTest {

    private final OpenAiCompatibleChatService openAiCompatibleChatService;

    OpenAiCompatibleManualTest(OpenAiCompatibleChatService openAiCompatibleChatService) {
        this.openAiCompatibleChatService = openAiCompatibleChatService;
    }

    @Test
    void shouldCallCompatibleChatApiWhenLiveTestEnabled() {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("LLM_TEST_ENABLE_LIVE_API")),
                "跳过真实模型接口测试，设置 LLM_TEST_ENABLE_LIVE_API=true 后再执行。");

        String model = requireEnv("LLM_TEST_MODEL");

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage("你是一个简洁的助手。")
                .addUserMessage("请回复：本地联调成功")
                .build();

        ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(params);

        assertNotNull(completion, "模型返回不能为空");
        assertNotNull(completion.choices(), "模型 choices 不能为空");
        assertTrue(!completion.choices().isEmpty(), "模型至少返回一个 choice");
        assertNotNull(completion.choices().get(0).message(), "第一条消息不能为空");

        System.out.printf("chat completion success, model=%s, id=%s%n",
                completion.model(),
                completion.id());
        System.out.printf("first message=%s%n",
                completion.choices().get(0).message().content().orElse(null));
        System.out.printf("usage=%s%n", completion.usage().orElse(null));
    }

    private String requireEnv(String key) {
        String value = System.getenv(key);
        assertTrue(value != null && !value.isBlank(), () -> "缺少环境变量: " + key);
        return value;
    }
}
