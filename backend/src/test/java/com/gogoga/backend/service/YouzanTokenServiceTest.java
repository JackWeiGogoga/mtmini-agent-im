package com.gogoga.backend.service;

import com.gogoga.backend.client.YouzanTokenClient;
import com.gogoga.backend.client.YouzanTokenResponse;
import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.youzan.YzAppToken;
import com.gogoga.backend.mapper.YzAppTokenMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouzanTokenServiceTest {

    @Mock
    private YouzanTokenClient youzanTokenClient;

    @Mock
    private YzAppTokenMapper yzAppTokenMapper;

    private YouzanTokenService youzanTokenService;

    @BeforeEach
    void setUp() {
        youzanTokenService = new YouzanTokenService(youzanTokenClient, yzAppTokenMapper, new YouzanProperties());
    }

    @Test
    void shouldPersistSuccessfulCodeExchange() {
        YouzanTokenResponse response = tokenResponse(true);
        long expiresAtMillis = 1893456000000L;
        response.setExpiresIn(null);
        response.setExpiresAtMillis(expiresAtMillis);
        when(youzanTokenClient.exchangeCode("code-1")).thenReturn(response);

        youzanTokenService.exchangeCode("code-1");

        ArgumentCaptor<YzAppToken> captor = ArgumentCaptor.forClass(YzAppToken.class);
        verify(yzAppTokenMapper).insert(captor.capture());
        YzAppToken token = captor.getValue();
        assertEquals("auth-1", token.getAuthorityId());
        assertEquals("access-1", token.getAccessToken());
        assertEquals("refresh-1", token.getRefreshToken());
        assertEquals(YouzanConstant.TOKEN_STATUS_ACTIVE, token.getStatus());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMillis), ZoneId.systemDefault()), token.getExpiresAt());
        assertEquals(28L * 24L * 60L * 60L, token.getRefreshExpiresIn());
    }

    @Test
    void shouldRejectFailedCodeExchange() {
        YouzanTokenResponse response = tokenResponse(false);
        response.setMessage("invalid code");
        when(youzanTokenClient.exchangeCode("bad-code")).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> youzanTokenService.exchangeCode("bad-code")
        );

        assertEquals("invalid code", exception.getMessage());
    }

    @Test
    void shouldMarkRefreshFailure() {
        YzAppToken token = new YzAppToken();
        token.setId(10L);
        token.setAuthorityId("auth-1");
        token.setRefreshToken("refresh-old");
        YouzanTokenResponse response = tokenResponse(false);
        response.setCode("401");
        response.setMessage("refresh failed");
        when(youzanTokenClient.refreshToken("refresh-old")).thenReturn(response);

        youzanTokenService.refreshOne(token);

        ArgumentCaptor<YzAppToken> captor = ArgumentCaptor.forClass(YzAppToken.class);
        verify(yzAppTokenMapper).updateById(captor.capture());
        assertEquals(YouzanConstant.TOKEN_STATUS_REFRESH_FAILED, captor.getValue().getStatus());
        assertEquals("refresh failed", captor.getValue().getLastErrorMsg());
    }

    @Test
    void shouldMaskTokenValue() {
        assertEquals("acce****7890", youzanTokenService.mask("access-token-7890"));
        assertEquals("****", youzanTokenService.mask("short"));
    }

    private YouzanTokenResponse tokenResponse(boolean success) {
        YouzanTokenResponse response = new YouzanTokenResponse();
        response.setSuccess(success);
        response.setAuthorityId("auth-1");
        response.setKdtId(30311000L);
        response.setAccessToken("access-1");
        response.setRefreshToken("refresh-1");
        response.setExpiresIn(604800L);
        response.setRawResponseJson("{\"access_token\":\"access-1\"}");
        return response;
    }
}
