package com.gogoga.backend.enums;

import com.gogoga.backend.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

/**
 * 后台门店绑定业务域
 */
public enum ShopBindingDomainEnum {

    ALL("全部"),
    ORDINARY("普通商户"),
    SALES("网销用户");

    private final String label;

    ShopBindingDomainEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ShopBindingDomainEnum fromValue(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (ShopBindingDomainEnum domain : values()) {
            if (StringUtils.equalsIgnoreCase(domain.name(), normalized)) {
                return domain;
            }
        }
        throw new BusinessException("不支持的门店绑定域");
    }
}
