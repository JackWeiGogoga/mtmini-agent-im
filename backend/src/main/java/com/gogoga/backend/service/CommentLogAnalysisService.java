package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentLogAnalysisPreviewDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;

public interface CommentLogAnalysisService {

    void generateAfterAutoReply(MtShop shop, MtComment comment);

    CommentLogAnalysisPreviewDTO preview(MtComment comment);
}
