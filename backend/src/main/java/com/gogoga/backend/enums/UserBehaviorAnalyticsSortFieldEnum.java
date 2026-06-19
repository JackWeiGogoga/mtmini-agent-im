package com.gogoga.backend.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum UserBehaviorAnalyticsSortFieldEnum {

    SHOP_NAME("shopName", "shop_name"),
    SHOP_ID("shopId", "shop_id"),
    SHOP_PHONE("shopPhone", "shop_phone"),
    H5_APP_OPEN_COUNT("h5AppOpenCount", "h5_app_open_count"),
    PC_MASTER_CONSOLE_OPERATION_COUNT("pcMasterConsoleOperationCount", "pc_master_console_operation_count"),
    H5_CONFIG_BUTTON_CLICK_COUNT("h5ConfigButtonClickCount", "h5_config_button_click_count"),
    H5_UNLOCK_AI_PEAK_CLICK_COUNT("h5UnlockAiPeakClickCount", "h5_unlock_ai_peak_click_count"),
    H5_CONFIG_MODAL_CONFIRM_CLICK_COUNT("h5ConfigModalConfirmClickCount", "h5_config_modal_confirm_click_count"),
    H5_CONFIG_MODAL_CANCEL_CLICK_COUNT("h5ConfigModalCancelClickCount", "h5_config_modal_cancel_click_count"),
    H5_POWER_TAB_CLICK_COUNT("h5PowerTabClickCount", "h5_power_tab_click_count"),
    H5_LOG_TAB_CLICK_COUNT("h5LogTabClickCount", "h5_log_tab_click_count"),
    H5_SUPPORT_TAB_CLICK_COUNT("h5SupportTabClickCount", "h5_support_tab_click_count"),
    H5_RECHARGE_GIF_CLICK_COUNT("h5RechargeGifClickCount", "h5_recharge_gif_click_count"),
    H5_POWER_PAY_CLICK_COUNT("h5PowerPayClickCount", "h5_power_pay_click_count"),
    H5_REDEMPTION_CODE_CLICK_COUNT("h5RedemptionCodeClickCount", "h5_redemption_code_click_count");

    private final String field;
    private final String columnAlias;

    UserBehaviorAnalyticsSortFieldEnum(String field, String columnAlias) {
        this.field = field;
        this.columnAlias = columnAlias;
    }

    public static String resolveColumnAlias(String field) {
        for (UserBehaviorAnalyticsSortFieldEnum item : values()) {
            if (StringUtils.equals(item.field, field)) {
                return item.columnAlias;
            }
        }
        return null;
    }

    public static String resolveDirection(String sortOrder) {
        if (StringUtils.equalsAnyIgnoreCase(sortOrder, "asc", "ascend", "ASC")) {
            return "ASC";
        }
        if (StringUtils.equalsAnyIgnoreCase(sortOrder, "desc", "descend", "DESC")) {
            return "DESC";
        }
        return null;
    }
}
