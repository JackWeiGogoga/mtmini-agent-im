package com.gogoga.backend.enums;

import lombok.Getter;

/**
 * 群发配置状态。
 *
 * <p>{@code enabled=0} 有两种成因：商家显式删除（字段被清空）和「一次」触发后自动失效（字段保留）。
 * 商家侧只关心「当前有没有在跑的定时任务」，两者都回到未配置态；但运营需要区分，
 * 因此由后端统一判定状态，避免 H5 与总控台各写一套 {@code enabled + weekDays} 的组合判断。</p>
 */
@Getter
public enum ImBroadcastSettingStatusEnum {

    /**
     * 从未配置，或配置已被显式删除。
     */
    NONE("未配置"),

    /**
     * 定时计划生效中。
     */
    ACTIVE("生效中"),

    /**
     * 「一次」已触发完成并自动失效，配置内容仍保留供回显。
     */
    ONCE_COMPLETED("已完成（一次性）"),
    ;

    private final String desc;

    ImBroadcastSettingStatusEnum(String desc) {
        this.desc = desc;
    }
}
