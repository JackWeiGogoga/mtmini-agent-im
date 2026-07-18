package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentLogAnalysisPreviewDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.service.async.MtCommentLogAnalysisTaskPayload;

public interface CommentLogAnalysisService {

    void generateAfterAutoReply(MtShop shop, MtComment comment);

    void executeCommentLogAnalysisTask(MtCommentLogAnalysisTaskPayload payload);

    CommentLogAnalysisPreviewDTO preview(MtComment comment);
}
