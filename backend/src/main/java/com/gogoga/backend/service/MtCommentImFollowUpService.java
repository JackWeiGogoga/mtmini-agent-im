package com.gogoga.backend.service;

import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;

/**
 * 差评评价匿名群聊跟进服务。
 */
public interface MtCommentImFollowUpService {

    void executeCommentAutoImFollowUpTask(MtCommentAutoImFollowUpTaskPayload payload);
}
