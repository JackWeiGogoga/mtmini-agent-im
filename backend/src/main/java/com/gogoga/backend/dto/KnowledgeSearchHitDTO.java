package com.gogoga.backend.dto;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import lombok.Data;

/**
 * 知识检索命中结果。
 */
@Data
public class KnowledgeSearchHitDTO {

    private MtReplyKnowledgeChunk chunk;
    private Double score;
    private Double cosineScore;
}
