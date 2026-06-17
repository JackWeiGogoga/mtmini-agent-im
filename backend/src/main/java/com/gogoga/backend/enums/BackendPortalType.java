package com.gogoga.backend.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 后台工作台上下文
 */
public enum BackendPortalType {

    ADMIN,
    ORDINARY,
    SALES,
    YOUZAN;

    public static BackendPortalType fromValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        for (BackendPortalType portalType : values()) {
            if (portalType.name().equalsIgnoreCase(value.trim())) {
                return portalType;
            }
        }
        return null;
    }
}
