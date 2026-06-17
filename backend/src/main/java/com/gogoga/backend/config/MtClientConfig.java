package com.gogoga.backend.config;

import com.meituan.sdk.DefaultMeituanClient;
import com.meituan.sdk.MeituanClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 美团客户端配置
 *
 * @author system
 * @since 2026-03-22
 */
@Configuration
public class MtClientConfig {

    /**
     * 创建美团 SDK 客户端
     *
     * @param mtProperties 美团配置
     * @return 美团客户端
     */
    @Bean
    public MeituanClient meituanClient(MtProperties mtProperties) {
        return DefaultMeituanClient.builder(mtProperties.getDeveloperId(), mtProperties.getSignKey()).build();
    }
}
