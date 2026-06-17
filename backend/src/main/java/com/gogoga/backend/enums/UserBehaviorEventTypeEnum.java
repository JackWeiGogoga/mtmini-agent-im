package com.gogoga.backend.enums;

import com.gogoga.backend.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum UserBehaviorEventTypeEnum {

    H5_APP_OPEN("H5_APP_OPEN", UserBehaviorEventSourceEnum.H5),
    H5_PROMOTION_SWITCH_CLICK("H5_PROMOTION_SWITCH_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_CONFIG_BUTTON_CLICK("H5_CONFIG_BUTTON_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_UNLOCK_AI_PEAK_CLICK("H5_UNLOCK_AI_PEAK_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_CONFIG_MODAL_CONFIRM_CLICK("H5_CONFIG_MODAL_CONFIRM_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_CONFIG_MODAL_CANCEL_CLICK("H5_CONFIG_MODAL_CANCEL_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_MANUAL_BID_EDIT_OPEN("H5_MANUAL_BID_EDIT_OPEN", UserBehaviorEventSourceEnum.H5),
    H5_MANUAL_BID_SUBMIT_CLICK("H5_MANUAL_BID_SUBMIT_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_POWER_TAB_CLICK("H5_POWER_TAB_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_LOG_TAB_CLICK("H5_LOG_TAB_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_SUPPORT_TAB_CLICK("H5_SUPPORT_TAB_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_RECHARGE_GIF_CLICK("H5_RECHARGE_GIF_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_POWER_PAY_CLICK("H5_POWER_PAY_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_REDEMPTION_CODE_CLICK("H5_REDEMPTION_CODE_CLICK", UserBehaviorEventSourceEnum.H5),

    ADMIN_PROMOTION_SWITCH_UPDATE("ADMIN_PROMOTION_SWITCH_UPDATE", UserBehaviorEventSourceEnum.ADMIN_BACKEND),
    ADMIN_PROMOTION_CONFIG_SAVE("ADMIN_PROMOTION_CONFIG_SAVE", UserBehaviorEventSourceEnum.ADMIN_BACKEND),
    ADMIN_PROMOTION_CONFIG_CANCEL("ADMIN_PROMOTION_CONFIG_CANCEL", UserBehaviorEventSourceEnum.ADMIN_BACKEND),
    ADMIN_PROMOTION_LOG_VIEW("ADMIN_PROMOTION_LOG_VIEW", UserBehaviorEventSourceEnum.ADMIN_BACKEND);

    private final String value;
    private final UserBehaviorEventSourceEnum source;

    UserBehaviorEventTypeEnum(String value, UserBehaviorEventSourceEnum source) {
        this.value = value;
        this.source = source;
    }

    public static UserBehaviorEventTypeEnum fromValue(String value) {
        for (UserBehaviorEventTypeEnum item : values()) {
            if (StringUtils.equalsIgnoreCase(item.value, value)) {
                return item;
            }
        }
        throw new BusinessException("未知埋点事件类型");
    }

    public static UserBehaviorEventTypeEnum requireForSource(String value, UserBehaviorEventSourceEnum source) {
        UserBehaviorEventTypeEnum eventType = fromValue(value);
        if (eventType.source != source) {
            throw new BusinessException("埋点事件类型与来源不匹配");
        }
        return eventType;
    }
}
