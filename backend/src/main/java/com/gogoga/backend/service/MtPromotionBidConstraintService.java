package com.gogoga.backend.service;

import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionBidConstraint;
import com.gogoga.backend.mapper.MtPromotionBidConstraintMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 门店推广出价约束服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPromotionBidConstraintService {

    public static final BigDecimal GLOBAL_MAX_BID_AMOUNT = new BigDecimal("4.00");
    public static final String SOURCE_MEITUAN_ERROR = "MEITUAN_ERROR";

    private static final int EXPIRE_DAYS = 7;
    private static final Pattern MAX_BID_MESSAGE_PATTERN = Pattern.compile("出价不能高于\\s*([0-9]+(?:\\.[0-9]+)?)\\s*元");

    private final MtPromotionBidConstraintMapper mtPromotionBidConstraintMapper;

    public BigDecimal resolveEffectiveMaxBidAmount(Long shopId, BigDecimal globalMaxBidAmount, LocalDateTime now) {
        BigDecimal normalizedGlobalMax = normalizeBidAmount(globalMaxBidAmount);
        if (shopId == null || normalizedGlobalMax == null || mtPromotionBidConstraintMapper == null) {
            return normalizedGlobalMax;
        }
        MtPromotionBidConstraint constraint = mtPromotionBidConstraintMapper.selectActiveByShopId(
                shopId,
                now == null ? LocalDateTime.now() : now
        );
        BigDecimal shopMax = constraint == null ? null : normalizeBidAmount(constraint.getMaxBidAmount());
        if (shopMax == null || shopMax.signum() <= 0) {
            return normalizedGlobalMax;
        }
        return shopMax.compareTo(normalizedGlobalMax) < 0 ? shopMax : normalizedGlobalMax;
    }

    public Optional<BigDecimal> observeMaxBidLimitFromFailure(
            MtPromotionAutoBidCandidate candidate,
            PromotionBidOpenApiResult updateResult,
            LocalDateTime observedAt
    ) {
        if (candidate == null || candidate.getShopId() == null || updateResult == null) {
            return Optional.empty();
        }
        String rawMessage = buildRawMessage(updateResult);
        Optional<BigDecimal> parsedMaxBidAmount = parseMaxBidAmount(rawMessage);
        if (parsedMaxBidAmount.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = observedAt == null ? LocalDateTime.now() : observedAt;
        MtPromotionBidConstraint constraint = new MtPromotionBidConstraint();
        constraint.setShopId(candidate.getShopId());
        constraint.setPoiId(candidate.getPoiId());
        constraint.setMaxBidAmount(parsedMaxBidAmount.get());
        constraint.setSource(SOURCE_MEITUAN_ERROR);
        constraint.setRawMessage(StringUtils.left(rawMessage, 1000));
        constraint.setObservedAt(now);
        constraint.setExpireAt(now.plusDays(EXPIRE_DAYS));
        constraint.setCreateTime(now);
        constraint.setUpdateTime(now);
        mtPromotionBidConstraintMapper.upsert(constraint);
        log.info("记录门店推广出价约束，shopId={}, poiId={}, maxBidAmount={}, expireAt={}",
                candidate.getShopId(), candidate.getPoiId(), constraint.getMaxBidAmount(), constraint.getExpireAt());
        return parsedMaxBidAmount;
    }

    public Optional<BigDecimal> parseMaxBidAmount(String message) {
        if (StringUtils.isBlank(message)) {
            return Optional.empty();
        }
        Matcher matcher = MAX_BID_MESSAGE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            BigDecimal maxBidAmount = normalizeBidAmount(new BigDecimal(matcher.group(1)));
            if (maxBidAmount == null || maxBidAmount.signum() <= 0) {
                return Optional.empty();
            }
            return Optional.of(maxBidAmount);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String buildRawMessage(PromotionBidOpenApiResult updateResult) {
        String errorMsg = StringUtils.defaultString(updateResult.getErrorMsg());
        String payloadJson = StringUtils.defaultString(updateResult.getPayloadJson());
        return StringUtils.trim(String.join("\n", errorMsg, payloadJson));
    }

    private BigDecimal normalizeBidAmount(BigDecimal bidAmount) {
        if (bidAmount == null) {
            return null;
        }
        return bidAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
