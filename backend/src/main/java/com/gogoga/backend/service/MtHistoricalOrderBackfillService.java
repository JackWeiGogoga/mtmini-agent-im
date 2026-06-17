package com.gogoga.backend.service;

import com.gogoga.backend.client.MtOrderOpenApiClient;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 近期历史订单补数服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtHistoricalOrderBackfillService {

    // 美团订单列表接口近 5 天可查，当前补数从昨天开始回看，因此最多只能回看 4 天。
    static final int AUTH_LOOKBACK_DAYS = 4;
    static final int ORDER_STATUS_COMPLETED = 8;
    static final int PAGE_NO_FIRST = 1;
    static final int PAGE_SIZE_MAX = 20;

    public static final String TRIGGER_MANUAL_UNLOCK = "MANUAL_UNLOCK";
    public static final String TRIGGER_ENABLE_PROMOTION = "ENABLE_PROMOTION";
    public static final String TRIGGER_AUTH_ASYNC_TASK = "AUTH_ASYNC_TASK";
    public static final String UNLOCK_INSUFFICIENT_MESSAGE = "数据不足，功能开启5天后解锁！";
    public static final String UNLOCK_SUCCESS_MESSAGE = "AI识别高峰期已解锁";

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_INT_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String UNAVAILABLE_REASON_CONTEXT_INCOMPLETE = "CONTEXT_INCOMPLETE";
    private static final String UNAVAILABLE_REASON_ALREADY_PROCESSING = "ALREADY_PROCESSING";
    private static final String UNAVAILABLE_REASON_BACKFILL_FAILED = "BACKFILL_FAILED";
    private static final String UNAVAILABLE_REASON_ALREADY_BACKFILLED = "ALREADY_BACKFILLED";

    private final MtOrderOpenApiClient mtOrderOpenApiClient;
    private final MtOrderService mtOrderService;
    private final MtShopService mtShopService;
    private final ShopPeakWindowProfileService shopPeakWindowProfileService;

    public void backfillAfterAuthorization(MtShop shop, String accessToken) {
        BackfillUnlockResult result = backfillRecentOrdersAndRefreshPeakProfile(
                shop,
                accessToken,
                TRIGGER_AUTH_ASYNC_TASK,
                true
        );
        if (!result.completed()) {
            throw result.failure() == null
                    ? new IllegalStateException("HISTORICAL_ORDER_BACKFILL_FAILED")
                    : result.failure();
        }
    }

    public BackfillUnlockResult tryBackfillRecentOrdersAndRefreshPeakProfile(
            MtShop shop,
            String accessToken,
            String trigger
    ) {
        if (shop == null || shop.getId() == null || StringUtils.isBlank(accessToken)) {
            log.info("跳过近期历史订单补数，原因=上下文不完整, trigger={}, shopId={}, accessTokenPresent={}",
                    trigger, shop == null ? null : shop.getId(), StringUtils.isNotBlank(accessToken));
            return BackfillUnlockResult.skipped(false, UNAVAILABLE_REASON_CONTEXT_INCOMPLETE, UNLOCK_INSUFFICIENT_MESSAGE);
        }
        if (isPeakProfileUsable(shop.getId())) {
            return BackfillUnlockResult.skipped(true, null, UNLOCK_SUCCESS_MESSAGE);
        }
        if (StringUtils.equals(shop.getHistoricalOrderBackfillStatus(), MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS)) {
            return buildResultFromCurrentProfile(shop.getId(), false, UNAVAILABLE_REASON_ALREADY_BACKFILLED);
        }
        if (!tryClaimBackfillProcessing(shop, trigger)) {
            MtShop latestShop = mtShopService.findById(shop.getId());
            if (latestShop != null
                    && StringUtils.equals(latestShop.getHistoricalOrderBackfillStatus(), MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS)) {
                return buildResultFromCurrentProfile(shop.getId(), false, UNAVAILABLE_REASON_ALREADY_BACKFILLED);
            }
            log.info("跳过近期历史订单补数，原因=门店补数正在处理或状态不可重试, trigger={}, shopId={}, status={}",
                    trigger, shop.getId(), latestShop == null ? shop.getHistoricalOrderBackfillStatus() : latestShop.getHistoricalOrderBackfillStatus());
            return BackfillUnlockResult.skipped(false, UNAVAILABLE_REASON_ALREADY_PROCESSING, UNLOCK_INSUFFICIENT_MESSAGE);
        }
        try {
            return backfillRecentOrdersAndRefreshPeakProfile(shop, accessToken, trigger, false);
        } catch (RuntimeException e) {
            log.warn("近期历史订单补数失败，trigger={}, shopId={}", trigger, shop.getId(), e);
            return BackfillUnlockResult.failed(UNAVAILABLE_REASON_BACKFILL_FAILED, UNLOCK_INSUFFICIENT_MESSAGE, e);
        }
    }

    private boolean tryClaimBackfillProcessing(MtShop shop, String trigger) {
        try {
            return mtShopService.tryMarkHistoricalOrderBackfillProcessingIfRetryable(shop.getId());
        } catch (TransientDataAccessException e) {
            log.warn("跳过近期历史订单补数，原因=门店补数状态锁冲突, trigger={}, shopId={}, error={}",
                    trigger, shop.getId(), e.getMessage());
            return false;
        }
    }

    private BackfillUnlockResult backfillRecentOrdersAndRefreshPeakProfile(
            MtShop shop,
            String accessToken,
            String trigger,
            boolean rethrowFailure
    ) {
        if (shop == null || shop.getId() == null || StringUtils.isBlank(accessToken)) {
            log.info("跳过近期历史订单补数，原因=上下文不完整, trigger={}, shopId={}, accessTokenPresent={}",
                    trigger, shop == null ? null : shop.getId(), StringUtils.isNotBlank(accessToken));
            return BackfillUnlockResult.skipped(false, UNAVAILABLE_REASON_CONTEXT_INCOMPLETE, UNLOCK_INSUFFICIENT_MESSAGE);
        }

        Set<String> orderIds = collectRecentCompletedOrderIds(shop.getId(), accessToken);
        int syncedOrderCount = 0;
        try {
            for (String orderId : orderIds) {
                Optional<WmoperOrderQueryOrderDetailResponse> detail = mtOrderOpenApiClient.queryOrderDetail(shop.getId(), accessToken, orderId);
                if (detail.isEmpty()) {
                    continue;
                }
                TransientDatabaseRetrySupport.run(
                        "RECENT_HISTORICAL_ORDER_BACKFILL",
                        orderId,
                        () -> mtOrderService.syncHistoricalOrderDetail(shop, detail.get())
                );
                syncedOrderCount++;
            }

            LocalDateTime now = LocalDateTime.now();
            shopPeakWindowProfileService.refreshProfilesForShop(shop.getId(), now);
            boolean autoTimeModeAvailable = isPeakProfileUsable(shop.getId());
            mtShopService.updateHistoricalOrderBackfillState(
                    shop.getId(),
                    MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS,
                    now
            );
            log.info("近期历史订单补数完成，trigger={}, shopId={}, lookbackDays={}, orderIdCount={}, syncedOrderCount={}, autoTimeModeAvailable={}",
                    trigger, shop.getId(), AUTH_LOOKBACK_DAYS, orderIds.size(), syncedOrderCount, autoTimeModeAvailable);
            return BackfillUnlockResult.completed(
                    autoTimeModeAvailable,
                    autoTimeModeAvailable ? null : resolveCurrentUnavailableReason(shop.getId()),
                    autoTimeModeAvailable ? UNLOCK_SUCCESS_MESSAGE : UNLOCK_INSUFFICIENT_MESSAGE,
                    orderIds.size(),
                    syncedOrderCount
            );
        } catch (RuntimeException e) {
            LocalDateTime now = LocalDateTime.now();
            mtShopService.updateHistoricalOrderBackfillState(
                    shop.getId(),
                    MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_FAILED,
                    now
            );
            log.error("近期历史订单补数失败，trigger={}, shopId={}, lookbackDays={}, orderIdCount={}, syncedOrderCount={}",
                    trigger, shop.getId(), AUTH_LOOKBACK_DAYS, orderIds.size(), syncedOrderCount, e);
            if (rethrowFailure) {
                throw e;
            }
            return BackfillUnlockResult.failed(UNAVAILABLE_REASON_BACKFILL_FAILED, UNLOCK_INSUFFICIENT_MESSAGE, e);
        }
    }

    private BackfillUnlockResult buildResultFromCurrentProfile(Long shopId, boolean completed, String fallbackReason) {
        ShopPeakWindowProfileLookupResult lookupResult = shopPeakWindowProfileService.lookup(shopId, LocalDateTime.now());
        boolean autoTimeModeAvailable = isLookupUsable(lookupResult);
        String unavailableReason = autoTimeModeAvailable
                ? null
                : StringUtils.defaultIfBlank(lookupResult == null ? null : lookupResult.getUnavailableReason(), fallbackReason);
        return completed
                ? BackfillUnlockResult.completed(autoTimeModeAvailable, unavailableReason,
                autoTimeModeAvailable ? UNLOCK_SUCCESS_MESSAGE : UNLOCK_INSUFFICIENT_MESSAGE, 0, 0)
                : BackfillUnlockResult.skipped(autoTimeModeAvailable, unavailableReason,
                autoTimeModeAvailable ? UNLOCK_SUCCESS_MESSAGE : UNLOCK_INSUFFICIENT_MESSAGE);
    }

    private boolean isPeakProfileUsable(Long shopId) {
        return isLookupUsable(shopPeakWindowProfileService.lookup(shopId, LocalDateTime.now()));
    }

    private boolean isLookupUsable(ShopPeakWindowProfileLookupResult lookupResult) {
        return lookupResult != null
                && StringUtils.equals(lookupResult.getProfileStatus(), ShopPeakWindowProfileLookupResult.STATUS_USABLE);
    }

    private String resolveCurrentUnavailableReason(Long shopId) {
        ShopPeakWindowProfileLookupResult lookupResult = shopPeakWindowProfileService.lookup(shopId, LocalDateTime.now());
        return lookupResult == null ? null : lookupResult.getUnavailableReason();
    }

    private Set<String> collectRecentCompletedOrderIds(Long shopId, String accessToken) {
        Set<String> orderIds = new LinkedHashSet<>();
        for (int offset = 1; offset <= AUTH_LOOKBACK_DAYS; offset++) {
            LocalDate targetDay = LocalDate.now(DEFAULT_ZONE_ID).minusDays(offset);
            if (mtOrderService.hasOrderSentOnDay(shopId, targetDay)) {
                log.info("跳过近期历史订单补数列表查询，原因=当日已有下单数据, shopId={}, day={}",
                        shopId, targetDay);
                continue;
            }
            String dayInt = targetDay.format(DAY_INT_FORMATTER);
            int pageNo = PAGE_NO_FIRST;
            while (true) {
                MtOrderOpenApiClient.OrderListQueryResult result = mtOrderOpenApiClient.queryOrderIds(
                        shopId,
                        accessToken,
                        dayInt,
                        ORDER_STATUS_COMPLETED,
                        pageNo,
                        PAGE_SIZE_MAX
                );
                orderIds.addAll(result.orderIds());
                if (!result.hasMore() || result.orderIds().isEmpty()) {
                    break;
                }
                pageNo++;
            }
        }
        return orderIds;
    }

    public record BackfillUnlockResult(
            boolean completed,
            boolean autoTimeModeAvailable,
            String unavailableReason,
            String message,
            int orderIdCount,
            int syncedOrderCount,
            RuntimeException failure
    ) {
        public static BackfillUnlockResult completed(
                boolean autoTimeModeAvailable,
                String unavailableReason,
                String message,
                int orderIdCount,
                int syncedOrderCount
        ) {
            return new BackfillUnlockResult(true, autoTimeModeAvailable, unavailableReason, message, orderIdCount, syncedOrderCount, null);
        }

        public static BackfillUnlockResult skipped(boolean autoTimeModeAvailable, String unavailableReason, String message) {
            return new BackfillUnlockResult(false, autoTimeModeAvailable, unavailableReason, message, 0, 0, null);
        }

        public static BackfillUnlockResult failed(String unavailableReason, String message, RuntimeException failure) {
            return new BackfillUnlockResult(false, false, unavailableReason, message, 0, 0, failure);
        }
    }
}
