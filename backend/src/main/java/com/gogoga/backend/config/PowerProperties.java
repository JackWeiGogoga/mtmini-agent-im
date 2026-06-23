package com.gogoga.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 算力与支付配置
 *
 * @author system
 * @since 2026-03-23
 */
@Data
@Component
@ConfigurationProperties(prefix = "power")
public class PowerProperties implements EnvironmentAware {

    private Environment environment;

    /**
     * 最小购买算力
     */
    private Integer minPurchaseAmount = 1000000;

    /**
     * 最大购买算力
     */
    private Integer maxPurchaseAmount = 1000000000;

    /**
     * 价格单位对应算力
     */
    private Integer unitPower = 100000;

    /**
     * 价格单位金额（分）
     */
    private Integer unitPriceFen = 100;

    /**
     * 新商户授权赠送算力
     */
    private Integer newAuthorizedShopGrantAmount = 800000;

    /**
     * 推荐购买档位
     */
    private Recharge recharge = new Recharge();

    /**
     * 微信支付配置
     */
    private WechatPay wechatPay = new WechatPay();

    /**
     * 算力计费配置
     */
    private Billing billing = new Billing();

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        requirePositive(minPurchaseAmount, "power.min-purchase-amount");
        requirePositive(maxPurchaseAmount, "power.max-purchase-amount");
        requirePositive(unitPower, "power.unit-power");
        requirePositive(unitPriceFen, "power.unit-price-fen");
        requirePositive(newAuthorizedShopGrantAmount, "power.new-authorized-shop-grant-amount");
        requirePositive(billing.getPowerPerYuan(), "power.billing.power-per-yuan");

        if (minPurchaseAmount > maxPurchaseAmount) {
            throw invalidConfig("power.min-purchase-amount 不能大于 power.max-purchase-amount");
        }
        if (minPurchaseAmount % unitPower != 0) {
            throw invalidConfig("power.min-purchase-amount 必须是 power.unit-power 的整数倍");
        }
        if (maxPurchaseAmount % unitPower != 0) {
            throw invalidConfig("power.max-purchase-amount 必须是 power.unit-power 的整数倍");
        }

        long rechargePowerPerYuan = (long) unitPower * 100L;
        long billingPowerPerYuan = (long) billing.getPowerPerYuan() * unitPriceFen;
        if (rechargePowerPerYuan != billingPowerPerYuan) {
            throw invalidConfig("充值单价与算力计费汇率不一致，请检查 power.unit-power、power.unit-price-fen、power.billing.power-per-yuan");
        }

        validateRechargeConfig(recharge.getRecommendedPackages(true), recharge.getDiscountTiers(true), "first");
        validateRechargeConfig(recharge.getRecommendedPackages(false), recharge.getDiscountTiers(false), "regular");
        validateExactPriceOverrides();
        validateWechatPayMockProfile();
    }

    public boolean isWechatPayMockEffective() {
        return wechatPay != null && wechatPay.isMockEnabled() && !isProductionProfile();
    }

    private void validateRechargeConfig(List<Integer> recommendedPackages, List<DiscountTier> discountTiers, String mode) {
        if (recommendedPackages == null || recommendedPackages.isEmpty()) {
            throw invalidConfig("power.recharge." + mode + " 推荐档位不能为空");
        }
        if (discountTiers == null || discountTiers.isEmpty()) {
            throw invalidConfig("power.recharge." + mode + " 折扣档位不能为空");
        }
        requirePositive(recharge.getFirstRechargeValidDays(), "power.recharge.first-recharge-valid-days");

        for (Integer powerAmount : recommendedPackages) {
            validatePowerAmountInRange(powerAmount, "power.recharge." + mode + "-recommended-packages");
        }

        for (DiscountTier tier : discountTiers) {
            if (tier == null) {
                throw invalidConfig("power.recharge." + mode + " 折扣档位不能为空项");
            }
            validatePowerAmountInRange(tier.getMinPowerAmount(), "power.recharge." + mode + "-discount-tiers.min-power-amount");
            if (tier.getMaxPowerAmount() != null) {
                validatePowerAmountWithinRange(tier.getMaxPowerAmount(), "power.recharge." + mode + "-discount-tiers.max-power-amount");
                if (tier.getMaxPowerAmount() < tier.getMinPowerAmount()) {
                    throw invalidConfig("power.recharge." + mode + " 折扣档位 max-power-amount 不能小于 min-power-amount");
                }
            }
            if (tier.getPayRate() == null || tier.getPayRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw invalidConfig("power.recharge." + mode + " 折扣档位 pay-rate 必须大于 0");
            }
        }
    }

    private void validatePowerAmountInRange(Integer powerAmount, String propertyName) {
        validatePowerAmountWithinRange(powerAmount, propertyName);
        if (powerAmount % unitPower != 0) {
            throw invalidConfig(propertyName + " 必须是 power.unit-power 的整数倍");
        }
    }

    private void validatePowerAmountWithinRange(Integer powerAmount, String propertyName) {
        requirePositive(powerAmount, propertyName);
        if (powerAmount < minPurchaseAmount || powerAmount > maxPurchaseAmount) {
            throw invalidConfig(propertyName + " 必须位于最小/最大充值算力范围内");
        }
    }

    private void requirePositive(Integer value, String propertyName) {
        if (value == null || value <= 0) {
            throw invalidConfig(propertyName + " 必须大于 0");
        }
    }

    private void validateExactPriceOverrides() {
        List<Recharge.ExactPriceOverride> overrides = recharge.getExactPriceOverrides();
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        Set<Integer> powerAmounts = new HashSet<>();
        for (Recharge.ExactPriceOverride override : overrides) {
            if (override == null) {
                throw invalidConfig("power.recharge.exact-price-overrides 不能包含空项");
            }
            validatePowerAmountInRange(override.getPowerAmount(), "power.recharge.exact-price-overrides.power-amount");
            requirePositive(override.getUnitPriceFen(), "power.recharge.exact-price-overrides.unit-price-fen");
            if (!powerAmounts.add(override.getPowerAmount())) {
                throw invalidConfig("power.recharge.exact-price-overrides.power-amount 不能重复");
            }
        }
    }

    private void validateWechatPayMockProfile() {
        if (wechatPay != null && wechatPay.isMockEnabled() && isProductionProfile()) {
            throw invalidConfig("线上环境禁止启用 power.wechat-pay.mock-enabled");
        }
    }

    private boolean isProductionProfile() {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .filter(profile -> profile != null)
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "online".equals(profile)
                        || "prod".equals(profile)
                        || "production".equals(profile));
    }

    private IllegalStateException invalidConfig(String message) {
        return new IllegalStateException("算力充值配置非法: " + message);
    }

    @Data
    public static class Billing {

        private static final BigDecimal THOUSAND = new BigDecimal("1000");

        /**
         * 1 元可兑换的算力数量
         */
        private Integer powerPerYuan = 100000;

        /**
         * 单次美团收费接口调用成本（元）
         */
        private BigDecimal meituanPaidApiPriceYuan = new BigDecimal("0.015");

        /**
         * 大模型输入单价（元 / 千 token）
         */
        private BigDecimal llmPromptPricePerThousandTokensYuan = new BigDecimal("0.01");

        /**
         * 大模型输出单价（元 / 千 token）
         */
        private BigDecimal llmCompletionPricePerThousandTokensYuan = new BigDecimal("0.015");

        public int calculateMeituanPaidApiPowerAmount() {
            return calculatePowerAmountByYuan(meituanPaidApiPriceYuan);
        }

        public int calculateLlmPowerAmount(Long promptTokens, Long completionTokens) {
            long safePromptTokens = promptTokens == null ? 0L : promptTokens;
            long safeCompletionTokens = completionTokens == null ? 0L : completionTokens;
            if (safePromptTokens <= 0L && safeCompletionTokens <= 0L) {
                throw new IllegalArgumentException("LLM token 用量缺失，无法计算算力消耗");
            }
            BigDecimal promptCostYuan = llmPromptPricePerThousandTokensYuan
                    .multiply(BigDecimal.valueOf(safePromptTokens))
                    .divide(THOUSAND, 12, RoundingMode.HALF_UP);
            BigDecimal completionCostYuan = llmCompletionPricePerThousandTokensYuan
                    .multiply(BigDecimal.valueOf(safeCompletionTokens))
                    .divide(THOUSAND, 12, RoundingMode.HALF_UP);
            return calculatePowerAmountByYuan(promptCostYuan.add(completionCostYuan));
        }

        int calculatePowerAmountByYuan(BigDecimal amountYuan) {
            if (amountYuan == null || amountYuan.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("金额缺失，无法计算算力消耗");
            }
            return amountYuan
                    .multiply(BigDecimal.valueOf(powerPerYuan))
                    .setScale(0, RoundingMode.CEILING)
                    .intValueExact();
        }
    }

    @Data
    public static class Recharge {

        /**
         * 首充优惠有效天数
         */
        private Integer firstRechargeValidDays = 3;

        /**
         * 首充快捷档位
         */
        private List<Integer> firstRechargeRecommendedPackages = Arrays.asList(
                6_000_000,
                10_000_000,
                50_000_000,
                100_000_000
        );

        /**
         * 非首充快捷档位
         */
        private List<Integer> regularRecommendedPackages = Arrays.asList(
                6_000_000,
                10_000_000,
                50_000_000,
                100_000_000
        );

        /**
         * 首充折扣梯度
         */
        private List<DiscountTier> firstRechargeDiscountTiers = Arrays.asList(
                discountTier(1_000_000, 9_990_000, BigDecimal.ONE),
                discountTier(10_000_000, 99_990_000, new BigDecimal("0.80")),
                discountTier(100_000_000, null, new BigDecimal("0.70"))
        );

        /**
         * 非首充折扣梯度
         */
        private List<DiscountTier> regularRechargeDiscountTiers = Arrays.asList(
                discountTier(1_000_000, 9_990_000, BigDecimal.ONE),
                discountTier(10_000_000, 49_990_000, new BigDecimal("0.95")),
                discountTier(50_000_000, 99_990_000, new BigDecimal("0.85")),
                discountTier(100_000_000, null, new BigDecimal("0.70"))
        );

        /**
         * 精确档位单价覆盖，仅对指定算力值生效
         */
        private List<ExactPriceOverride> exactPriceOverrides = new ArrayList<>();

        public List<Integer> getRecommendedPackages(boolean firstRecharge) {
            return firstRecharge ? firstRechargeRecommendedPackages : regularRecommendedPackages;
        }

        public List<DiscountTier> getDiscountTiers(boolean firstRecharge) {
            return firstRecharge ? firstRechargeDiscountTiers : regularRechargeDiscountTiers;
        }

        public ExactPriceOverride findExactPriceOverride(Integer powerAmount) {
            if (powerAmount == null || exactPriceOverrides == null) {
                return null;
            }
            for (ExactPriceOverride override : exactPriceOverrides) {
                if (override != null && powerAmount.equals(override.getPowerAmount())) {
                    return override;
                }
            }
            return null;
        }

        private static DiscountTier discountTier(Integer minPowerAmount, Integer maxPowerAmount, BigDecimal payRate) {
            DiscountTier tier = new DiscountTier();
            tier.setMinPowerAmount(minPowerAmount);
            tier.setMaxPowerAmount(maxPowerAmount);
            tier.setPayRate(payRate);
            return tier;
        }

        @Data
        public static class ExactPriceOverride {

            /**
             * 精确匹配的算力数量
             */
            private Integer powerAmount;

            /**
             * 覆盖后的单位价格（分）
             */
            private Integer unitPriceFen;
        }
    }

    @Data
    public static class DiscountTier {

        /**
         * 最小算力
         */
        private Integer minPowerAmount;

        /**
         * 最大算力，null 表示不设上限
         */
        private Integer maxPowerAmount;

        /**
         * 支付折扣率，例如 0.95 表示 9.5 折
         */
        private BigDecimal payRate = BigDecimal.ONE;

        public boolean matches(Integer powerAmount) {
            if (powerAmount == null || minPowerAmount == null || payRate == null) {
                return false;
            }
            if (powerAmount < minPowerAmount) {
                return false;
            }
            return maxPowerAmount == null || powerAmount <= maxPowerAmount;
        }

        public int applyDiscount(int originalAmountFen) {
            return BigDecimal.valueOf(originalAmountFen)
                    .multiply(payRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        }

        public int getPayRateBasisPoints() {
            return payRate.multiply(new BigDecimal("10000"))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        }

        public String buildDiscountTag(boolean firstRecharge) {
            if (payRate == null || payRate.compareTo(BigDecimal.ONE) >= 0) {
                return null;
            }
            BigDecimal discount = payRate.multiply(new BigDecimal("10")).stripTrailingZeros();
            return (firstRecharge ? "首充" : "") + discount.toPlainString() + "折";
        }
    }

    public DiscountTier resolveRechargeDiscountTier(Integer powerAmount, boolean firstRecharge) {
        return recharge.getDiscountTiers(firstRecharge).stream()
                .filter(tier -> tier.matches(powerAmount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未匹配到充值折扣档位"));
    }

    public int resolveRechargeUnitPriceFen(Integer powerAmount) {
        Recharge.ExactPriceOverride override = recharge.findExactPriceOverride(powerAmount);
        return override == null ? unitPriceFen : override.getUnitPriceFen();
    }

    public int calculateOriginalRechargeAmountFen(Integer powerAmount) {
        return powerAmount / unitPower * resolveRechargeUnitPriceFen(powerAmount);
    }

    public int calculateRechargeTotalAmountFen(Integer powerAmount, boolean firstRecharge) {
        int originalAmountFen = calculateOriginalRechargeAmountFen(powerAmount);
        return resolveRechargeDiscountTier(powerAmount, firstRecharge).applyDiscount(originalAmountFen);
    }

    @Data
    public static class WechatPay {

        /**
         * 是否启用 mock 支付
         */
        private boolean mockEnabled = false;

        /**
         * 支付商户号
         */
        private String mchId;

        /**
         * 商户证书序列号
         */
        private String merchantSerialNumber;

        /**
         * 商户私钥路径
         */
        private String privateKeyPath;

        /**
         * APIv3 密钥
         */
        private String apiV3Key;

        /**
         * 微信支付平台公钥路径
         */
        private String platformPublicKeyPath;

        /**
         * 微信应用 ID
         */
        private String appId;

        /**
         * H5 支付回调地址
         */
        private String notifyUrl = "http://localhost:8080/api/callbacks/wechat/pay";

        /**
         * H5 场景名称（映射微信支付 H5 下单的 app_name）
         */
        private String wapName = "算力充值";

        /**
         * H5 场景地址（映射微信支付 H5 下单的 app_url）
         */
        private String wapUrl = "http://localhost:5173/#/pages/power/index";
    }
}
