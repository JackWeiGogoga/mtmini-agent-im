package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评论回复知识范围。
 */
@Getter
public enum CommentReplyKnowledgeScopeEnum {

    OFFICIAL("官方知识"),
    SHOP("商户知识");

    private final String desc;

    CommentReplyKnowledgeScopeEnum(String desc) {
        this.desc = desc;
    }
}
