package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 群发批次状态。
 */
@Getter
public enum ImBroadcastTaskStatusEnum {

    PENDING("待编排"),
    SENDING("发送中"),
    SUCCESS("全部成功"),
    PARTIAL("部分成功"),
    FAILED("全部失败"),
    SKIPPED("已跳过"),
    ;

    private final String desc;

    ImBroadcastTaskStatusEnum(String desc) {
        this.desc = desc;
    }
}
