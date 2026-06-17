package com.gogoga.backend.service;

import com.gogoga.backend.client.MtUpdateBlockMsgListClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 美团订单推送控制服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtOrderPushControlService {

    private static final int ERROR_MAX_LENGTH = 1000;

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtUpdateBlockMsgListClient mtUpdateBlockMsgListClient;

    public OrderPushSyncResult unblockOrderPush(MtAccessToken accessToken, String source) {
        return syncOrderPush(accessToken, false, source);
    }

    public OrderPushSyncResult blockOrderPush(MtAccessToken accessToken, String source) {
        return syncOrderPush(accessToken, true, source);
    }

    private OrderPushSyncResult syncOrderPush(MtAccessToken accessToken, boolean block, String source) {
        LocalDateTime now = LocalDateTime.now();
        String safeSource = StringUtils.defaultIfBlank(source, "UNKNOWN");
        if (accessToken == null || accessToken.getId() == null) {
            return OrderPushSyncResult.failure("缺少美团授权记录");
        }
        if (StringUtils.isBlank(accessToken.getAccessToken())) {
            String error = "缺少 accessToken";
            updateSyncFailure(accessToken, now, error);
            return OrderPushSyncResult.failure(error);
        }
        if (StringUtils.isBlank(accessToken.getOpBizCode())) {
            String error = "缺少 opBizCode";
            updateSyncFailure(accessToken, now, error);
            return OrderPushSyncResult.failure(error);
        }

        MtUpdateBlockMsgListClient.UpdateBlockMsgListResult remoteResult = block
                ? mtUpdateBlockMsgListClient.blockOrderPushResult(accessToken.getAccessToken(), accessToken.getOpBizCode())
                : mtUpdateBlockMsgListClient.unblockOrderPushResult(accessToken.getAccessToken(), accessToken.getOpBizCode());
        if (remoteResult != null && remoteResult.success()) {
            mtAccessTokenMapper.updateOrderPushSyncState(accessToken.getId(), block, now, null);
            accessToken.setOrderPushBlocked(block);
            accessToken.setOrderPushLastSyncAt(now);
            accessToken.setOrderPushLastSyncError(null);
            log.info("同步美团订单推送状态成功，source={}, shopId={}, opBizCode={}, blocked={}, traceId={}",
                    safeSource, accessToken.getShopId(), accessToken.getOpBizCode(), block, remoteResult.traceId());
            return OrderPushSyncResult.succeeded();
        }

        String error = buildRemoteError(remoteResult);
        updateSyncFailure(accessToken, now, error);
        log.warn("同步美团订单推送状态失败，source={}, shopId={}, opBizCode={}, blocked={}, code={}, msg={}, traceId={}",
                safeSource,
                accessToken.getShopId(),
                accessToken.getOpBizCode(),
                block,
                remoteResult == null ? null : remoteResult.code(),
                remoteResult == null ? null : remoteResult.msg(),
                remoteResult == null ? null : remoteResult.traceId());
        return OrderPushSyncResult.failure(error);
    }

    private void updateSyncFailure(MtAccessToken accessToken, LocalDateTime now, String error) {
        mtAccessTokenMapper.updateOrderPushSyncState(
                accessToken.getId(),
                accessToken.getOrderPushBlocked(),
                now,
                StringUtils.left(StringUtils.defaultIfBlank(error, "同步订单推送失败"), ERROR_MAX_LENGTH)
        );
        accessToken.setOrderPushLastSyncAt(now);
        accessToken.setOrderPushLastSyncError(error);
    }

    private String buildRemoteError(MtUpdateBlockMsgListClient.UpdateBlockMsgListResult remoteResult) {
        if (remoteResult == null) {
            return "同步订单推送失败";
        }
        return StringUtils.defaultIfBlank(remoteResult.msg(), "同步订单推送失败");
    }

    public record OrderPushSyncResult(boolean success, String errorMessage) {

        public static OrderPushSyncResult succeeded() {
            return new OrderPushSyncResult(true, null);
        }

        public static OrderPushSyncResult failure(String errorMessage) {
            return new OrderPushSyncResult(false, errorMessage);
        }
    }
}
