package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 数据库异步任务状态枚举。
 */
@Getter
public enum MtAsyncTaskStatusEnum {

    PENDING("待执行"),
    PROCESSING("执行中"),
    SUCCESS("执行成功"),
    DEAD("终态失败"),
    ;

    private final String desc;

    MtAsyncTaskStatusEnum(String desc) {
        this.desc = desc;
    }
}
