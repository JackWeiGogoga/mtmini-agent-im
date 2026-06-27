package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.enums.CommentReplySentimentEnum;

import java.util.List;

public interface CommentReplyOfficialTemplateService {

    MtReplyKnowledgeItem selectRandomEnabledTemplate(CommentReplySentimentEnum sentiment);

    List<MtReplyKnowledgeItem> listEnabledTemplates(CommentReplySentimentEnum sentiment);

    String selectRandomReplyOrDefault(CommentReplySentimentEnum sentiment);
}
