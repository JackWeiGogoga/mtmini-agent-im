package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店自动调价执行记录
 *
 * @author system
 * @since 2026-03-26
 */
@Data
public class MtPromotionBidRecord {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 业务日期
     */
    private LocalDate bizDate;

    /**
     * 时段时间
     */
    private LocalDateTime slotTime;

    /**
     * 记录来源：AUTO / MANUAL
     */
    private String recordSource;

    /**
     * 手动记录归属的标准 10 分钟槽位
     */
    private LocalDateTime attributedSlotTime;

    /**
     * 实际操作时间
     */
    private LocalDateTime operatedAt;

    /**
     * 窗口唯一标识
     */
    private String windowKey;

    /**
     * 窗口开始时间
     */
    private LocalDateTime windowStartAt;

    /**
     * 窗口结束时间
     */
    private LocalDateTime windowEndAt;

    /**
     * 请求号
     */
    private String requestNo;

    /**
     * 上一次出价
     */
    private BigDecimal previousBidAmount;

    /**
     * 下一次出价
     */
    private BigDecimal nextBidAmount;

    /**
     * 决策动作
     */
    private String decisionAction;

    /**
     * 决策原因
     */
    private String decisionReason;

    /**
     * 广告累计花费计数器
     */
    private BigDecimal costCounterValue;

    /**
     * 最近一个窗口花费
     */
    private BigDecimal totalCost;

    /**
     * 最近一个窗口实收
     */
    private BigDecimal totalIncome;

    /**
     * 最近一个窗口费用率
     */
    private BigDecimal totalCostRatio;

    /**
     * 最近一个窗口花费
     */
    private BigDecimal costDelta;

    /**
     * 执行状态
     */
    private String executeStatus;

    /**
     * 指标原始内容
     */
    private String metricsPayloadJson;

    /**
     * 更新原始内容
     */
    private String updatePayloadJson;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 商家可读 Markdown 日志
     */
    private String markdownMessage;

    /**
     * 日志 Markdown 状态
     */
    private String logMarkdownStatus;

    /**
     * 日志异步任务 ID
     */
    private Long logTaskId;

    /**
     * 决策扩展详情
     */
    private String decisionDetailJson;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
