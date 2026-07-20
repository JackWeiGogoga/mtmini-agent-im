package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 群发单条明细状态。
 */
@Getter
public enum ImBroadcastRecordStatusEnum {

    PENDING("待发送"),
    SENT("已发送"),
    FAILED("发送失败"),
    ;

    private final String desc;

    ImBroadcastRecordStatusEnum(String desc) {
        this.desc = desc;
    }
}
