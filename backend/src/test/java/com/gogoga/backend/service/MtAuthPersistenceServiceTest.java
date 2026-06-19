package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.meituan.sdk.auth.MeituanTokenData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtAuthPersistenceServiceTest {

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtAccessTokenService mtAccessTokenService;

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @InjectMocks
    private MtAuthPersistenceService mtAuthPersistenceService;

    @Test
    void shouldSaveCoreAuthorizationStateBeforePostAuthorizationTasks() {
        MeituanTokenData tokenData = new MeituanTokenData();
        tokenData.setAccessToken("access-token");
        tokenData.setOpBizCode("epoi-001");

        MtShop shop = new MtShop();
        shop.setId(1L);
        MtAccessToken token = new MtAccessToken();
        token.setAccessToken("access-token");
        token.setNewlyCreated(true);

        when(mtShopService.saveAuthorizedShop(tokenData, null)).thenReturn(shop);
        when(mtAccessTokenService.saveOrUpdate(shop, tokenData)).thenReturn(token);

        mtAuthPersistenceService.saveAuthorization(tokenData, null);

        InOrder inOrder = inOrder(mtShopService, mtAccessTokenService, mtPowerAccountService, mtAuthAsyncTaskService);
        inOrder.verify(mtShopService).saveAuthorizedShop(tokenData, null);
        inOrder.verify(mtAccessTokenService).saveOrUpdate(shop, tokenData);
        inOrder.verify(mtPowerAccountService).ensureAccountInitialized(1L);
        inOrder.verify(mtAuthAsyncTaskService).enqueuePostAuthorizationTasks(1L, "access-token");
    }
}
