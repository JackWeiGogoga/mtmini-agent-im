package com.gogoga.backend.client;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OpenAI 兼容客户端工厂
 */
@Slf4j
@Component
public class OpenAiCompatibleClientFactory {

    private static final String V1_SUFFIX = "/v1";

    private final ConcurrentMap<String, OpenAIClient> clientCache = new ConcurrentHashMap<>();

    public OpenAIClient getClient(String baseUrl, String apiKey) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        String cacheKey = normalizedBaseUrl + "::" + apiKey;
        return clientCache.computeIfAbsent(cacheKey, key -> buildClient(normalizedBaseUrl, apiKey));
    }

    public String normalizeBaseUrl(String baseUrl) {
        String trimmed = StringUtils.trimToEmpty(baseUrl);
        String withoutTrailingSlash = StringUtils.removeEnd(trimmed, "/");
        String withoutChatPath = StringUtils.removeEnd(withoutTrailingSlash, "/chat/completions");
        if (StringUtils.endsWith(withoutChatPath, V1_SUFFIX)) {
            return withoutChatPath;
        }
        return withoutChatPath + V1_SUFFIX;
    }

    private OpenAIClient buildClient(String normalizedBaseUrl, String apiKey) {
        log.info("创建 OpenAI 兼容客户端，baseUrl={}", normalizedBaseUrl);
        return OpenAIOkHttpClient.builder()
                .baseUrl(normalizedBaseUrl)
                .apiKey(apiKey)
                .build();
    }
}
