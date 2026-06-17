package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAccessTokenAuthStatusEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.auth.MeituanTokenResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 美团 token 服务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAccessTokenService {

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MeituanClient meituanClient;
    private final MtProperties mtProperties;
    private final MtAccessTokenHistoryService mtAccessTokenHistoryService;

    /**
     * 保存或更新授权 token
     *
     * @param shop      店铺实体
     * @param tokenData token 数据
     * @return token 实体
     */
    public MtAccessToken saveOrUpdate(MtShop shop, MeituanTokenData tokenData) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(tokenData.getExpireIn());
        MtAccessToken current = mtAccessTokenMapper.selectByOpBizCode(tokenData.getOpBizCode());
        MtAccessToken savedToken;

        if (current == null) {
            MtAccessToken token = new MtAccessToken();
            token.setAccessToken(tokenData.getAccessToken());
            token.setShopId(shop.getId());
            token.setCreateTime(now);
            token.setUpdateTime(now);
            token.setOpBizCode(tokenData.getOpBizCode());
            token.setRefreshToken(tokenData.getRefreshToken());
            token.setExpiresAt(expiresAt);
            token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
            token.setAuthStatusChangedAt(now);
            token.setLastAuthFailureAt(null);
            token.setOrderPushBlocked(null);
            token.setOrderPushLastSyncAt(null);
            token.setOrderPushLastSyncError(null);
            mtAccessTokenMapper.insert(token);
            token.setNewlyCreated(true);
            savedToken = token;
        } else {
            current.setAccessToken(tokenData.getAccessToken());
            current.setShopId(shop.getId());
            current.setUpdateTime(now);
            current.setOpBizCode(tokenData.getOpBizCode());
            current.setRefreshToken(tokenData.getRefreshToken());
            current.setExpiresAt(expiresAt);
            current.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
            current.setAuthStatusChangedAt(now);
            mtAccessTokenMapper.updateById(current);
            current.setNewlyCreated(false);
            savedToken = current;
        }

        return savedToken;
    }

    /**
     * 根据门店查询当前 token
     */
    public MtAccessToken findByShopId(Long shopId) {
        if (shopId == null) {
            return null;
        }
        return mtAccessTokenMapper.selectByShopId(shopId);
    }

    /**
     * 刷新即将过期的 token
     */
    public void refreshExpiringTokens() {
        LocalDateTime refreshBefore = LocalDateTime.now()
                .plusSeconds(mtProperties.getToken().getRefreshBeforeExpirySeconds());
        List<MtAccessToken> candidates = mtAccessTokenMapper.selectRefreshCandidates(refreshBefore);
        log.info("刷新即将过期美团 token，refreshBefore={}, candidateCount={}", refreshBefore, candidates.size());
        for (MtAccessToken candidate : candidates) {
            refreshToken(candidate);
        }
        log.info("刷新即将过期美团 token 完成，refreshBefore={}, candidateCount={}", refreshBefore, candidates.size());
    }

    public MtAccessToken refreshTokenForShop(Long shopId) {
        return refreshTokenForShopWithResult(shopId).token();
    }

    public TokenRefreshResult refreshTokenForShopWithResult(Long shopId) {
        MtAccessToken token = findByShopId(shopId);
        if (token == null) {
            log.warn("刷新美团 token 失败，shopId={} 未找到 token", shopId);
            return TokenRefreshResult.failure(null, null, "TOKEN_NOT_FOUND");
        }
        return refreshToken(token);
    }

    private TokenRefreshResult refreshToken(MtAccessToken token) {
        try {
            log.info("开始刷新美团 token，shopId={}, opBizCode={}, expiresAt={}",
                    token.getShopId(), token.getOpBizCode(), token.getExpiresAt());
            MeituanTokenResponse response = meituanClient.refreshToken(mtProperties.getBusinessId(), token.getRefreshToken());
            if (Objects.isNull(response) || !Objects.equals(response.getCode(), 0) || Objects.isNull(response.getData())) {
                log.warn("刷新美团 token 失败，opBizCode={}, response={}", token.getOpBizCode(), response);
                return TokenRefreshResult.failure(
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        "REMOTE_REFRESH_FAILED"
                );
            }

            MeituanTokenData refreshed = response.getData();
            mtAccessTokenHistoryService.recordTokenSafely(
                    MtAccessTokenHistoryService.EVENT_TYPE_REFRESH,
                    token.getShopId(),
                    refreshed,
                    parseResponseCode(response.getCode()),
                    response.getMsg()
            );
            LocalDateTime now = LocalDateTime.now();
            token.setAccessToken(refreshed.getAccessToken());
            token.setRefreshToken(refreshed.getRefreshToken());
            token.setExpiresAt(now.plusSeconds(refreshed.getExpireIn()));
            token.setOpBizCode(refreshed.getOpBizCode());
            token.setAuthStatus(MtAccessTokenAuthStatusEnum.VALID.getCode());
            token.setAuthStatusChangedAt(now);
            token.setUpdateTime(now);
            mtAccessTokenMapper.updateById(token);
            log.info("刷新美团 token 成功，shopId={}, opBizCode={}, newExpiresAt={}",
                    token.getShopId(), token.getOpBizCode(), token.getExpiresAt());
            return TokenRefreshResult.success(token, response.getCode(), response.getMsg());
        } catch (MtSdkException e) {
            log.error("刷新美团 token 异常，opBizCode={}", token.getOpBizCode(), e);
            return TokenRefreshResult.failure(null, e.getMessage(), "REMOTE_REFRESH_EXCEPTION");
        }
    }

    private Integer parseResponseCode(Object code) {
        if (code == null) {
            return null;
        }
        if (code instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(code));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isReauthRequired(MtAccessToken token) {
        return token != null
                && Objects.equals(token.getAuthStatus(), MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode());
    }

    public int markReauthRequired(Long shopId) {
        if (shopId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = mtAccessTokenMapper.updateAuthStatusByShopId(
                shopId,
                MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode(),
                now,
                now,
                now
        );
        if (rows > 0) {
            log.warn("美团授权状态已标记为需重新授权，shopId={}, authStatus={}",
                    shopId, MtAccessTokenAuthStatusEnum.REAUTH_REQUIRED.getCode());
        }
        return rows;
    }

    public int markValid(Long shopId) {
        if (shopId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = mtAccessTokenMapper.updateAuthStatusByShopId(
                shopId,
                MtAccessTokenAuthStatusEnum.VALID.getCode(),
                now,
                null,
                now
        );
        if (rows > 0) {
            log.info("美团授权状态已恢复为有效，shopId={}, authStatus={}",
                    shopId, MtAccessTokenAuthStatusEnum.VALID.getCode());
        }
        return rows;
    }

    public record TokenRefreshResult(
            boolean success,
            MtAccessToken token,
            Integer responseCode,
            String responseMsg,
            String error
    ) {

        static TokenRefreshResult success(MtAccessToken token, Integer responseCode, String responseMsg) {
            return new TokenRefreshResult(true, token, responseCode, responseMsg, null);
        }

        static TokenRefreshResult failure(Integer responseCode, String responseMsg, String error) {
            return new TokenRefreshResult(false, null, responseCode, responseMsg, error);
        }
    }

}
