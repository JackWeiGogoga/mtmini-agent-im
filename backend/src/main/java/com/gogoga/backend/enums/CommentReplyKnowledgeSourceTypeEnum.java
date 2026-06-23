package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评论回复知识来源类型。
 */
@Getter
public enum CommentReplyKnowledgeSourceTypeEnum {

    TEMPLATE("官方模板"),
    QA("手工QA"),
    DOCUMENT("文档片段");

    private final String desc;

    CommentReplyKnowledgeSourceTypeEnum(String desc) {
        this.desc = desc;
    }
}
