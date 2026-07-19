package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.client.MtImConfigurationClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
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
    private final MtOrderPushControlService mtOrderPushControlService;
    private final MtImConfigurationClient mtImConfigurationClient;
    private final ShopOperationSettingsService shopOperationSettingsService;

    public void enqueuePostAuthorizationTasks(Long shopId, String accessToken) {
        if (shopId == null || StringUtils.isBlank(accessToken)) {
            return;
        }
        String authEventKey = buildAuthEventKey(shopId, accessToken);
        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(shopId);
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_BLOCK_ORDER_PUSH, payload);
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_BACKFILL_POI_ID, payload);
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_SYNC_RIGHTS, payload);
        enqueueSafely(shopId, authEventKey, MtAsyncTaskTypeEnum.AUTH_CONFIGURE_IM, payload);
    }

    public void executeAuthSyncRightsTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        mtShopRightService.syncRightsFromBuyInfo(context.shop(), context.token().getAccessToken());
    }

    public void executeAuthBackfillPoiIdTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        mtShopPoiIdBackfillService.tryBackfillAfterAuthorization(
                context.shop(), context.token().getAccessToken());
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

    public void executeAuthConfigureImTask(MtAuthPostProcessTaskPayload payload) {
        ShopContext context = loadContext(payload);
        Long shopId = context.shop().getId();
        MtImConfigurationClient.ImConfigurationResult imResult =
                mtImConfigurationClient.enablePoiIm(context.token().getAccessToken());
        logConfigurationResult(shopId, imResult);
        if (!imResult.success()) {
            keepLocalImAutoReplyDisabled(shopId, imResult.stage());
            throw new IllegalStateException("AUTH_CONFIGURE_IM_FAILED:" + imResult.stage() + ":" + imResult.errorCode());
        }

        MtImConfigurationClient.ImConfigurationResult robotResult =
                mtImConfigurationClient.disableAllSmartReplies(context.token().getAccessToken());
        logConfigurationResult(shopId, robotResult);
        if (!robotResult.success()) {
            keepLocalImAutoReplyDisabled(shopId, robotResult.stage());
            throw new IllegalStateException("AUTH_CONFIGURE_IM_FAILED:" + robotResult.stage() + ":" + robotResult.errorCode());
        }

        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(true);
        shopOperationSettingsService.updateSettings(
                shopId,
                param,
                ShopOperationSettingsService.CHANGE_SOURCE_AUTH,
                "AUTH_IM_CONFIGURED"
        );
    }

    private void keepLocalImAutoReplyDisabled(Long shopId, String failedStage) {
        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(false);
        shopOperationSettingsService.updateSettings(
                shopId,
                param,
                ShopOperationSettingsService.CHANGE_SOURCE_AUTH,
                "AUTH_IM_CONFIG_FAILED:" + failedStage
        );
    }

    private void logConfigurationResult(Long shopId, MtImConfigurationClient.ImConfigurationResult result) {
        if (result.success()) {
            log.info("授权后门店IM配置完成，shopId={}, stage={}, summary={}",
                    shopId, result.stage(), result.responseSummary());
            return;
        }
        log.warn("授权后门店IM配置失败，shopId={}, stage={}, errorCode={}, summary={}",
                shopId, result.stage(), result.errorCode(), result.responseSummary());
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
            MtAuthPostProcessTaskPayload payload
    ) {
        try {
            enqueue(shopId, authEventKey, taskType, payload);
        } catch (RuntimeException e) {
            log.error("授权后异步任务入队失败，taskType={}, shopId={}", taskType.name(), shopId, e);
        }
    }

    private int resolveMaxRetryCount(MtAsyncTaskTypeEnum taskType) {
        return switch (taskType) {
            case AUTH_BLOCK_ORDER_PUSH -> 0;
            case AUTH_SYNC_RIGHTS -> mtProperties.getAsyncTask().getAuthSyncRights().getMaxRetryCount();
            case AUTH_BACKFILL_POI_ID -> mtProperties.getAsyncTask().getAuthBackfillPoiId().getMaxRetryCount();
            case AUTH_CONFIGURE_IM -> mtProperties.getAsyncTask().getAuthConfigureIm().getMaxRetryCount();
            default -> throw new IllegalArgumentException("不支持的授权后任务类型: " + taskType);
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
