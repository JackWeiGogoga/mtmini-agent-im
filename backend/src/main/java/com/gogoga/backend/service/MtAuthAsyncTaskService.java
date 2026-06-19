package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 授权后数据库异步任务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAuthAsyncTaskService {

    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtProperties mtProperties;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtShopService mtShopService;
    private final MtShopRightService mtShopRightService;
    private final MtShopPoiIdBackfillService mtShopPoiIdBackfillService;
    private final MtHistoricalOrderBackfillService mtHistoricalOrderBackfillService;
    private final MtOrderPushControlService mtOrderPushControlService;

    public void enqueuePostAuthorizationTasks(Long shopId, String accessToken, boolean newlyCreatedToken) {
        if (shopId == null || StringUtils.isBlank(accessToken)) {
            return;
        }
        String authEventKey = buildAuthEventKey(shopId, accessToken);
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(shopId);
        if (newlyCreatedToken) {
            enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH, payload, false);
        }
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID, payload, false);
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS, payload, false);
        log.info("跳过授权后历史订单补数任务入队，原因=新评论回复服务不依赖订单历史补数, shopId={}", shopId);
    }

    public void executeAuthSyncRightsTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        mtShopRightService.syncRightsFromBuyInfo(context.shop(), context.token().getAccessToken());
    }

    public void executeAuthBackfillPoiIdTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        mtShopPoiIdBackfillService.tryBackfillAfterAuthorization(context.shop(), context.token().getAccessToken());
    }

    public void executeAuthHistoricalOrderBackfillTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        mtHistoricalOrderBackfillService.backfillAfterAuthorization(context.shop(), context.token().getAccessToken());
    }

    public void executeAuthBlockOrderPushTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        MtOrderPushControlService.OrderPushSyncResult result =
                mtOrderPushControlService.blockOrderPush(context.token(), "INITIAL_AUTH");
        if (!result.success()) {
            log.warn("授权后异步停止订单推送失败，shopId={}, opBizCode={}, error={}",
                    context.shop().getId(), context.token().getOpBizCode(), result.errorMessage());
            return;
        }
        log.info("授权后异步停止订单推送成功，shopId={}, opBizCode={}",
                context.shop().getId(), context.token().getOpBizCode());
    }

    private void enqueue(Long shopId, String authEventKey, MtAsyncTaskTypeEnum taskType, MtAuthPostProcessTaskPayload payload) {
        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                taskType,
                authEventKey,
                String.valueOf(shopId),
                payload,
                LocalDateTime.now(),
                resolveMaxRetryCount(taskType)
        );
        log.info("授权后异步任务入队完成，taskType={}, shopId={}, taskId={}, created={}",
                taskType.name(), shopId, enqueueResult.getTaskId(), enqueueResult.isCreated());
    }

    private void enqueueSafely(
            Long shopId,
            String authEventKey,
            MtAsyncTaskTypeEnum taskType,
            MtAuthPostProcessTaskPayload payload,
            boolean revertHistoricalBackfillStateOnFailure
    ) {
        try {
            enqueue(shopId, authEventKey, taskType, payload);
        } catch (RuntimeException e) {
            if (revertHistoricalBackfillStateOnFailure) {
                mtShopService.updateHistoricalOrderBackfillState(
                        shopId,
                        MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_FAILED,
                        LocalDateTime.now()
                );
            }
            log.error("授权后异步任务入队失败，taskType={}, shopId={}", taskType.name(), shopId, e);
        }
    }

    private int resolveMaxRetryCount(MtAsyncTaskTypeEnum taskType) {
        return switch (taskType) {
            case AUTH_BLOCK_ORDER_PUSH -> 0;
            case AUTH_SYNC_RIGHTS -> mtProperties.getAsyncTask().getAuthSyncRights().getMaxRetryCount();
            case AUTH_BACKFILL_POI_ID -> mtProperties.getAsyncTask().getAuthBackfillPoiId().getMaxRetryCount();
            case AUTH_HISTORICAL_ORDER_BACKFILL -> mtProperties.getAsyncTask().getAuthHistoricalOrderBackfill().getMaxRetryCount();
            default -> 0;
        };
    }

    private ShopContext loadContext(MtAuthPostProcessTaskPayload payload) {
        if (payload == null || payload.getShopId() == null) {
            throw new IllegalArgumentException("AUTH_TASK_PAYLOAD_INVALID");
        }
        MtShop shop = mtShopService.findById(payload.getShopId());
        if (shop == null) {
            throw new IllegalArgumentException("SHOP_NOT_FOUND");
        }
        MtAccessToken token = mtAccessTokenService.findByShopId(payload.getShopId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            throw new IllegalStateException("ACCESS_TOKEN_MISSING");
        }
        return new ShopContext(shop, token);
    }

    private String buildAuthEventKey(Long shopId, String accessToken) {
        return shopId + "#" + UUID.nameUUIDFromBytes(accessToken.getBytes(StandardCharsets.UTF_8));
    }

    private record ShopContext(MtShop shop, MtAccessToken token) {
    }
}
