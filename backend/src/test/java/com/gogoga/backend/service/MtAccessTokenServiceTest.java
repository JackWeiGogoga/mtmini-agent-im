package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.auth.MeituanTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAccessTokenServiceTest {

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtProperties mtProperties;

    @Mock
    private MtAccessTokenHistoryService mtAccessTokenHistoryService;

    @InjectMocks
    private MtAccessTokenService mtAccessTokenService;

    @Test
    void shouldMarkNewTokenOrderPushAsPendingSync() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        MeituanTokenData tokenData = buildTokenData();
        when(mtAccessTokenMapper.selectByOpBizCode("op-001")).thenReturn(null);

        MtAccessToken saved = mtAccessTokenService.saveOrUpdate(shop, tokenData);

        assertTrue(saved.getNewlyCreated());
        assertNull(saved.getOrderPushBlocked());
        assertNull(saved.getOrderPushLastSyncAt());
        assertNull(saved.getOrderPushLastSyncError());
        assertEquals(1, saved.getAuthStatus());
        assertNotNull(saved.getAuthStatusChangedAt());
        verify(mtAccessTokenMapper).insert(any(MtAccessToken.class));
    }

    @Test
    void shouldNotResetOrderPushStateWhenUpdatingExistingToken() {
        MtShop shop = new MtShop();
        shop.setId(101L);
        MeituanTokenData tokenData = buildTokenData();
        MtAccessToken current = new MtAccessToken();
        current.setId(1L);
        current.setOrderPushBlocked(true);
        current.setOrderPushLastSyncError("old-error");
        when(mtAccessTokenMapper.selectByOpBizCode("op-001")).thenReturn(current);

        MtAccessToken saved = mtAccessTokenService.saveOrUpdate(shop, tokenData);

        assertFalse(saved.getNewlyCreated());
        assertTrue(saved.getOrderPushBlocked());
        verify(mtAccessTokenMapper).updateById(current);
    }

    @Test
    void shouldRefreshTokenForShopImmediately() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setId(1L);
        token.setShopId(101L);
        token.setOpBizCode("op-old");
        token.setAccessToken("access-old");
        token.setRefreshToken("refresh-old");
        token.setExpiresAt(LocalDateTime.of(2026, 5, 8, 10, 0));
        MeituanTokenData refreshed = new MeituanTokenData();
        refreshed.setAccessToken("access-new");
        refreshed.setRefreshToken("refresh-new");
        refreshed.setOpBizCode("op-new");
        refreshed.setExpireIn(3600);
        MeituanTokenResponse response = new MeituanTokenResponse();
        response.setCode(0);
        response.setData(refreshed);
        when(mtAccessTokenMapper.selectByShopId(101L)).thenReturn(token);
        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.refreshToken(16, "refresh-old")).thenReturn(response);

        MtAccessToken result = mtAccessTokenService.refreshTokenForShop(101L);

        assertEquals("access-new", result.getAccessToken());
        assertEquals("refresh-new", result.getRefreshToken());
        assertEquals("op-new", result.getOpBizCode());
        assertEquals(1, result.getAuthStatus());
        assertNotNull(result.getAuthStatusChangedAt());
        verify(mtAccessTokenMapper).updateById(token);
        verify(mtAccessTokenHistoryService).recordTokenSafely(
                MtAccessTokenHistoryService.EVENT_TYPE_REFRESH,
                101L,
                refreshed,
                0,
                null
        );
    }

    @Test
    void shouldReturnRefreshFailureDetailWhenRemoteRejectsRefreshToken() throws Exception {
        MtAccessToken token = new MtAccessToken();
        token.setId(1L);
        token.setShopId(101L);
        token.setOpBizCode("op-old");
        token.setRefreshToken("refresh-old");
        MeituanTokenResponse response = new MeituanTokenResponse();
        response.setCode(10009);
        response.setMsg("授权不存在或已过期, 请联系商家进行授权.");
        when(mtAccessTokenMapper.selectByShopId(101L)).thenReturn(token);
        when(mtProperties.getBusinessId()).thenReturn(16);
        when(meituanClient.refreshToken(16, "refresh-old")).thenReturn(response);

        MtAccessTokenService.TokenRefreshResult result = mtAccessTokenService.refreshTokenForShopWithResult(101L);

        assertFalse(result.success());
        assertNull(result.token());
        assertEquals(10009, result.responseCode());
        assertEquals("授权不存在或已过期, 请联系商家进行授权.", result.responseMsg());
        assertEquals("REMOTE_REFRESH_FAILED", result.error());
        verify(mtAccessTokenMapper, never()).updateById(any());
    }

    @Test
    void shouldMarkTokenReauthRequired() {
        when(mtAccessTokenMapper.updateAuthStatusByShopId(any(), any(), any(), any(), any())).thenReturn(1);

        int rows = mtAccessTokenService.markReauthRequired(101L);

        assertEquals(1, rows);
        verify(mtAccessTokenMapper).updateAuthStatusByShopId(
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(2),
                any(),
                any(),
                any()
        );
    }

    private MeituanTokenData buildTokenData() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setRefreshToken("refresh-token");
        tokenData.setOpBizCode("op-001");
        tokenData.setExpireIn(3600);
        return tokenData;
    }
}
