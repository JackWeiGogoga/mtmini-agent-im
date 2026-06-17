package com.gogoga.backend.enums;

import com.gogoga.backend.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum UserBehaviorEventSourceEnum {

    H5("H5", "H5"),
    ADMIN_BACKEND("ADMIN_BACKEND", "管理后台");

    private final String value;
    private final String label;

    UserBehaviorEventSourceEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static UserBehaviorEventSourceEnum fromValue(String value) {
        for (UserBehaviorEventSourceEnum item : values()) {
            if (StringUtils.equalsIgnoreCase(item.value, value)) {
                return item;
            }
        }
        throw new BusinessException("未知埋点事件来源");
    }
}
