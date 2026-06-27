package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentComplaintPreviewDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;

/**
 * 差评自动投诉服务。
 */
public interface MtCommentComplaintService {

    void executeCommentAutoComplaintTask(MtCommentAutoComplaintTaskPayload payload);

    CommentComplaintPreviewDTO previewComplaint(MtShop shop, MtComment comment);
}
