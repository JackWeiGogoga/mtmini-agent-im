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

    TEXT(1, true, true),
    IMAGE(2, true, false),
    OTHER(3, true, false),
    CARD(17, false, false);

    private final int code;
    private final boolean sendSupported;
    private final boolean conversational;

    ImMessageTypeEnum(int code, boolean sendSupported, boolean conversational) {
        this.code = code;
        this.sendSupported = sendSupported;
        this.conversational = conversational;
    }

    public int getCode() {
        return code;
    }

    public boolean isSendSupported() {
        return sendSupported;
    }

    /**
     * 是否承载真实的用户话语。
     *
     * <p>只有此类消息才进入模型上下文、才能触发自动回复、才能顶掉一条待发送的回复。
     * CARD 的正文是“其他消息”这类占位串，IMAGE/OTHER 也没有可用文本，把它们计入会
     * 污染 prompt，并且会把用户真实提问的回复顶成 SUPERSEDED 后无人接手。</p>
     */
    public boolean isConversational() {
        return conversational;
    }

    public static boolean isConversational(Integer code) {
        return fromCode(code).map(ImMessageTypeEnum::isConversational).orElse(false);
    }

    /**
     * 是否为卡片消息。卡片由平台推送而非真人编辑，因此不能视为商家的人工回复。
     */
    public static boolean isCard(Integer code) {
        return code != null && CARD.code == code;
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
