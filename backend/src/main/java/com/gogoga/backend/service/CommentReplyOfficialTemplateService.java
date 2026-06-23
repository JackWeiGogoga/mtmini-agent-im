package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.enums.CommentReplySentimentEnum;

public interface CommentReplyOfficialTemplateService {

    MtReplyKnowledgeItem selectRandomEnabledTemplate(CommentReplySentimentEnum sentiment);

    String selectRandomReplyOrDefault(CommentReplySentimentEnum sentiment);
}
