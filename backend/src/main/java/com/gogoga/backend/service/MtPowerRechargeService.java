package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerPaymentCreateResultDTO;
import com.gogoga.backend.dto.PowerPaymentQueryResultDTO;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.PowerRechargeStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.service.SalesRedemptionCodeService.ResolvedRedemptionCode;
import com.gogoga.backend.vo.MtH5PowerRechargeCreateVO;
import com.gogoga.backend.vo.MtH5PowerRechargeResultVO;
import com.gogoga.backend.vo.MtH5PowerSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 门店算力充值服务
 *
 * @author system
 * @since 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPowerRechargeService {

    private static final String PAY_CHANNEL_WECHAT_H5 = "WECHAT_H5";
    private static final int POWER_INSUFFICIENT_THRESHOLD = 500_000;
    private static final int FIRST_RECHARGE_BONUS_THRESHOLD = 10_000_000;
    private static final int FIRST_RECHARGE_BONUS_AMOUNT = 1_000_000;
    private static final String FIRST_RECHARGE_BONUS_GRANT_REASON = "首充满额赠送";
    private static final String FIRST_RECHARGE_BONUS_OPERATOR = "system";
    private static final String GRANT_STATUS_SUCCESS = "SUCCESS";

    private final PowerProperties powerProperties;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtPowerRechargeMapper mtPowerRechargeMapper;
    private final MtPowerGrantMapper mtPowerGrantMapper;
    private final MtPowerPayService mtPowerPayService;
    private final MtShopService mtShopService;
    private final SalesRedemptionCodeService salesRedemptionCodeService;
    private final SalesCommissionService salesCommissionService;

    /**
     * 获取算力摘要
     *
     * @param shop 当前门店
     * @return 摘要信息
     */
    public MtH5PowerSummaryVO getSummary(MtShop shop, String redemptionCode) {
        MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(shop.getId());
        MtPowerRecharge latestRecharge = mtPowerRechargeMapper.selectLatestByShopId(shop.getId());
        RechargePricingContext pricingContext = buildRechargePricingContext(shop, account, latestRecharge, LocalDateTime.now(), redemptionCode, false);
        MtH5PowerSummaryVO.FirstRechargeOffer firstRechargeOffer = pricingContext.getFirstRechargeOffer();
        boolean firstRecharge = pricingContext.isFirstRecharge();

        MtH5PowerSummaryVO summaryVO = new MtH5PowerSummaryVO();
        summaryVO.setBalance(account.getBalance());
        boolean insufficient = account.getBalance() != null && account.getBalance() < POWER_INSUFFICIENT_THRESHOLD;
        summaryVO.setInsufficient(insufficient);
        summaryVO.setStatusText(insufficient ? "不足" : null);
        summaryVO.setTotalRecharge(account.getTotalRecharge());
        summaryVO.setTotalConsume(account.getTotalConsume());
        summaryVO.setTotalGrant(account.getTotalGrant());
        summaryVO.setRechargeConsumed(calculateConsumed(defaultInt(account.getTotalRecharge()), defaultInt(account.getRechargeBalance())));
        summaryVO.setGrantConsumed(calculateConsumed(defaultInt(account.getTotalGrant()), defaultInt(account.getGrantBalance())));
        summaryVO.setUnitPower(powerProperties.getUnitPower());
        summaryVO.setUnitPriceFen(powerProperties.getUnitPriceFen());
        summaryVO.setMinPurchaseAmount(powerProperties.getMinPurchaseAmount());
        summaryVO.setMaxPurchaseAmount(powerProperties.getMaxPurchaseAmount());
        summaryVO.setFirstRecharge(firstRecharge);
        summaryVO.setFirstRechargeOffer(firstRechargeOffer);
        summaryVO.setRecommendedPackages(buildRecommendedPackages(pricingContext.getPricingTiers(), firstRecharge, !hasPaidRecharge(account)));
        summaryVO.setPricingTiers(buildPricingTiers(pricingContext.getPricingTiers(), firstRecharge));
        summaryVO.setLatestRecharge(buildLatestRecharge(latestRecharge));
        return summaryVO;
    }

    /**
     * 创建充值单
     *
     * @param shop 当前门店
     * @param powerAmount 购买算力
     * @param clientIp 客户端 IP
     * @return 创建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PowerRechargeCreateVO createRecharge(MtShop shop, Integer powerAmount, String redemptionCode, String clientIp) {
        validatePowerAmount(powerAmount);
        LocalDateTime now = LocalDateTime.now();
        MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(shop.getId());
        MtPowerRecharge latestRecharge = mtPowerRechargeMapper.selectLatestByShopId(shop.getId());
        RechargePricingContext pricingContext = buildRechargePricingContext(shop, account, latestRecharge, now, redemptionCode, false);
        PowerProperties.DiscountTier discountTier = resolveDiscountTier(powerAmount, pricingContext.getPricingTiers());
        int effectiveUnitPriceFen = powerProperties.resolveRechargeUnitPriceFen(powerAmount);
        int originalAmountFen = calculateOriginalTotalAmount(powerAmount);
        int totalAmountFen = discountTier.applyDiscount(originalAmountFen);
        ResolvedRedemptionCode resolvedRedemptionCode = pricingContext.getResolvedRedemptionCode();

        MtPowerRecharge recharge = new MtPowerRecharge();
        recharge.setRechargeNo(generateRechargeNo());
        recharge.setShopId(shop.getId());
        recharge.setPowerAmount(powerAmount);
        recharge.setUnitPower(powerProperties.getUnitPower());
        recharge.setUnitPriceFen(effectiveUnitPriceFen);
        recharge.setOriginalAmountFen(originalAmountFen);
        recharge.setDiscountContextType(pricingContext.getDiscountContextType());
        recharge.setDiscountTag(discountTier.buildDiscountTag(pricingContext.isFirstRecharge()));
        recharge.setDiscountRateBasisPoints(discountTier.getPayRateBasisPoints());
        recharge.setTotalAmountFen(totalAmountFen);
        recharge.setRedemptionCodeId(resolvedRedemptionCode == null ? null : resolvedRedemptionCode.getId());
        recharge.setRedemptionCode(resolvedRedemptionCode == null ? null : resolvedRedemptionCode.getCode());
        recharge.setSalesUserId(resolvedRedemptionCode == null ? null : resolvedRedemptionCode.getSalesUserId());
        recharge.setSalesUserPhone(resolvedRedemptionCode == null ? null : resolvedRedemptionCode.getSalesUserPhone());
        recharge.setCommissionWindowStartAt(pricingContext.getCommissionWindowStartAt());
        recharge.setDefaultCommissionRatio(pricingContext.getDefaultCommissionRatio());
        recharge.setPayChannel(PAY_CHANNEL_WECHAT_H5);
        recharge.setPayStatus(PowerRechargeStatusEnum.CREATED.getCode());
        recharge.setPaymentOrderNo(generatePaymentOrderNo());
        recharge.setCreateTime(now);
        recharge.setUpdateTime(now);
        mtPowerRechargeMapper.insert(recharge);
        if (resolvedRedemptionCode != null) {
            salesRedemptionCodeService.recordRechargeUse(
                    resolvedRedemptionCode,
                    recharge.getId(),
                    recharge.getRechargeNo(),
                    shop.getId(),
                    shop.getPoiId(),
                    StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName())
            );
        }

        PowerPaymentCreateResultDTO payResult = mtPowerPayService.createRechargeOrder(recharge, shop, clientIp);
        recharge.setPayStatus(PowerRechargeStatusEnum.PAYING.getCode());
        recharge.setPayPayload(payResult.getRawPayload());
        recharge.setUpdateTime(LocalDateTime.now());
        mtPowerRechargeMapper.updateById(recharge);

        MtH5PowerRechargeCreateVO vo = new MtH5PowerRechargeCreateVO();
        vo.setRechargeNo(recharge.getRechargeNo());
        if (payResult.isMockEnabled()) {
            vo.setMockPayEnabled(true);
        }
        vo.setMwebUrl(payResult.getMwebUrl());
        return vo;
    }

    /**
     * 查询充值结果
     *
     * @param shopId 店铺 ID
     * @param rechargeNo 充值单号
     * @return 查询结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PowerRechargeResultVO getRechargeResult(Long shopId, String rechargeNo) {
        MtPowerRecharge recharge = requireRecharge(shopId, rechargeNo);
        if (!PowerRechargeStatusEnum.isSuccess(recharge.getPayStatus())
                && recharge.getPayStatus() != PowerRechargeStatusEnum.FAILED.getCode()
                && recharge.getPayStatus() != PowerRechargeStatusEnum.CLOSED.getCode()) {
            PowerPaymentQueryResultDTO queryResult = mtPowerPayService.queryRechargeOrder(recharge);
            syncRechargeStatus(recharge, queryResult);
            recharge = requireRecharge(shopId, rechargeNo);
        }
        return buildRechargeResultVO(recharge);
    }

    /**
     * mock 支付成功
     *
     * @param shopId 店铺 ID
     * @param rechargeNo 充值单号
     * @return 充值结果
     */
    @Transactional(rollbackFor = Exception.class)
    public MtH5PowerRechargeResultVO mockSuccess(Long shopId, String rechargeNo) {
        if (!powerProperties.isWechatPayMockEffective()) {
            throw new BusinessException(400, "当前环境未启用 mock 支付");
        }

        MtPowerRecharge recharge = requireRecharge(shopId, rechargeNo);
        PowerPaymentQueryResultDTO queryResult = new PowerPaymentQueryResultDTO();
        queryResult.setRechargeNo(rechargeNo);
        queryResult.setPaymentOrderNo(recharge.getPaymentOrderNo());
        queryResult.setTransactionId("MOCK-" + UUID.randomUUID().toString().replace("-", ""));
        queryResult.setPayStatus(PowerRechargeStatusEnum.SUCCESS.getCode());
        queryResult.setSuccess(true);
        queryResult.setFinished(true);
        queryResult.setRawPayload("{\"mock\":true,\"rechargeNo\":\"" + rechargeNo + "\"}");
        syncRechargeStatus(recharge, queryResult);
        return buildRechargeResultVO(requireRecharge(shopId, rechargeNo));
    }

    /**
     * 处理微信支付通知
     *
     * @param request 请求对象
     * @param body 请求体
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleWechatNotify(HttpServletRequest request, String body) {
        PowerPaymentQueryResultDTO queryResult = mtPowerPayService.parseNotify(request, body);
        String rechargeNo = queryResult.getRechargeNo();
        if (StringUtils.isBlank(rechargeNo) && StringUtils.isNotBlank(queryResult.getPaymentOrderNo())) {
            MtPowerRecharge byPaymentOrder = mtPowerRechargeMapper.selectByPaymentOrderNo(queryResult.getPaymentOrderNo());
            if (byPaymentOrder != null) {
                rechargeNo = byPaymentOrder.getRechargeNo();
            }
        }

        if (StringUtils.isBlank(rechargeNo)) {
            throw new BusinessException(400, "支付通知缺少充值单号");
        }
        MtPowerRecharge recharge = mtPowerRechargeMapper.selectByRechargeNo(rechargeNo);
        if (recharge == null) {
            throw new BusinessException(404, "充值单不存在");
        }
        syncRechargeStatus(recharge, queryResult);
    }

    private void syncRechargeStatus(MtPowerRecharge recharge, PowerPaymentQueryResultDTO queryResult) {
        if (PowerRechargeStatusEnum.isSuccess(recharge.getPayStatus())) {
            return;
        }

        if (queryResult == null) {
            return;
        }

        Integer payStatus = queryResult.getPayStatus();
        if (payStatus == null) {
            return;
        }
        validatePaymentResultMatchesRecharge(recharge, queryResult);

        if (PowerRechargeStatusEnum.isSuccess(payStatus)) {
            LocalDateTime now = LocalDateTime.now();
            int updated = mtPowerRechargeMapper.markSuccess(
                    recharge.getId(),
                    queryResult.getTransactionId(),
                    queryResult.getRawPayload(),
                    now,
                    now
            );
            if (updated <= 0) {
                return;
            }

            MtPowerAccount account = mtPowerAccountService.addPaidRecharge(recharge.getShopId(), recharge.getPowerAmount());
            int bonusPowerAmount = resolveFirstRechargeBonusAmount(recharge, account);
            if (bonusPowerAmount > 0) {
                createFirstRechargeBonusGrant(recharge, bonusPowerAmount, now);
                account = mtPowerAccountService.addAdminGrant(recharge.getShopId(), bonusPowerAmount);
            }
            salesCommissionService.createSnapshotForPaidRecharge(reloadRecharge(recharge.getId()), mtShopService.findById(recharge.getShopId()));
            log.info("算力充值入账成功，rechargeNo={}, shopId={}, balance={}, bonusPowerAmount={}",
                    recharge.getRechargeNo(), recharge.getShopId(), account.getBalance(), bonusPowerAmount);
            return;
        }

        if (payStatus == PowerRechargeStatusEnum.FAILED.getCode() || payStatus == PowerRechargeStatusEnum.CLOSED.getCode()) {
            mtPowerRechargeMapper.updateStatus(
                    recharge.getId(),
                    payStatus,
                    queryResult.getTransactionId(),
                    queryResult.getRawPayload(),
                    LocalDateTime.now()
            );
        }
    }

    private void validatePaymentResultMatchesRecharge(MtPowerRecharge recharge, PowerPaymentQueryResultDTO queryResult) {
        if (powerProperties.isWechatPayMockEffective()) {
            return;
        }

        if (!StringUtils.equals(queryResult.getPaymentOrderNo(), recharge.getPaymentOrderNo())) {
            throw new BusinessException(400, "支付结果订单号不匹配");
        }
        if (StringUtils.isNotBlank(queryResult.getAppId())
                && !StringUtils.equals(queryResult.getAppId(), powerProperties.getWechatPay().getAppId())) {
            throw new BusinessException(400, "支付结果应用ID不匹配");
        }
        if (StringUtils.isNotBlank(queryResult.getMchId())
                && !StringUtils.equals(queryResult.getMchId(), powerProperties.getWechatPay().getMchId())) {
            throw new BusinessException(400, "支付结果商户号不匹配");
        }

        if (!PowerRechargeStatusEnum.isSuccess(queryResult.getPayStatus())) {
            return;
        }
        if (StringUtils.isBlank(queryResult.getTransactionId())) {
            throw new BusinessException(400, "支付成功结果缺少微信交易号");
        }
        if (StringUtils.isNotBlank(queryResult.getTradeType())
                && !StringUtils.equalsIgnoreCase(queryResult.getTradeType(), "MWEB")) {
            throw new BusinessException(400, "支付结果交易类型不匹配");
        }
        if (queryResult.getTotalAmountFen() == null
                || !queryResult.getTotalAmountFen().equals(recharge.getTotalAmountFen())) {
            throw new BusinessException(400, "支付结果金额不匹配");
        }
        if (StringUtils.isNotBlank(queryResult.getCurrency())
                && !StringUtils.equalsIgnoreCase(queryResult.getCurrency(), "CNY")) {
            throw new BusinessException(400, "支付结果币种不匹配");
        }
        if (StringUtils.isNotBlank(queryResult.getPayerCurrency())
                && !StringUtils.equalsIgnoreCase(queryResult.getPayerCurrency(), "CNY")) {
            throw new BusinessException(400, "支付结果支付币种不匹配");
        }
    }

    private MtPowerRecharge requireRecharge(Long shopId, String rechargeNo) {
        MtPowerRecharge recharge = mtPowerRechargeMapper.selectByRechargeNo(rechargeNo);
        if (recharge == null || !shopId.equals(recharge.getShopId())) {
            throw new BusinessException(404, "充值单不存在");
        }
        return recharge;
    }

    private MtH5PowerSummaryVO.RechargeSummary buildLatestRecharge(MtPowerRecharge recharge) {
        if (recharge == null) {
            return null;
        }
        MtH5PowerSummaryVO.RechargeSummary summary = new MtH5PowerSummaryVO.RechargeSummary();
        summary.setRechargeNo(recharge.getRechargeNo());
        summary.setPowerAmount(recharge.getPowerAmount());
        summary.setTotalAmountFen(recharge.getTotalAmountFen());
        summary.setPayStatus(recharge.getPayStatus());
        summary.setPaidAt(recharge.getPaidAt());
        return summary;
    }

    private List<MtH5PowerSummaryVO.PackageItem> buildRecommendedPackages(
            List<PowerProperties.DiscountTier> pricingTiers,
            boolean firstRecharge,
            boolean firstRechargeBonusEligible
    ) {
        List<MtH5PowerSummaryVO.PackageItem> packages = new ArrayList<>();
        for (Integer powerAmount : powerProperties.getRecharge().getRecommendedPackages(firstRecharge)) {
            int originalAmountFen = calculateOriginalTotalAmount(powerAmount);
            PowerProperties.DiscountTier discountTier = resolveDiscountTier(powerAmount, pricingTiers);
            MtH5PowerSummaryVO.PackageItem item = new MtH5PowerSummaryVO.PackageItem();
            item.setPowerAmount(powerAmount);
            item.setOriginalAmountFen(originalAmountFen);
            item.setTotalAmountFen(discountTier.applyDiscount(originalAmountFen));
            item.setDiscountTag(discountTier.buildDiscountTag(firstRecharge));
            item.setBonusPowerAmount(resolveFirstRechargeBonusAmount(powerAmount, firstRechargeBonusEligible));
            packages.add(item);
        }
        return packages;
    }

    private List<MtH5PowerSummaryVO.PricingTier> buildPricingTiers(List<PowerProperties.DiscountTier> configuredTiers, boolean firstRecharge) {
        List<MtH5PowerSummaryVO.PricingTier> pricingTiers = new ArrayList<>();
        for (PowerProperties.DiscountTier configuredTier : configuredTiers) {
            MtH5PowerSummaryVO.PricingTier pricingTier = new MtH5PowerSummaryVO.PricingTier();
            pricingTier.setMinPowerAmount(configuredTier.getMinPowerAmount());
            pricingTier.setMaxPowerAmount(configuredTier.getMaxPowerAmount());
            pricingTier.setPayRateBasisPoints(configuredTier.getPayRateBasisPoints());
            pricingTier.setDiscountTag(configuredTier.buildDiscountTag(firstRecharge));
            pricingTiers.add(pricingTier);
        }
        return pricingTiers;
    }

    private MtH5PowerRechargeResultVO buildRechargeResultVO(MtPowerRecharge recharge) {
        MtH5PowerRechargeResultVO vo = new MtH5PowerRechargeResultVO();
        vo.setPowerAmount(recharge.getPowerAmount());
        vo.setPayStatus(recharge.getPayStatus());
        return vo;
    }

    private void validatePowerAmount(Integer powerAmount) {
        if (powerAmount == null) {
            throw new BusinessException(400, "购买算力不能为空");
        }
        if (powerAmount < powerProperties.getMinPurchaseAmount()) {
            throw new BusinessException(400, "购买算力不能低于最小限制");
        }
        if (powerAmount > powerProperties.getMaxPurchaseAmount()) {
            throw new BusinessException(400, "购买算力超过最大限制");
        }
        if (powerAmount % powerProperties.getUnitPower() != 0) {
            throw new BusinessException(400, "购买算力必须为价格单位的整数倍");
        }
    }

    private MtH5PowerSummaryVO.FirstRechargeOffer buildFirstRechargeOffer(
            MtShop shop,
            MtPowerAccount account,
            MtPowerRecharge latestRecharge,
            LocalDateTime now,
            int extraDays
    ) {
        MtH5PowerSummaryVO.FirstRechargeOffer offer = new MtH5PowerSummaryVO.FirstRechargeOffer();
        offer.setEligible(false);
        if (shop == null || shop.getCreateTime() == null) {
            return offer;
        }

        LocalDateTime expireAt = shop.getCreateTime().plusDays(powerProperties.getRecharge().getFirstRechargeValidDays() + extraDays);
        offer.setExpireAt(expireAt);
        if (hasPaidRecharge(account)) {
            return offer;
        }

        long remainingSeconds = Duration.between(now, expireAt).getSeconds();
        if (remainingSeconds <= 0) {
            return offer;
        }

        offer.setEligible(true);
        offer.setRemainingSeconds(remainingSeconds);
        return offer;
    }

    private boolean hasPaidRecharge(MtPowerAccount account) {
        return account != null && account.getTotalRecharge() != null && account.getTotalRecharge() > 0;
    }

    private int calculateOriginalTotalAmount(Integer powerAmount) {
        return powerProperties.calculateOriginalRechargeAmountFen(powerAmount);
    }

    private int calculateConsumed(int totalAmount, int remainingAmount) {
        return Math.max(totalAmount - remainingAmount, 0);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int resolveFirstRechargeBonusAmount(MtPowerRecharge recharge, MtPowerAccount accountAfterPaidRecharge) {
        if (recharge == null || accountAfterPaidRecharge == null
                || !Integer.valueOf(defaultInt(recharge.getPowerAmount())).equals(accountAfterPaidRecharge.getTotalRecharge())) {
            return 0;
        }
        return resolveFirstRechargeBonusAmount(recharge.getPowerAmount(), true);
    }

    private int resolveFirstRechargeBonusAmount(Integer powerAmount, boolean firstRechargeBonusEligible) {
        if (!firstRechargeBonusEligible || powerAmount == null || powerAmount < FIRST_RECHARGE_BONUS_THRESHOLD) {
            return 0;
        }
        return FIRST_RECHARGE_BONUS_AMOUNT;
    }

    private void createFirstRechargeBonusGrant(MtPowerRecharge recharge, int bonusPowerAmount, LocalDateTime now) {
        MtPowerGrant grant = new MtPowerGrant();
        grant.setGrantNo(buildGrantNo());
        grant.setShopId(recharge.getShopId());
        grant.setPowerAmount(bonusPowerAmount);
        grant.setGrantReason(FIRST_RECHARGE_BONUS_GRANT_REASON);
        grant.setRemark("充值单 " + recharge.getRechargeNo() + " 首充满1000万赠送");
        grant.setOperatorUserId(FIRST_RECHARGE_BONUS_OPERATOR);
        grant.setOperatorUsername(FIRST_RECHARGE_BONUS_OPERATOR);
        grant.setStatus(GRANT_STATUS_SUCCESS);
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        mtPowerGrantMapper.insert(grant);
    }

    private String generateRechargeNo() {
        return "PR" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String generatePaymentOrderNo() {
        return "PWP" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String buildGrantNo() {
        return "PG" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private RechargePricingContext buildRechargePricingContext(MtShop shop,
                                                               MtPowerAccount account,
                                                               MtPowerRecharge latestRecharge,
                                                               LocalDateTime now,
                                                               String redemptionCode,
                                                               boolean requireRedemptionCode) {
        boolean hasRedemptionCode = StringUtils.isNotBlank(redemptionCode);
        if (requireRedemptionCode && !hasRedemptionCode) {
            throw new BusinessException("兑换码不能为空");
        }
        if (!hasRedemptionCode) {
            MtH5PowerSummaryVO.FirstRechargeOffer firstRechargeOffer = buildFirstRechargeOffer(shop, account, latestRecharge, now, 0);
            boolean firstRecharge = Boolean.TRUE.equals(firstRechargeOffer.getEligible());
            return new RechargePricingContext(
                    null,
                    firstRecharge,
                    firstRechargeOffer,
                    powerProperties.getRecharge().getDiscountTiers(firstRecharge),
                    firstRecharge ? "FIRST_RECHARGE" : "REGULAR",
                    null,
                    null
            );
        }

        ResolvedRedemptionCode resolvedRedemptionCode = salesRedemptionCodeService.requireResolvedCode(redemptionCode);
        MtH5PowerSummaryVO.FirstRechargeOffer firstRechargeOffer = buildFirstRechargeOffer(shop, account, latestRecharge, now, 3);
        boolean firstRecharge = Boolean.TRUE.equals(firstRechargeOffer.getEligible());
        MtPowerRecharge firstSalesRecharge = mtPowerRechargeMapper.selectFirstSalesRechargeByShopId(shop.getId());
        LocalDateTime commissionWindowStartAt = firstSalesRecharge == null
                ? now
                : (firstSalesRecharge.getCommissionWindowStartAt() == null ? firstSalesRecharge.getCreateTime() : firstSalesRecharge.getCommissionWindowStartAt());
        Integer defaultCommissionRatio = now.isBefore(commissionWindowStartAt.plusDays(90)) ? 30 : 15;
        return new RechargePricingContext(
                resolvedRedemptionCode,
                firstRecharge,
                firstRechargeOffer,
                firstRecharge ? powerProperties.getRecharge().getDiscountTiers(true) : buildRedemptionRegularTiers(),
                firstRecharge ? "REDEMPTION_FIRST_RECHARGE" : "REDEMPTION_REGULAR",
                commissionWindowStartAt,
                defaultCommissionRatio
        );
    }

    private List<PowerProperties.DiscountTier> buildRedemptionRegularTiers() {
        List<PowerProperties.DiscountTier> tiers = new ArrayList<>();
        tiers.add(buildDiscountTier(1_000_000, 9_990_000, BigDecimal.ONE));
        tiers.add(buildDiscountTier(10_000_000, 99_990_000, new BigDecimal("0.80")));
        tiers.add(buildDiscountTier(100_000_000, null, new BigDecimal("0.70")));
        return tiers;
    }

    private PowerProperties.DiscountTier buildDiscountTier(Integer minPowerAmount, Integer maxPowerAmount, BigDecimal payRate) {
        PowerProperties.DiscountTier tier = new PowerProperties.DiscountTier();
        tier.setMinPowerAmount(minPowerAmount);
        tier.setMaxPowerAmount(maxPowerAmount);
        tier.setPayRate(payRate);
        return tier;
    }

    private PowerProperties.DiscountTier resolveDiscountTier(Integer powerAmount, List<PowerProperties.DiscountTier> tiers) {
        return tiers.stream()
                .filter(tier -> tier.matches(powerAmount))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未匹配到充值折扣档位"));
    }

    private MtPowerRecharge reloadRecharge(Long rechargeId) {
        return rechargeId == null ? null : mtPowerRechargeMapper.selectById(rechargeId);
    }

    @RequiredArgsConstructor
    private static class RechargePricingContext {

        private final ResolvedRedemptionCode resolvedRedemptionCode;
        private final boolean firstRecharge;
        private final MtH5PowerSummaryVO.FirstRechargeOffer firstRechargeOffer;
        private final List<PowerProperties.DiscountTier> pricingTiers;
        private final String discountContextType;
        private final LocalDateTime commissionWindowStartAt;
        private final Integer defaultCommissionRatio;

        public ResolvedRedemptionCode getResolvedRedemptionCode() {
            return resolvedRedemptionCode;
        }

        public boolean isFirstRecharge() {
            return firstRecharge;
        }

        public MtH5PowerSummaryVO.FirstRechargeOffer getFirstRechargeOffer() {
            return firstRechargeOffer;
        }

        public List<PowerProperties.DiscountTier> getPricingTiers() {
            return pricingTiers;
        }

        public String getDiscountContextType() {
            return discountContextType;
        }

        public LocalDateTime getCommissionWindowStartAt() {
            return commissionWindowStartAt;
        }

        public Integer getDefaultCommissionRatio() {
            return defaultCommissionRatio;
        }
    }
}
