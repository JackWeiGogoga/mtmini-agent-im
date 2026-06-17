package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * H5 算力摘要视图对象
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class MtH5PowerSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前余额
     */
    private Integer balance;

    /**
     * 累计充值
     */
    private Integer totalRecharge;

    /**
     * 累计消费
     */
    private Integer totalConsume;

    /**
     * 累计赠送
     */
    private Integer totalGrant;

    /**
     * 实充已消耗
     */
    private Integer rechargeConsumed;

    /**
     * 赠送已消耗
     */
    private Integer grantConsumed;

    /**
     * 单价对应算力
     */
    private Integer unitPower;

    /**
     * 单价金额（分）
     */
    private Integer unitPriceFen;

    /**
     * 最小购买量
     */
    private Integer minPurchaseAmount;

    /**
     * 最大购买量
     */
    private Integer maxPurchaseAmount;

    /**
     * 是否首充
     */
    private Boolean firstRecharge;

    /**
     * 首充优惠信息
     */
    private FirstRechargeOffer firstRechargeOffer;

    /**
     * 推荐档位
     */
    private List<PackageItem> recommendedPackages;

    /**
     * 当前生效的折扣梯度
     */
    private List<PricingTier> pricingTiers;

    /**
     * 最新充值摘要
     */
    private RechargeSummary latestRecharge;

    /**
     * 算力是否不足
     */
    private Boolean insufficient;

    /**
     * 算力状态文案
     */
    private String statusText;

    @Data
    public static class PackageItem implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 算力数量
         */
        private Integer powerAmount;

        /**
         * 金额（分）
         */
        private Integer totalAmountFen;

        /**
         * 原价金额（分）
         */
        private Integer originalAmountFen;

        /**
         * 档位标记
         */
        private String discountTag;

        /**
         * 首充满额赠送算力
         */
        private Integer bonusPowerAmount;
    }

    @Data
    public static class FirstRechargeOffer implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 是否满足首充优惠资格
         */
        private Boolean eligible;

        /**
         * 首充优惠截止时间
         */
        private LocalDateTime expireAt;

        /**
         * 距离首充优惠结束的剩余秒数
         */
        private Long remainingSeconds;
    }

    @Data
    public static class PricingTier implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 最小算力
         */
        private Integer minPowerAmount;

        /**
         * 最大算力，null 表示不设上限
         */
        private Integer maxPowerAmount;

        /**
         * 折扣率，10000 表示原价，9500 表示 9.5 折
         */
        private Integer payRateBasisPoints;

        /**
         * 展示标签
         */
        private String discountTag;
    }

    @Data
    public static class RechargeSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 充值单号
         */
        private String rechargeNo;

        /**
         * 充值算力
         */
        private Integer powerAmount;

        /**
         * 充值金额（分）
         */
        private Integer totalAmountFen;

        /**
         * 支付状态
         */
        private Integer payStatus;

        /**
         * 支付成功时间
         */
        private LocalDateTime paidAt;
    }
}
