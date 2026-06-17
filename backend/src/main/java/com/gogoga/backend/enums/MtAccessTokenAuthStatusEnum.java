package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 美团授权 token 当前可用状态。
 */
@Getter
public enum MtAccessTokenAuthStatusEnum {

    VALID(1, "有效"),
    REAUTH_REQUIRED(2, "需重新授权");

    private final int code;
    private final String desc;

    MtAccessTokenAuthStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
