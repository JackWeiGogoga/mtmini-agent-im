package com.gogoga.backend.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
public enum UserBehaviorAnalyticsSortFieldEnum {

    SHOP_NAME("shopName", "shop_name"),
    SHOP_ID("shopId", "shop_id"),
    SHOP_PHONE("shopPhone", "shop_phone"),
    H5_APP_OPEN_COUNT("h5AppOpenCount", "h5_app_open_count"),
    H5_HOME_PAGE_VIEW_COUNT("h5HomePageViewCount", "h5_home_page_view_count"),
    H5_LOG_PAGE_VIEW_COUNT("h5LogPageViewCount", "h5_log_page_view_count"),
    H5_POWER_PAGE_VIEW_COUNT("h5PowerPageViewCount", "h5_power_page_view_count"),
    H5_KNOWLEDGE_PAGE_VIEW_COUNT("h5KnowledgePageViewCount", "h5_knowledge_page_view_count"),
    H5_SUPPORT_PAGE_VIEW_COUNT("h5SupportPageViewCount", "h5_support_page_view_count"),
    H5_SUPPORT_ENTRY_CLICK_COUNT("h5SupportEntryClickCount", "h5_support_entry_click_count"),
    H5_EXTERNAL_LINK_CLICK_COUNT("h5ExternalLinkClickCount", "h5_external_link_click_count"),
    H5_RECHARGE_SUBMIT_CLICK_COUNT("h5RechargeSubmitClickCount", "h5_recharge_submit_click_count"),
    H5_REDEMPTION_APPLY_SUCCESS_COUNT("h5RedemptionApplySuccessCount", "h5_redemption_apply_success_count"),
    H5_LOG_DETAIL_EXPAND_COUNT("h5LogDetailExpandCount", "h5_log_detail_expand_count"),
    H5_KNOWLEDGE_ACTION_COUNT("h5KnowledgeActionCount", "h5_knowledge_action_count"),
    PC_COMMENT_LOG_VIEW_COUNT("pcCommentLogViewCount", "pc_comment_log_view_count"),
    PC_KNOWLEDGE_VIEW_COUNT("pcKnowledgeViewCount", "pc_knowledge_view_count"),
    H5_AUTO_REPLY_CHANGE_COUNT("h5AutoReplyChangeCount", "h5_auto_reply_change_count"),
    H5_NEGATIVE_APPEAL_CHANGE_COUNT("h5NegativeAppealChangeCount", "h5_negative_appeal_change_count"),
    PC_AUTO_REPLY_CHANGE_COUNT("pcAutoReplyChangeCount", "pc_auto_reply_change_count"),
    PC_NEGATIVE_APPEAL_CHANGE_COUNT("pcNegativeAppealChangeCount", "pc_negative_appeal_change_count"),
    SYSTEM_AUTO_DISABLE_COUNT("systemAutoDisableCount", "system_auto_disable_count");

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
