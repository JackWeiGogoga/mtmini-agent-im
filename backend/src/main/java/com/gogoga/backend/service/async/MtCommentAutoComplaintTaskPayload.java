package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 差评自动投诉异步任务载荷。
 */
@Data
public class MtCommentAutoComplaintTaskPayload {

    private Long shopId;

    private Long commentId;
}
