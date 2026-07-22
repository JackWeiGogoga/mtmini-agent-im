package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsAppExpiryCandidate;
import com.gogoga.backend.mapper.MtShopRightMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 根据本地累计权益规划应用到期短信事件。
 */
@Service
@RequiredArgsConstructor
public class SmsAppExpiryPlannerService {

    private static final LocalTime SEND_TIME = LocalTime.of(10, 0);

    private final MtShopRightMapper mtShopRightMapper;
    private final MerchantLifecycleSmsService lifecycleSmsService;
    private final SmsProperties smsProperties;

    public SmsAppExpiryPlanningResult planActiveRights() {
        ZoneId zoneId = ZoneId.of(smsProperties.getBusiness().getZoneId());
        LocalDateTime now = LocalDateTime.now(zoneId);
        int batchSize = Math.max(1, smsProperties.getBusiness().getPlannerBatchSize());
        long lastShopId = 0L;
        int candidateCount = 0;
        int createdCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        while (true) {
            List<SmsAppExpiryCandidate> candidates = mtShopRightMapper
                    .selectActiveAppExpiryCandidatesByCursor(now, lastShopId, batchSize);
            if (candidates.isEmpty()) {
                return result(candidateCount, createdCount, duplicateCount, failedCount);
            }
            for (SmsAppExpiryCandidate candidate : candidates) {
                lastShopId = candidate.getShopId();
                LocalDateTime expectedExpireAt = candidate.getExpectedExpireAt();
                if (expectedExpireAt == null || !expectedExpireAt.isAfter(now)) {
                    continue;
                }
                LocalDateTime scheduledAt = expectedExpireAt.toLocalDate().plusDays(1).atTime(SEND_TIME);
                SmsNotificationEventEnqueueResult enqueueResult = lifecycleSmsService.enqueueAppExpired(
                        candidate.getShopId(),
                        expectedExpireAt,
                        scheduledAt
                );
                candidateCount++;
                if (enqueueResult == null) {
                    failedCount++;
                } else if (enqueueResult.created()) {
                    createdCount++;
                } else {
                    duplicateCount++;
                }
            }
            if (candidates.size() < batchSize) {
                return result(candidateCount, createdCount, duplicateCount, failedCount);
            }
        }
    }

    private SmsAppExpiryPlanningResult result(
            int candidateCount,
            int createdCount,
            int duplicateCount,
            int failedCount
    ) {
        return new SmsAppExpiryPlanningResult(
                candidateCount,
                createdCount,
                duplicateCount,
                failedCount
        );
    }
}
