package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 评价自动回复异步任务载荷。
 */
@Data
public class MtCommentAutoReplyTaskPayload {

    private Long shopId;

    private Long commentId;
}
