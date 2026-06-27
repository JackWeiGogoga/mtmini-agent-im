package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentReplySensitiveWordHitDTO;

public interface CommentReplySensitiveWordService {

    CommentReplySensitiveWordHitDTO detect(String text);

    void validateKnowledgeText(Long shopId, String sourceType, String text);

    String toSafeMessage(CommentReplySensitiveWordHitDTO hit);
}
