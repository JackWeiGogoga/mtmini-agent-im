package com.gogoga.backend.client;

import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adAppBuyInfo.AdAppBuyInfoRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adAppBuyInfo.AdAppBuyInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * 美团应用购买信息开放平台客户端
 *
 * @author system
 * @since 2026-04-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtAppBuyInfoOpenApiClient {

    private static final String CODE_UNIAUTH_FAILED = "OP_UNIAUTH_FAILED";

    private final MeituanClient meituanClient;

    /**
     * 查询商家购买的应用信息
     *
     * @param accessToken 美团 access token
     * @return 已购应用信息
     */
    public Optional<AdAppBuyInfoResponse> queryBuyInfo(String accessToken) {
        return queryBuyInfoResult(accessToken).buyInfo();
    }

    /**
     * 查询商家购买的应用信息，并返回原始调用状态用于上层决策
     *
     * @param accessToken 美团 access token
     * @return 查询结果
     */
    public AppBuyInfoQueryResult queryBuyInfoResult(String accessToken) {
        try {
            MeituanResponse<AdAppBuyInfoResponse> response = meituanClient.invokeApi(new AdAppBuyInfoRequest(), accessToken);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询美团已购应用信息失败，code={}, msg={}, traceId={}, tokenSuffix={}, response={}",
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        maskAccessToken(accessToken),
                        response);
                return AppBuyInfoQueryResult.failure(
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response != null && CODE_UNIAUTH_FAILED.equals(response.getCode())
                );
            }

            AdAppBuyInfoResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询美团已购应用信息业务失败，traceId={}, tokenSuffix={}, result={}",
                        response.getTraceId(), maskAccessToken(accessToken), payload.getResult());
                return AppBuyInfoQueryResult.failure(
                        response.getCode(),
                        payload.getResult() == null ? null : payload.getResult().getMessage(),
                        response.getTraceId(),
                        false
                );
            }

            log.info("查询美团已购应用信息结果，traceId={}, tokenSuffix={}, version={}, endTime={}",
                    response.getTraceId(), maskAccessToken(accessToken), payload.getVersion(), payload.getEndTime());
            return AppBuyInfoQueryResult.success(
                    Optional.of(payload),
                    response.getCode(),
                    response.getMsg(),
                    response.getTraceId()
            );
        } catch (MtSdkException e) {
            log.error("查询美团已购应用信息异常，tokenSuffix={}", maskAccessToken(accessToken), e);
            return AppBuyInfoQueryResult.failure(null, e.getMessage(), null, false);
        }
    }

    private String maskAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        if (accessToken.length() <= 8) {
            return "****";
        }
        return accessToken.substring(0, 4) + "****" + accessToken.substring(accessToken.length() - 4);
    }

    public record AppBuyInfoQueryResult(Optional<AdAppBuyInfoResponse> buyInfo,
                                        String code,
                                        String msg,
                                        String traceId,
                                        boolean retryable) {

        public static AppBuyInfoQueryResult success(Optional<AdAppBuyInfoResponse> buyInfo,
                                                    String code,
                                                    String msg,
                                                    String traceId) {
            return new AppBuyInfoQueryResult(Objects.requireNonNullElse(buyInfo, Optional.empty()), code, msg, traceId, false);
        }

        public static AppBuyInfoQueryResult failure(String code,
                                                    String msg,
                                                    String traceId,
                                                    boolean retryable) {
            return new AppBuyInfoQueryResult(Optional.empty(), code, msg, traceId, retryable);
        }
    }
}
