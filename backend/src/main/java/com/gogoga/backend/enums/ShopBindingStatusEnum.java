package com.gogoga.backend.enums;

import com.gogoga.backend.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

/**
 * 后台商户绑定状态筛选
 */
public enum ShopBindingStatusEnum {

    ALL("全部"),
    UNBOUND("未绑定"),
    BOUND("已绑定"),
    BOTH("双绑定");

    private final String label;

    ShopBindingStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ShopBindingStatusEnum fromValue(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (ShopBindingStatusEnum status : values()) {
            if (StringUtils.equalsIgnoreCase(status.name(), normalized)) {
                return status;
            }
        }
        throw new BusinessException("不支持的门店绑定状态");
    }
}
