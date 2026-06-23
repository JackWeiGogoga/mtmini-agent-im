package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 知识文档解析任务载荷。
 */
@Data
public class MtKnowledgeDocumentParseTaskPayload {

    private Long shopId;

    private Long documentId;
}
