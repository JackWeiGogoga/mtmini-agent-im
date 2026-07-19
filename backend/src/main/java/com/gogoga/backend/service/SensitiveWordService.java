package com.gogoga.backend.service;

import com.gogoga.backend.dto.SensitiveWordHitDTO;

public interface SensitiveWordService {

    SensitiveWordHitDTO detect(String text);

    void validateKnowledgeText(Long shopId, String sourceType, String text);

    String toSafeMessage(SensitiveWordHitDTO hit);
}
