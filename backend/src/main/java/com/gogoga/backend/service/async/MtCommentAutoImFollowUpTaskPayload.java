package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 差评匿名群聊跟进异步任务载荷。
 */
@Data
public class MtCommentAutoImFollowUpTaskPayload {

    private Long shopId;
    private Long commentId;
}
