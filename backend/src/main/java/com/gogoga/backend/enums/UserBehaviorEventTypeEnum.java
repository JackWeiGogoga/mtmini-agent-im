package com.gogoga.backend.enums;

import com.gogoga.backend.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum UserBehaviorEventTypeEnum {

    H5_APP_OPEN("H5_APP_OPEN", UserBehaviorEventSourceEnum.H5),
    H5_PAGE_VIEW("H5_PAGE_VIEW", UserBehaviorEventSourceEnum.H5),
    H5_SUPPORT_ENTRY_CLICK("H5_SUPPORT_ENTRY_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_EXTERNAL_LINK_CLICK("H5_EXTERNAL_LINK_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_RECHARGE_SUBMIT_CLICK("H5_RECHARGE_SUBMIT_CLICK", UserBehaviorEventSourceEnum.H5),
    H5_REDEMPTION_APPLY_RESULT("H5_REDEMPTION_APPLY_RESULT", UserBehaviorEventSourceEnum.H5),
    H5_LOG_DETAIL_EXPAND("H5_LOG_DETAIL_EXPAND", UserBehaviorEventSourceEnum.H5),
    H5_KNOWLEDGE_ACTION("H5_KNOWLEDGE_ACTION", UserBehaviorEventSourceEnum.H5),

    PC_COMMENT_LOG_VIEW("PC_COMMENT_LOG_VIEW", UserBehaviorEventSourceEnum.ADMIN_BACKEND),
    PC_KNOWLEDGE_VIEW("PC_KNOWLEDGE_VIEW", UserBehaviorEventSourceEnum.ADMIN_BACKEND);

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
