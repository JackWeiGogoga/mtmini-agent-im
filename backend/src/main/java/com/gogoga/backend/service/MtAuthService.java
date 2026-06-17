package com.gogoga.backend.service;

import com.gogoga.backend.client.MtShopClient;
import com.gogoga.backend.config.MtProperties;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.auth.MeituanTokenResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 美团授权服务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAuthService {

    private static final int AUTHORIZED_SHOP_QUERY_MAX_ATTEMPTS = 3;
    private static final long AUTHORIZED_SHOP_QUERY_RETRY_INTERVAL_MILLIS = 1500L;

    private final MeituanClient meituanClient;
    private final MtProperties mtProperties;
    private final MtShopClient mtShopClient;
    private final MtAuthPersistenceService mtAuthPersistenceService;
    private final MtAccessTokenHistoryService mtAccessTokenHistoryService;

    /**
     * 处理美团授权回调
     *
     * @param code 授权码
     */
    public void auth(String code) {
        if (StringUtils.isBlank(code)) {
            throw new IllegalArgumentException("auth code must not be blank");
        }

        try {
            MeituanTokenResponse tokenResponse = meituanClient.getOAuthToken(mtProperties.getBusinessId(), code);
            if (Objects.isNull(tokenResponse) || !Objects.equals(tokenResponse.getCode(), 0) || Objects.isNull(tokenResponse.getData())) {
                log.error("美团授权获取 token 失败，code={}, response={}", code, tokenResponse);
                throw new IllegalStateException("Failed to fetch Meituan OAuth token");
            }

            MeituanTokenData tokenData = tokenResponse.getData();
            mtAccessTokenHistoryService.recordTokenSafely(
                    MtAccessTokenHistoryService.EVENT_TYPE_AUTH_CALLBACK,
                    null,
                    tokenData,
                    tokenResponse.getCode(),
                    tokenResponse.getMsg()
            );
            AuthorizedShopLookupResult authorizedShopLookup =
                    queryAuthorizedShopWithRetry(tokenData);
            MtAuthPersistenceService.AuthorizedShopContext context = mtAuthPersistenceService.saveAuthorization(
                    tokenData,
                    authorizedShopLookup.shopDetail().orElse(null)
            );
            log.info("美团授权核心状态保存成功，shopId={}, opBizCode={}, remoteShopQueryCount={}",
                    context.shop().getId(), tokenData.getOpBizCode(), authorizedShopLookup.remoteInvocationCount());
        } catch (MtSdkException e) {
            log.error("美团授权异常，code={}", code, e);
            throw new IllegalStateException("Failed to authorize Meituan shop", e);
        }
    }

    private AuthorizedShopLookupResult queryAuthorizedShopWithRetry(
            MeituanTokenData tokenData) {
        MtShopClient.AuthorizedShopQueryResult result = null;
        int remoteInvocationCount = 0;
        for (int attempt = 1; attempt <= AUTHORIZED_SHOP_QUERY_MAX_ATTEMPTS; attempt++) {
            result = mtShopClient.queryAuthorizedShopResult(tokenData.getAccessToken());
            if (result.remoteInvoked()) {
                remoteInvocationCount++;
            }
            if (result.shopDetail().isPresent()) {
                if (attempt > 1) {
                    log.info("授权后查询门店详情重试成功，attempt={}, opBizCode={}, traceId={}",
                            attempt, tokenData.getOpBizCode(), result.traceId());
                }
                return new AuthorizedShopLookupResult(result.shopDetail(), remoteInvocationCount);
            }
            if (!result.retryable() || attempt == AUTHORIZED_SHOP_QUERY_MAX_ATTEMPTS) {
                break;
            }
            log.warn("授权后查询门店详情鉴权未生效，准备重试，attempt={}, maxAttempts={}, opBizCode={}, traceId={}, code={}, msg={}",
                    attempt,
                    AUTHORIZED_SHOP_QUERY_MAX_ATTEMPTS,
                    tokenData.getOpBizCode(),
                    result.traceId(),
                    result.code(),
                    result.msg());
            sleepBeforeAuthorizedShopRetry(tokenData.getOpBizCode(), attempt);
        }
        if (result != null && result.retryable()) {
            log.warn("授权后查询门店详情多次重试仍失败，按降级逻辑继续，opBizCode={}, traceId={}, code={}, msg={}",
                    tokenData.getOpBizCode(), result.traceId(), result.code(), result.msg());
        }
        return new AuthorizedShopLookupResult(Optional.empty(), remoteInvocationCount);
    }

    void sleepBeforeAuthorizedShopRetry(String opBizCode, int attempt) {
        try {
            Thread.sleep(AUTHORIZED_SHOP_QUERY_RETRY_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("授权后查询门店详情重试等待被中断，attempt={}, opBizCode={}", attempt, opBizCode);
        }
    }

    private record AuthorizedShopLookupResult(
            Optional<com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data> shopDetail,
            int remoteInvocationCount
    ) {
    }
}
