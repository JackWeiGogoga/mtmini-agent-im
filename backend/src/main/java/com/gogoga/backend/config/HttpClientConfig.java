package com.gogoga.backend.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端配置类
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Configuration
public class HttpClientConfig {

    /**
     * 配置 OkHttpClient Bean
     * 用于 OAuth 等外部 HTTP 请求
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
}
