package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 美团订单状态枚举。
 *
 * <p>当前只收口系统内已使用或已观测到的状态码，避免业务代码继续散落魔法值。</p>
 */
@Getter
public enum MtOrderStatusEnum {

    CREATED(2, "已创建"),
    PROCESSING(4, "商家已接单"),
    COMPLETED(8, "已完成"),
    CANCELED(9, "已取消"),
    ;

    private final int code;
    private final String desc;

    MtOrderStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MtOrderStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MtOrderStatusEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    public static boolean isCompleted(Integer code) {
        return code != null && COMPLETED.code == code;
    }
}
