package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAccessTokenHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenHistoryMapper;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.meituan.sdk.auth.MeituanTokenData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAccessTokenHistoryServiceTest {

    @Mock
    private MtAccessTokenHistoryMapper mtAccessTokenHistoryMapper;

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @Mock
    private MtShopService mtShopService;

    private MtTokenCryptoService mtTokenCryptoService;
    private MtAccessTokenHistoryService mtAccessTokenHistoryService;

    @BeforeEach
    void setUp() {
        MtProperties properties = new MtProperties();
        properties.getToken().getHistory().setEncryptionKey("unit-test-encryption-key");
        properties.getToken().getHistory().setFingerprintKey("unit-test-fingerprint-key");
        mtTokenCryptoService = new MtTokenCryptoService(properties);
        mtAccessTokenHistoryService = new MtAccessTokenHistoryService(
                mtAccessTokenHistoryMapper,
                mtAccessTokenMapper,
                mtShopService,
                mtTokenCryptoService,
                new NoopTransactionManager()
        );
    }

    @Test
    void shouldPersistEncryptedHistoryWithoutPlainToken() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token-secret");
        tokenData.setRefreshToken("refresh-token-secret");
        tokenData.setOpBizCode("epoi-001");
        tokenData.setExpireIn(3600);

        mtAccessTokenHistoryService.recordTokenSafely(
                MtAccessTokenHistoryService.EVENT_TYPE_AUTH_CALLBACK,
                null,
                tokenData,
                0,
                "success"
        );

        ArgumentCaptor<MtAccessTokenHistory> captor = ArgumentCaptor.forClass(MtAccessTokenHistory.class);
        verify(mtAccessTokenHistoryMapper).insert(captor.capture());
        MtAccessTokenHistory history = captor.getValue();
        assertEquals("epoi-001", history.getOpBizCode());
        assertEquals(MtAccessTokenHistoryService.EVENT_TYPE_AUTH_CALLBACK, history.getEventType());
        assertFalse(history.getAccessTokenCipher().contains("access-token-secret"));
        assertFalse(history.getRefreshTokenCipher().contains("refresh-token-secret"));
        assertEquals(mtTokenCryptoService.fingerprint("access-token-secret"), history.getAccessTokenFingerprint());
    }

    @Test
    void shouldRestoreAccessTokenFromEncryptedHistory() {
        MtAccessTokenHistory history = new MtAccessTokenHistory();
        history.setId(10L);
        history.setShopId(null);
        history.setOpBizCode("epoi-001");
        history.setAccessTokenCipher(mtTokenCryptoService.encrypt("access-token-secret"));
        history.setRefreshTokenCipher(mtTokenCryptoService.encrypt("refresh-token-secret"));
        history.setExpiresAt(LocalDateTime.of(2026, 5, 9, 12, 0));
        when(mtAccessTokenHistoryMapper.selectById(10L)).thenReturn(history);
        when(mtAccessTokenMapper.selectByOpBizCode("epoi-001")).thenReturn(new MtAccessToken());
        MtShop shop = new MtShop();
        shop.setId(99L);
        when(mtShopService.findByEpoiId("epoi-001")).thenReturn(shop);

        mtAccessTokenHistoryService.restoreFromHistory(10L);

        ArgumentCaptor<MtAccessToken> tokenCaptor = ArgumentCaptor.forClass(MtAccessToken.class);
        verify(mtAccessTokenMapper).insert(tokenCaptor.capture());
        assertEquals("access-token-secret", tokenCaptor.getValue().getAccessToken());
        assertEquals("refresh-token-secret", tokenCaptor.getValue().getRefreshToken());
        assertEquals(99L, tokenCaptor.getValue().getShopId());
    }

    @Test
    void shouldNotThrowWhenHistoryPersistenceFails() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token-secret");
        tokenData.setRefreshToken("refresh-token-secret");
        tokenData.setOpBizCode("epoi-001");
        tokenData.setExpireIn(3600);
        when(mtAccessTokenHistoryMapper.insert(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> mtAccessTokenHistoryService.recordTokenSafely(
                MtAccessTokenHistoryService.EVENT_TYPE_AUTH_CALLBACK,
                null,
                tokenData,
                0,
                "success"
        ));
    }

    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
