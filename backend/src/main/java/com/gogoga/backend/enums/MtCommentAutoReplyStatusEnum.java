package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 评价自动回复状态。
 */
@Getter
public enum MtCommentAutoReplyStatusEnum {

    PENDING("待回复"),
    SUCCESS("回复成功"),
    FAILED("回复失败"),
    SKIPPED("不可重试跳过");

    private final String desc;

    MtCommentAutoReplyStatusEnum(String desc) {
        this.desc = desc;
    }
}
