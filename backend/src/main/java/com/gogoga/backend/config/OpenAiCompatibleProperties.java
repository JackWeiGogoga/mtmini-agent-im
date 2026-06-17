package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容客户端默认配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm.openai-compatible")
public class OpenAiCompatibleProperties {

    /**
     * 默认兼容服务根地址，支持不带 /v1 的根地址
     */
    private String defaultBaseUrl = "https://api.openai.com";

    /**
     * 默认 API Key，可为空；为空时要求调用方显式传入
     */
    private String defaultApiKey;
}
