package com.gogoga.backend.service.promotion;

import lombok.Data;

/**
 * 推广日志 Markdown 生成结果
 */
@Data
public class PromotionLogMarkdownResult {

    private String markdownMessage;

    private boolean fallback;

    private String failureSummary;

    private String rawResponseSummary;

    private Long promptTokens;

    private Long completionTokens;

    private Long totalTokens;
}
