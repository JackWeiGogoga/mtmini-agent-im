package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 评论日志 AI 分析异步任务载荷。
 */
@Data
public class MtCommentLogAnalysisTaskPayload {

    private Long shopId;
    private Long commentId;
}
