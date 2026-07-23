package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 商家生命周期短信场景的事件创建入口。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MerchantLifecycleSmsService {

    private static final Set<Integer> SUPPORTED_APP_DAYS = Set.of(7, 30, 90, 360);
    private static final LocalTime PURCHASE_GUIDE_SEND_TIME = LocalTime.of(10, 0);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final SmsNotificationEventService eventService;
    private final SmsProperties smsProperties;

    public SmsNotificationEventEnqueueResult enqueuePurchaseGuide(
            Long shopId,
            String orderId,
            Integer days,
            Instant occurredAt
    ) {
        if (days == null || !SUPPORTED_APP_DAYS.contains(days)) {
            log.warn("购买引导短信事件跳过，应用订单规格不受模板支持, shopId={}, orderId={}, days={}",
                    shopId, orderId, days);
            return null;
        }
        SmsProperties.BusinessConfig config = smsProperties.getBusiness();
        return buildAndPublishFailOpen(
                SmsNotificationSceneEnum.PURCHASE_GUIDE,
                orderId,
                shopId,
                orderId,
                config.getPurchaseGuideTemplateId(),
                () -> new SmsNotificationEventEnqueueRequest(
                        SmsNotificationSceneEnum.PURCHASE_GUIDE,
                        orderId,
                        shopId,
                        orderId,
                        config.getPurchaseGuideTemplateId(),
                        List.of(config.getApplicationName(), days + "天"),
                        Map.of("days", days),
                        purchaseGuideAvailableAt(occurredAt, config.getZoneId())
                )
        );
    }

    private LocalDateTime purchaseGuideAvailableAt(Instant occurredAt, String zoneId) {
        return occurredAt.atZone(ZoneId.of(zoneId))
                .toLocalDate()
                .plusDays(1)
                .atTime(PURCHASE_GUIDE_SEND_TIME);
    }

    public SmsNotificationEventEnqueueResult enqueuePowerExhausted(
            Long shopId,
            String consumeNo,
            LocalDateTime now
    ) {
        SmsProperties.BusinessConfig config = smsProperties.getBusiness();
        return buildAndPublishFailOpen(
                SmsNotificationSceneEnum.POWER_EXHAUSTED,
                consumeNo,
                shopId,
                null,
                config.getPowerExhaustedTemplateId(),
                () -> new SmsNotificationEventEnqueueRequest(
                        SmsNotificationSceneEnum.POWER_EXHAUSTED,
                        consumeNo,
                        shopId,
                        null,
                        config.getPowerExhaustedTemplateId(),
                        List.of(config.getApplicationName()),
                        Map.of("consumeNo", consumeNo),
                        now
                )
        );
    }

    public SmsNotificationEventEnqueueResult enqueueAppExpired(
            Long shopId,
            LocalDateTime expectedExpireAt,
            LocalDateTime scheduledAt
    ) {
        SmsProperties.BusinessConfig config = smsProperties.getBusiness();
        String bizKey = expectedExpireAt == null ? null : shopId + "#" + expectedExpireAt;
        return buildAndPublishFailOpen(
                SmsNotificationSceneEnum.APP_EXPIRED,
                bizKey,
                shopId,
                null,
                config.getAppExpiredTemplateId(),
                () -> {
                    String expireAtText = expectedExpireAt.toString();
                    return new SmsNotificationEventEnqueueRequest(
                            SmsNotificationSceneEnum.APP_EXPIRED,
                            shopId + "#" + expireAtText,
                            shopId,
                            null,
                            config.getAppExpiredTemplateId(),
                            List.of(config.getApplicationName()),
                            Map.of("expectedExpireAt", expireAtText),
                            scheduledAt
                    );
                }
        );
    }

    private SmsNotificationEventEnqueueResult buildAndPublishFailOpen(
            SmsNotificationSceneEnum scene,
            String bizKey,
            Long shopId,
            String orderId,
            String templateId,
            Supplier<SmsNotificationEventEnqueueRequest> requestSupplier
    ) {
        if (!smsProperties.getBusiness().isEnabled()) {
            return null;
        }
        try {
            return publishFailOpen(requestSupplier.get());
        } catch (RuntimeException error) {
            logEnqueueFailure("BUILD_OR_REGISTER", scene, bizKey, shopId, orderId, templateId, error);
            return null;
        }
    }

    private SmsNotificationEventEnqueueResult publishFailOpen(
            SmsNotificationEventEnqueueRequest request
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return enqueueNowFailOpen(request, "IMMEDIATE");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            logEnqueueFailure(
                    "REGISTER_AFTER_COMMIT",
                    request.scene(),
                    request.bizKey(),
                    request.shopId(),
                    request.orderId(),
                    request.templateId(),
                    new IllegalStateException("活动事务未启用事务同步，已跳过短信事件入队")
            );
            return null;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueueNowFailOpen(request, "AFTER_COMMIT");
            }
        });
        log.info("业务短信事件已登记为事务提交后入队, phase=REGISTER_AFTER_COMMIT, scene={}, bizKey={}, "
                        + "shopId={}, orderId={}, templateId={}",
                request.scene(), request.bizKey(), request.shopId(), request.orderId(), request.templateId());
        return null;
    }

    private SmsNotificationEventEnqueueResult enqueueNowFailOpen(
            SmsNotificationEventEnqueueRequest request,
            String phase
    ) {
        try {
            SmsNotificationEventEnqueueResult result = eventService.enqueue(request);
            if (result == null) {
                return null;
            }
            if (result.created()) {
                logCreatedEvent(request, phase, result);
            } else {
                log.debug("业务短信事件重复入队已忽略, phase={}, scene={}, bizKey={}, shopId={}, orderId={}, "
                                + "templateId={}, eventId={}",
                        phase, request.scene(), request.bizKey(), request.shopId(), request.orderId(),
                        request.templateId(), result.event().getId());
            }
            return result;
        } catch (RuntimeException error) {
            logEnqueueFailure(
                    phase,
                    request.scene(),
                    request.bizKey(),
                    request.shopId(),
                    request.orderId(),
                    request.templateId(),
                    error
            );
            return null;
        }
    }

    private void logCreatedEvent(
            SmsNotificationEventEnqueueRequest request,
            String phase,
            SmsNotificationEventEnqueueResult result
    ) {
        if (request.scene() == SmsNotificationSceneEnum.APP_EXPIRED) {
            log.debug("业务短信新事件入队成功, phase={}, scene={}, bizKey={}, shopId={}, orderId={}, "
                            + "templateId={}, eventId={}",
                    phase, request.scene(), request.bizKey(), request.shopId(), request.orderId(),
                    request.templateId(), result.event().getId());
            return;
        }
        log.info("业务短信新事件入队成功, phase={}, scene={}, bizKey={}, shopId={}, orderId={}, "
                        + "templateId={}, eventId={}",
                phase, request.scene(), request.bizKey(), request.shopId(), request.orderId(),
                request.templateId(), result.event().getId());
    }

    private void logEnqueueFailure(
            String phase,
            SmsNotificationSceneEnum scene,
            String bizKey,
            Long shopId,
            String orderId,
            String templateId,
            RuntimeException error
    ) {
        String errorMessage = StringUtils.abbreviate(
                StringUtils.defaultIfBlank(error.getMessage(), error.getClass().getSimpleName()),
                MAX_ERROR_MESSAGE_LENGTH
        );
        log.error("业务短信事件入队失败，已 fail-open 不影响主业务, phase={}, scene={}, bizKey={}, "
                        + "shopId={}, orderId={}, templateId={}, errorType={}, errorMessage={}",
                phase, scene, bizKey, shopId, orderId, templateId,
                error.getClass().getSimpleName(), errorMessage, error);
    }
}
