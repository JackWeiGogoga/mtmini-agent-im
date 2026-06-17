package com.gogoga.backend.client;

import com.gogoga.backend.client.request.MtPoiDetailRequest;
import com.gogoga.backend.service.MtPaidApiPowerConsumeService;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 美团店铺开放平台客户端
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtShopClient {

    private static final String CODE_UNIAUTH_FAILED = "OP_UNIAUTH_FAILED";

    private final MeituanClient meituanClient;
    private final MtPaidApiPowerConsumeService mtPaidApiPowerConsumeService;

    /**
     * 查询授权店铺信息
     *
     * @param accessToken 授权 access token
     * @return 店铺详情
     */
    public Optional<Data> queryAuthorizedShop(String accessToken) {
        return queryAuthorizedShopResult(accessToken).shopDetail();
    }

    public Optional<Data> queryAuthorizedShop(Long shopId, String accessToken, String scene) {
        AuthorizedShopQueryResult result = queryAuthorizedShopResult(accessToken);
        if (result.remoteInvoked() && mtPaidApiPowerConsumeService != null) {
            mtPaidApiPowerConsumeService.tryConsumeAuthorizedShopQuery(shopId, scene, scene);
        }
        return result.shopDetail();
    }

    /**
     * 查询授权店铺信息，并返回原始调用状态用于上层决策
     *
     * @param accessToken 授权 access token
     * @return 查询结果
     */
    public AuthorizedShopQueryResult queryAuthorizedShopResult(String accessToken) {
        try {
            MeituanResponse<List<Data>> response = meituanClient.invokeApi(new MtPoiDetailRequest(), accessToken);
            if (Objects.isNull(response) || !response.isSuccess() || CollectionUtils.isEmpty(response.getData())) {
                log.warn("查询美团授权店铺失败，code={}, msg={}, traceId={}, tokenSuffix={}, response={}",
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        maskAccessToken(accessToken),
                        response);
                return AuthorizedShopQueryResult.failure(
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg(),
                        response == null ? null : response.getTraceId(),
                        response != null && CODE_UNIAUTH_FAILED.equals(response.getCode()),
                        response != null
                );
            }
            log.info("查询美团授权店铺结果，response={}", response);
            return AuthorizedShopQueryResult.success(
                    response.getData().stream().filter(Objects::nonNull).findFirst(),
                    response.getCode(),
                    response.getMsg(),
                    response.getTraceId(),
                    true
            );
        } catch (MtSdkException e) {
            log.error("查询美团授权店铺异常，tokenSuffix={}", maskAccessToken(accessToken), e);
            return AuthorizedShopQueryResult.failure(null, e.getMessage(), null, false, false);
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

    public record AuthorizedShopQueryResult(Optional<Data> shopDetail,
                                            String code,
                                            String msg,
                                            String traceId,
                                            boolean retryable,
                                            boolean remoteInvoked) {

        public static AuthorizedShopQueryResult success(Optional<Data> shopDetail,
                                                        String code,
                                                        String msg,
                                                        String traceId,
                                                        boolean remoteInvoked) {
            return new AuthorizedShopQueryResult(shopDetail, code, msg, traceId, false, remoteInvoked);
        }

        public static AuthorizedShopQueryResult failure(String code,
                                                        String msg,
                                                        String traceId,
                                                        boolean retryable,
                                                        boolean remoteInvoked) {
            return new AuthorizedShopQueryResult(Optional.empty(), code, msg, traceId, retryable, remoteInvoked);
        }
    }
}
