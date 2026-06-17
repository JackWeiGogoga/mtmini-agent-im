package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 算力充值状态枚举
 *
 * @author system
 * @since 2026-03-23
 */
@Getter
public enum PowerRechargeStatusEnum {

    CREATED(0, "待支付"),
    PAYING(1, "支付中"),
    SUCCESS(2, "支付成功"),
    FAILED(3, "支付失败"),
    CLOSED(4, "已关闭");

    private final int code;
    private final String desc;

    PowerRechargeStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 判断是否成功状态
     *
     * @param code 状态码
     * @return 是否成功
     */
    public static boolean isSuccess(Integer code) {
        return code != null && SUCCESS.code == code;
    }
}
