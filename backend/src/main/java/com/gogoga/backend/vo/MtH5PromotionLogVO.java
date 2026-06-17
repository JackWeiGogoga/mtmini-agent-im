package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * H5 推广日志视图对象
 *
 * @author system
 * @since 2026-03-26
 */
@Data
public class MtH5PromotionLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时段时间
     */
    private LocalDateTime slotTime;

    /**
     * 日志类型
     */
    private String type;

    /**
     * 执行状态
     */
    private String status;

    /**
     * 出价描述
     */
    private String price;

    /**
     * 日志内容
     */
    private String message;

    /**
     * Markdown 日志内容
     */
    private String markdownMessage;

    /**
     * Markdown 状态：READY / FALLBACK / PENDING
     */
    private String markdownStatus;
}
