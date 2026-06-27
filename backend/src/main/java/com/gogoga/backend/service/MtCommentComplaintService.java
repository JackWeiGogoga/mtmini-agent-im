package com.gogoga.backend.service;

import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;

/**
 * 差评自动投诉服务。
 */
public interface MtCommentComplaintService {

    void executeCommentAutoComplaintTask(MtCommentAutoComplaintTaskPayload payload);
}
