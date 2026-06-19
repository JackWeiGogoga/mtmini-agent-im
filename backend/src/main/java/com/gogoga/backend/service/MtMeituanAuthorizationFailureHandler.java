package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.util.MeituanAuthFailureUtil;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 美团授权失败后的本地状态收敛与 token 重试处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtMeituanAuthorizationFailureHandler {

    private final MtAccessTokenService mtAccessTokenService;

    public void handleConfirmedAuthFailure(Long shopId, String source, String responseCode, String responseMsg) {
        if (shopId == null) {
            return;
        }
        mtAccessTokenService.markReauthRequired(shopId);
        log.warn("美团接口确认鉴权失败，已标记重新授权，shopId={}, source={}, responseCode={}, responseMsg={}",
                shopId, source, responseCode, responseMsg);
    }

    public <T> AuthorizedCallResult<T> invokeWithRefreshRetry(
            Long shopId,
            String accessToken,
            String source,
            AuthorizedCall<T> call,
            AuthFailureInspector<T> authFailureInspector
    ) throws MtSdkException {
        T firstResponse = call.invoke(accessToken);
        AuthFailureInfo firstFailure = inspectAuthFailure(firstResponse, authFailureInspector);
        if (!firstFailure.authFailure()) {
            return new AuthorizedCallResult<>(firstResponse, accessToken, false, false);
        }

        log.warn("美团接口鉴权失败，准备刷新 token 后重试一次，shopId={}, source={}, responseCode={}, responseMsg={}",
                shopId, source, firstFailure.code(), firstFailure.message());
        MtAccessTokenService.TokenRefreshResult tokenRefreshResult = mtAccessTokenService.refreshTokenForShopWithResult(shopId);
        MtAccessToken refreshedToken = tokenRefreshResult == null ? null : tokenRefreshResult.token();
        String refreshedAccessToken = refreshedToken == null ? null : refreshedToken.getAccessToken();
        if (StringUtils.isBlank(refreshedAccessToken)) {
            handleConfirmedAuthFailure(shopId, source, firstFailure.code(), firstFailure.message());
            log.warn("美团接口鉴权失败后刷新 token 未获得可用 accessToken，shopId={}, source={}, refreshStatus={}, refreshCode={}, refreshMsg={}, refreshError={}",
                    shopId,
                    source,
                    tokenRefreshResult != null && tokenRefreshResult.success() ? "SUCCESS" : "FAILED",
                    tokenRefreshResult == null ? null : tokenRefreshResult.responseCode(),
                    tokenRefreshResult == null ? null : tokenRefreshResult.responseMsg(),
                    tokenRefreshResult == null ? "REFRESH_RESULT_MISSING" : tokenRefreshResult.error());
            return new AuthorizedCallResult<>(firstResponse, accessToken, true, true);
        }

        T retryResponse = call.invoke(refreshedAccessToken);
        AuthFailureInfo retryFailure = inspectAuthFailure(retryResponse, authFailureInspector);
        if (retryFailure.authFailure()) {
            handleConfirmedAuthFailure(shopId, source, retryFailure.code(), retryFailure.message());
            log.warn("美团接口刷新 token 后重试仍鉴权失败，shopId={}, source={}, responseCode={}, responseMsg={}",
                    shopId, source, retryFailure.code(), retryFailure.message());
            return new AuthorizedCallResult<>(retryResponse, refreshedAccessToken, true, true);
        }

        log.info("美团接口鉴权失败后刷新 token 重试成功，shopId={}, source={}", shopId, source);
        return new AuthorizedCallResult<>(retryResponse, refreshedAccessToken, true, false);
    }

    private <T> AuthFailureInfo inspectAuthFailure(T response, AuthFailureInspector<T> authFailureInspector) {
        AuthFailureInfo info = authFailureInspector == null ? null : authFailureInspector.inspect(response);
        return info == null ? AuthFailureInfo.notAuthFailure() : info;
    }

    public record AuthorizedCallResult<T>(
            T response,
            String accessToken,
            boolean retried,
            boolean confirmedAuthFailure
    ) {
    }

    public record AuthFailureInfo(boolean authFailure, String code, String message) {

        public static AuthFailureInfo of(String code, String message) {
            return new AuthFailureInfo(MeituanAuthFailureUtil.isAuthFailure(code, message), code, message);
        }

        public static AuthFailureInfo notAuthFailure() {
            return new AuthFailureInfo(false, null, null);
        }
    }

    @FunctionalInterface
    public interface AuthorizedCall<T> {

        T invoke(String accessToken) throws MtSdkException;
    }

    @FunctionalInterface
    public interface AuthFailureInspector<T> {

        AuthFailureInfo inspect(T response);
    }
}
