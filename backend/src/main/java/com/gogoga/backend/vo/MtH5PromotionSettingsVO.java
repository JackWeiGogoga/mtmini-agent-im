package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * H5 推广配置视图对象
 *
 * @author system
 * @since 2026-03-24
 */
@Data
public class MtH5PromotionSettingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否开启推广
     */
    private Boolean promotionEnabled;

    /**
     * 是否开启余额提醒
     */
    private Boolean balanceReminderEnabled;

    /**
     * 是否已配置策略
     */
    private Boolean strategyConfigured;

    /**
     * 策略配置 JSON
     */
    private String strategyConfigJson;

    /**
     * 是否展示策略入口
     */
    private Boolean strategyEntryVisible;

    /**
     * 是否允许选择 AI 自动管理时段
     */
    private Boolean autoTimeModeAvailable;

    /**
     * AI 自动管理时段提示文案
     */
    private String autoTimeModeHintText;

    /**
     * AI 识别出的高峰时段展示文本
     */
    private List<String> autoTimeModePeakWindows;

    /**
     * 当前高峰日型展示文本
     */
    private String autoTimeModeDayTypeText;

    /**
     * 当前是否命中高峰时段
     */
    private Boolean autoTimeModePeakMatched;

    /**
     * 本次是否尝试识别高峰期
     */
    private Boolean autoTimeModeUnlockAttempted;

    /**
     * AI 自动管理时段解锁结果提示
     */
    private String autoTimeModeUnlockMessage;

    /**
     * AI 自动管理时段不可用原因
     */
    private String autoTimeModeUnavailableReason;
}
