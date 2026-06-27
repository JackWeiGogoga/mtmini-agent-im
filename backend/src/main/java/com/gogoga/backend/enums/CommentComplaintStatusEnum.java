package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 差评自动投诉状态。
 */
@Getter
public enum CommentComplaintStatusEnum {

    PENDING("待处理"),
    GENERATED("已生成申诉内容"),
    SUCCESS("申诉成功"),
    FAILED("申诉失败"),
    SKIPPED("已跳过");

    private final String desc;

    CommentComplaintStatusEnum(String desc) {
        this.desc = desc;
    }
}
