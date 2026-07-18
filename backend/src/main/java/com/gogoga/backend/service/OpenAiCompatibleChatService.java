package com.gogoga.backend.service;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.OpenAiCompatibleProperties;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.llm.LlmCapacityDeferredException;
import com.gogoga.backend.service.llm.LlmClusterRateLimiter;
import com.gogoga.backend.service.llm.LlmRateLimitContext;
import com.gogoga.backend.service.llm.LlmRateLimitDescriptor;
import com.gogoga.backend.service.llm.LlmRateLimitRetryAfterParser;
import com.openai.client.OpenAIClient;
import com.openai.errors.RateLimitException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * OpenAI 兼容聊天补全服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleChatService {

    private final OpenAiCompatibleProperties properties;
    private final OpenAiCompatibleClientFactory clientFactory;
    private final LlmClusterRateLimiter rateLimiter;
    private final LlmRateLimitRetryAfterParser retryAfterParser;

    public ChatCompletion createChatCompletion(ChatCompletionCreateParams params) {
        return createChatCompletion(null, null, null, params);
    }

    public ChatCompletion createChatCompletion(String baseUrl, String apiKey, ChatCompletionCreateParams params) {
        return createChatCompletion(null, baseUrl, apiKey, params);
    }

    public ChatCompletion createChatCompletion(
            String provider,
            String baseUrl,
            String apiKey,
            ChatCompletionCreateParams params
    ) {
        if (params == null) {
            throw new BusinessException(400, "聊天补全参数不能为空");
        }

        String resolvedBaseUrl = firstNonBlank(baseUrl, properties.getDefaultBaseUrl());
        String resolvedApiKey = firstNonBlank(apiKey, properties.getDefaultApiKey());
        String resolvedProvider = firstNonBlank(provider, properties.getDefaultProvider());
        if (StringUtils.isBlank(resolvedBaseUrl)) {
            throw new BusinessException(400, "OpenAI 兼容服务 baseUrl 未配置");
        }
        if (StringUtils.isBlank(resolvedApiKey)) {
            throw new BusinessException(400, "OpenAI 兼容服务 apiKey 未配置");
        }

        LlmRateLimitDescriptor descriptor = new LlmRateLimitDescriptor(
                resolvedProvider,
                resolvedApiKey,
                params.model().asString(),
                "chat-completion",
                LlmRateLimitContext.isInteractive()
        );
        try {
            OpenAIClient client = clientFactory.getClient(resolvedBaseUrl, resolvedApiKey);
            rateLimiter.acquire(descriptor);
            try {
                return client.chat().completions().create(params);
            } catch (RateLimitException firstRateLimit) {
                long retryAfterMillis = retryAfterMillis(firstRateLimit);
                rateLimiter.registerProviderRateLimit(descriptor, retryAfterMillis);
                rateLimiter.acquire(descriptor);
                try {
                    return client.chat().completions().create(params);
                } catch (RateLimitException secondRateLimit) {
                    long secondRetryAfterMillis = retryAfterMillis(secondRateLimit);
                    rateLimiter.registerProviderRateLimit(descriptor, secondRetryAfterMillis);
                    throw new LlmCapacityDeferredException(
                            "LLM_PROVIDER_RATE_LIMITED",
                            secondRetryAfterMillis,
                            secondRateLimit
                    );
                }
            }
        } catch (LlmCapacityDeferredException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "OpenAI 兼容请求参数不正确", e);
        } catch (RuntimeException e) {
            log.error("调用 OpenAI 兼容聊天补全失败，baseUrl={}", clientFactory.normalizeBaseUrl(resolvedBaseUrl), e);
            throw new BusinessException(502, "调用 OpenAI 兼容聊天补全失败", e);
        }
    }

    private long retryAfterMillis(RateLimitException exception) {
        String retryAfter = exception.headers().values("retry-after").stream().findFirst().orElse(null);
        return retryAfterParser.parseMillis(retryAfter, Duration.ofMillis(rateLimiter.defaultCooldownMillis()));
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
}
