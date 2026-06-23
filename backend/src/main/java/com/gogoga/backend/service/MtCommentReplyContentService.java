package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;

public interface MtCommentReplyContentService {

    CommentReplyContentResultDTO generateReply(MtShop shop, MtComment comment);

    CommentReplyContentResultDTO previewReply(MtShop shop, MtComment comment);
}
