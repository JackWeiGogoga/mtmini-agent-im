package com.gogoga.backend.enums;

/**
 * 网销提成结算状态
 */
public enum SalesCommissionSettlementStatusEnum {

    UNSETTLED,
    SETTLED;

    public static SalesCommissionSettlementStatusEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SalesCommissionSettlementStatusEnum statusEnum : values()) {
            if (statusEnum.name().equalsIgnoreCase(value)) {
                return statusEnum;
            }
        }
        return null;
    }
}
