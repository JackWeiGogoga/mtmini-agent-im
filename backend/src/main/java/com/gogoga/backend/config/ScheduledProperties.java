package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 定时任务开关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "scheduled")
public class ScheduledProperties {

    /**
     * 是否启用定时任务 Bean
     */
    private Boolean enabled = true;
}
