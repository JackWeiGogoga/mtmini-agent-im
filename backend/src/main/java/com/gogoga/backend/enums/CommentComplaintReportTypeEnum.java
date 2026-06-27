package com.gogoga.backend.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 美团差评申诉类型。
 */
@Getter
public enum CommentComplaintReportTypeEnum {

    RIDER_TIMEOUT(31, "WM_RIDER_TIMEOUT", "骑手配送超时", "配送原因导致差评"),
    RIDER_SERVICE(32, "WM_RIDER_SERVICE", "骑手服务问题", "配送原因导致差评"),
    CHOOSES_WRONG_SCORE(34, "WM_CHOOSES_WRONG_SCORE", "用户选错评分", "用户操作错误导致差评"),
    ENVIRONMENTAL(35, "WM_ENVIRONMENTAL", "用户选择环保单", "用户操作错误导致差评"),
    NOT_THE_ORDER(36, "WM_NOT_THE_ORDER", "评价与订单不符", "用户操作错误导致差评"),
    NOT_RECEIVED(37, "WM_NOT_RECEIVED", "用户未收餐", "用户操作错误导致差评"),
    NO_REMARKS(39, "WM_NO_REMARKS", "用户未备注产生差评", "用户提出额外要求导致差评"),
    UNREASONABLE_REQUEST(40, "WM_UNREASONABLE_REQUEST", "用户提出不合理要求", "用户提出额外要求导致差评"),
    USER_THREATEN(41, "WM_USER_THREATEN", "用户以差评威胁商家", "用户提出额外要求导致差评"),
    ACCOMPANY_BAD_REVIEW(43, "WM_ACCOMPANY_BAD_REVIEW", "同行或特殊身份给差评", "同行或特殊身份给差评"),
    MULTIPLE_BAD_REVIEW(44, "WM_MULTIPLE_BAD_REVIEW", "多次差评", "同行或特殊身份给差评"),
    ADVERT(46, "WM_ADVERT", "不文明用语、泄露隐私、广告信息", "广告或无意义评价"),
    NO_MEAN(47, "WM_NO_MEAN", "内容无意义评价", "广告或无意义评价");

    public static final int DEFAULT_CODE = 47;

    private final int code;
    private final String apiCode;
    private final String name;
    private final String category;

    CommentComplaintReportTypeEnum(int code, String apiCode, String name, String category) {
        this.code = code;
        this.apiCode = apiCode;
        this.name = name;
        this.category = category;
    }

    public static CommentComplaintReportTypeEnum fromCode(Integer code) {
        if (code == null) {
            return NO_MEAN;
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(NO_MEAN);
    }

    public static boolean isSupported(Integer code) {
        if (code == null) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(item -> item.code == code);
    }
}
