package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评论自动回复来源。
 */
@Getter
public enum CommentReplySourceEnum {

    OFFICIAL_RANDOM("官方随机"),
    SHOP_RAG("商户RAG"),
    FALLBACK_OFFICIAL("官方兜底");

    private final String desc;

    CommentReplySourceEnum(String desc) {
        this.desc = desc;
    }
}
