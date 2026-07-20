package com.gogoga.backend.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 群发触发频率。
 */
@Getter
public enum ImBroadcastRepeatModeEnum {

    /**
     * 每次：勾选的星期每周都触发。
     */
    EVERY("每次"),

    /**
     * 一次：在下一个匹配的星期触发一次后自动失效。
     */
    ONCE("一次"),
    ;

    private final String desc;

    ImBroadcastRepeatModeEnum(String desc) {
        this.desc = desc;
    }

    public static Optional<ImBroadcastRepeatModeEnum> fromName(String name) {
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(name)).findFirst();
    }
}
