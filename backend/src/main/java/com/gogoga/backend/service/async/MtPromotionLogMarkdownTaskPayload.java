package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 推广日志 Markdown 异步任务载荷。
 */
@Data
public class MtPromotionLogMarkdownTaskPayload {

    private Long recordId;

    private Long shopId;

    private String accessToken;
}
