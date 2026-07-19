package com.gogoga.backend.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 美团 IM 消息类型。
 *
 * <p>两方会话接收协议支持文本、图片、其他和卡片；发送接口只声明支持文本、图片和其他。
 * 官方协议没有单独的视频类型，视频类扩展只能按上游实际推送的“其他”类型透明处理。</p>
 */
public enum ImMessageTypeEnum {

    TEXT(1, true),
    IMAGE(2, true),
    OTHER(3, true),
    CARD(17, false);

    private final int code;
    private final boolean sendSupported;

    ImMessageTypeEnum(int code, boolean sendSupported) {
        this.code = code;
        this.sendSupported = sendSupported;
    }

    public int getCode() {
        return code;
    }

    public boolean isSendSupported() {
        return sendSupported;
    }

    public static Optional<ImMessageTypeEnum> fromCode(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.code == code)
                .findFirst();
    }
}
