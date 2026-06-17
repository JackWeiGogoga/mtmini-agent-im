package com.gogoga.backend.service;

import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.OpenAiCompatibleProperties;
import com.gogoga.backend.exception.BusinessException;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * OpenAI 兼容聊天补全服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleChatService {

    private final OpenAiCompatibleProperties properties;
    private final OpenAiCompatibleClientFactory clientFactory;

    public ChatCompletion createChatCompletion(ChatCompletionCreateParams params) {
        return createChatCompletion(null, null, params);
    }

    public ChatCompletion createChatCompletion(String baseUrl, String apiKey, ChatCompletionCreateParams params) {
        if (params == null) {
            throw new BusinessException(400, "聊天补全参数不能为空");
        }

        String resolvedBaseUrl = firstNonBlank(baseUrl, properties.getDefaultBaseUrl());
        String resolvedApiKey = firstNonBlank(apiKey, properties.getDefaultApiKey());
        if (StringUtils.isBlank(resolvedBaseUrl)) {
            throw new BusinessException(400, "OpenAI 兼容服务 baseUrl 未配置");
        }
        if (StringUtils.isBlank(resolvedApiKey)) {
            throw new BusinessException(400, "OpenAI 兼容服务 apiKey 未配置");
        }

        try {
            OpenAIClient client = clientFactory.getClient(resolvedBaseUrl, resolvedApiKey);
            return client.chat().completions().create(params);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "OpenAI 兼容请求参数不正确", e);
        } catch (RuntimeException e) {
            log.error("调用 OpenAI 兼容聊天补全失败，baseUrl={}", clientFactory.normalizeBaseUrl(resolvedBaseUrl), e);
            throw new BusinessException(502, "调用 OpenAI 兼容聊天补全失败", e);
        }
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
}
