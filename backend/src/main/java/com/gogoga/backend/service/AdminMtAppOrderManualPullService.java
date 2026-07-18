package com.gogoga.backend.service;

import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.AdminMtAppOrderManualPullParam;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminMtAppOrderManualPullVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 后台手动拉取美团订阅订单服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMtAppOrderManualPullService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final Duration MAX_PULL_RANGE = Duration.ofDays(7);

    private final MtAppOrderService mtAppOrderService;
    private final RedisDistributedLockService redisDistributedLockService;

    public AdminMtAppOrderManualPullVO pull(AdminMtAppOrderManualPullParam param) {
        validate(param);

        LocalDateTime startTime = param.getStartTime();
        LocalDateTime endTime = param.getEndTime();
        long startEpochSecond = startTime.atZone(DEFAULT_ZONE_ID).toEpochSecond();
        long endEpochSecond = endTime.atZone(DEFAULT_ZONE_ID).toEpochSecond();
        AtomicReference<MtAppOrderService.PullResult> resultReference = new AtomicReference<>();

        log.info("管理员手动拉取美团订阅订单开始，operatorUserId={}, startTime={}, endTime={}",
                UserContext.getUserId(), startTime, endTime);
        boolean acquired = redisDistributedLockService.tryRun(
                MtAppOrderService.PULL_LOCK_NAME,
                MtAppOrderService.PULL_LOCK_LEASE_DURATION,
                () -> resultReference.set(mtAppOrderService.pullOrders(startEpochSecond, endEpochSecond))
        );
        if (!acquired) {
            throw new BusinessException(409, "订阅订单拉取任务正在执行，请稍后重试");
        }

        MtAppOrderService.PullResult result = resultReference.get();
        log.info("管理员手动拉取美团订阅订单完成，operatorUserId={}, result={}",
                UserContext.getUserId(), result);
        return AdminMtAppOrderManualPullVO.builder()
                .appId(result.appId())
                .startTime(startTime)
                .endTime(endTime)
                .processedPages(result.processedPages())
                .totalFetched(result.totalFetched())
                .createdCount(result.createdCount())
                .updatedCount(result.updatedCount())
                .skippedCount(result.skippedCount())
                .appliedRightsCount(result.appliedRightsCount())
                .build();
    }

    private void validate(AdminMtAppOrderManualPullParam param) {
        if (param == null || param.getStartTime() == null || param.getEndTime() == null) {
            throw new BusinessException(400, "开始时间和结束时间不能为空");
        }
        if (!param.getStartTime().isBefore(param.getEndTime())) {
            throw new BusinessException(400, "开始时间必须早于结束时间");
        }
        if (param.getEndTime().isAfter(LocalDateTime.now(DEFAULT_ZONE_ID))) {
            throw new BusinessException(400, "结束时间不能晚于当前时间");
        }
        if (Duration.between(param.getStartTime(), param.getEndTime()).compareTo(MAX_PULL_RANGE) > 0) {
            throw new BusinessException(400, "单次拉取时间范围不能超过 7 天");
        }
    }
}
