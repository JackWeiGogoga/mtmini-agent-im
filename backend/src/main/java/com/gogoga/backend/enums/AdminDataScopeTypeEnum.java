package com.gogoga.backend.enums;

/**
 * 后台用户数据范围类型
 */
public enum AdminDataScopeTypeEnum {

    SELF,
    TEAM,
    ALL;

    public static AdminDataScopeTypeEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return SELF;
        }
        for (AdminDataScopeTypeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return SELF;
    }
}
