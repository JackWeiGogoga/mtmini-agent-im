package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 自动调价大模型配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "mt.promotion.llm")
public class MtPromotionLlmProperties {

    /**
     * 首版统一模型名，由后端配置控制。
     */
    private String model;

    /**
     * 成功执行日志 Markdown Prompt 模板资源路径。
     */
    private String successLogPromptResource = "prompts/promotion-log-success-prompt.txt";

    /**
     * 窗口首次初始出价成功日志 Markdown Prompt 模板资源路径。
     */
    private String initialSuccessLogPromptResource = "prompts/promotion-log-initial-success-prompt.txt";

    /**
     * 跳过/失败日志 Markdown Prompt 模板资源路径。
     */
    private String exceptionLogPromptResource = "prompts/promotion-log-exception-prompt.txt";
}
