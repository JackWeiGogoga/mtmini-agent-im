package com.gogoga.backend.service;

import com.gogoga.backend.client.MtShopClient;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.auth.MeituanTokenResponse;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAuthServiceTest {

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtProperties mtProperties;

    @Mock
    private MtShopClient mtShopClient;

    @Mock
    private MtAuthPersistenceService mtAuthPersistenceService;

    @Mock
    private MtAccessTokenHistoryService mtAccessTokenHistoryService;

    @InjectMocks
    private MtAuthService mtAuthService;

    @Test
    void shouldEnqueuePostAuthorizationTasksAfterTokenAndShopContextAreReady() throws Exception {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setOpBizCode("epoi-001");
        tokenData.setOpBizName("测试门店");

        MeituanTokenResponse tokenResponse = new MeituanTokenResponse();
        tokenResponse.setCode(0);
        tokenResponse.setData(tokenData);

        MtShop savedShop = new MtShop();
        savedShop.setId(1L);
        savedShop.setEpoiId("epoi-001");
        MtAccessToken savedToken = new MtAccessToken();
        savedToken.setAccessToken("access-token");

        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.getOAuthToken(16, "auth-code")).thenReturn(tokenResponse);
        when(mtShopClient.queryAuthorizedShopResult("access-token"))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure("OP_FAILED", "fail", "trace-1", false, true));
        when(mtAuthPersistenceService.saveAuthorization(tokenData, null))
                .thenReturn(new MtAuthPersistenceService.AuthorizedShopContext(savedShop, savedToken));

        mtAuthService.auth("auth-code");

        InOrder inOrder = inOrder(mtAccessTokenHistoryService, mtAuthPersistenceService);
        inOrder.verify(mtAccessTokenHistoryService).recordTokenSafely(
                eq(MtAccessTokenHistoryService.EVENT_TYPE_AUTH_CALLBACK),
                eq(null),
                eq(tokenData),
                eq(0),
                eq(null)
        );
        inOrder.verify(mtAuthPersistenceService).saveAuthorization(tokenData, null);
    }

    @Test
    void shouldStillEnqueuePostAuthorizationTasksWhenShopDetailQueryFallsBackToNull() throws Exception {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setOpBizCode("epoi-001");

        MeituanTokenResponse tokenResponse = new MeituanTokenResponse();
        tokenResponse.setCode(0);
        tokenResponse.setData(tokenData);

        MtShop savedShop = new MtShop();
        savedShop.setId(1L);
        MtAccessToken savedToken = new MtAccessToken();
        savedToken.setAccessToken("access-token");

        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.getOAuthToken(16, "auth-code")).thenReturn(tokenResponse);
        when(mtShopClient.queryAuthorizedShopResult("access-token"))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure("OP_FAILED", "fail", "trace-1", false, true));
        when(mtAuthPersistenceService.saveAuthorization(tokenData, null))
                .thenReturn(new MtAuthPersistenceService.AuthorizedShopContext(savedShop, savedToken));

        mtAuthService.auth("auth-code");

        verify(mtAuthPersistenceService).saveAuthorization(tokenData, null);
    }

    @Test
    void shouldRetryAuthorizedShopQueryWhenUniAuthFailedThenSucceed() throws Exception {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setOpBizCode("epoi-001");

        MeituanTokenResponse tokenResponse = new MeituanTokenResponse();
        tokenResponse.setCode(0);
        tokenResponse.setData(tokenData);

        MtShop savedShop = new MtShop();
        savedShop.setId(1L);

        Data detail = new Data();
        detail.setEpoiId("epoi-001");

        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.getOAuthToken(16, "auth-code")).thenReturn(tokenResponse);
        when(mtShopClient.queryAuthorizedShopResult("access-token"))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure(
                        "OP_UNIAUTH_FAILED", "授权关系不存在 鉴权失败", "trace-1", true, true))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.success(
                        Optional.of(detail), "OP_SUCCESS", "success", "trace-2", true));
        MtAccessToken savedToken = new MtAccessToken();
        savedToken.setAccessToken("access-token");
        when(mtAuthPersistenceService.saveAuthorization(tokenData, detail))
                .thenReturn(new MtAuthPersistenceService.AuthorizedShopContext(savedShop, savedToken));

        mtAuthService.auth("auth-code");

        verify(mtShopClient, times(2)).queryAuthorizedShopResult("access-token");
        verify(mtAuthPersistenceService).saveAuthorization(tokenData, detail);
    }

    @Test
    void shouldFallbackToNullShopDetailAfterRetryableAuthorizedShopQueryFailsRepeatedly() throws Exception {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setOpBizCode("epoi-001");

        MeituanTokenResponse tokenResponse = new MeituanTokenResponse();
        tokenResponse.setCode(0);
        tokenResponse.setData(tokenData);

        MtShop savedShop = new MtShop();
        savedShop.setId(1L);

        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.getOAuthToken(16, "auth-code")).thenReturn(tokenResponse);
        when(mtShopClient.queryAuthorizedShopResult("access-token"))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure(
                        "OP_UNIAUTH_FAILED", "授权关系不存在 鉴权失败", "trace-1", true, true))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure(
                        "OP_UNIAUTH_FAILED", "授权关系不存在 鉴权失败", "trace-2", true, true))
                .thenReturn(MtShopClient.AuthorizedShopQueryResult.failure(
                        "OP_UNIAUTH_FAILED", "授权关系不存在 鉴权失败", "trace-3", true, true));
        MtAccessToken savedToken = new MtAccessToken();
        savedToken.setAccessToken("access-token");
        when(mtAuthPersistenceService.saveAuthorization(tokenData, null))
                .thenReturn(new MtAuthPersistenceService.AuthorizedShopContext(savedShop, savedToken));

        mtAuthService.auth("auth-code");

        verify(mtShopClient, times(3)).queryAuthorizedShopResult("access-token");
        verify(mtAuthPersistenceService).saveAuthorization(tokenData, null);
    }
}
