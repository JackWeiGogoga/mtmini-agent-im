package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 美团授权核心落库服务。
 */
@Service
@RequiredArgsConstructor
public class MtAuthPersistenceService {

    private final MtShopService mtShopService;
    private final MtAccessTokenService mtAccessTokenService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtAuthAsyncTaskService mtAuthAsyncTaskService;

    @Transactional(rollbackFor = Exception.class)
    public AuthorizedShopContext saveAuthorization(MeituanTokenData tokenData, Data shopDetail) {
        MtShop shop = mtShopService.saveAuthorizedShop(tokenData, shopDetail);
        MtAccessToken savedToken = mtAccessTokenService.saveOrUpdate(shop, tokenData);
        mtPowerAccountService.ensureAccountInitialized(shop.getId());
        triggerPostAuthorizationTasksAfterCommit(
                shop.getId(),
                savedToken.getAccessToken(),
                Boolean.TRUE.equals(savedToken.getNewlyCreated())
        );
        return new AuthorizedShopContext(shop, savedToken);
    }

    private void triggerPostAuthorizationTasksAfterCommit(Long shopId, String accessToken, boolean newlyCreatedToken) {
        if (shopId == null || StringUtils.isBlank(accessToken)) {
            return;
        }
        Runnable action = () -> mtAuthAsyncTaskService.enqueuePostAuthorizationTasks(shopId, accessToken, newlyCreatedToken);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public record AuthorizedShopContext(MtShop shop, MtAccessToken accessToken) {
    }
}
