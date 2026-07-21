package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评论回复知识文档解析状态。
 */
@Getter
public enum KnowledgeDocumentParseStatusEnum {

    PENDING("待解析"),
    PARSING("解析中"),
    SUCCESS("解析成功"),
    FAILED("解析失败");

    private final String desc;

    KnowledgeDocumentParseStatusEnum(String desc) {
        this.desc = desc;
    }
}
