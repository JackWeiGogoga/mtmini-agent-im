package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtAccessTokenHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAccessTokenHistoryMapper;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.meituan.sdk.auth.MeituanTokenData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 美团 token 历史服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAccessTokenHistoryService {

    public static final String EVENT_TYPE_AUTH_CALLBACK = "AUTH_CALLBACK";
    public static final String EVENT_TYPE_REFRESH = "REFRESH";
    public static final String EVENT_STATUS_SUCCESS = "SUCCESS";

    private final MtAccessTokenHistoryMapper mtAccessTokenHistoryMapper;
    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtShopService mtShopService;
    private final MtTokenCryptoService mtTokenCryptoService;
    private final PlatformTransactionManager transactionManager;

    public void recordTokenSafely(String eventType,
                                  Long shopId,
                                  MeituanTokenData tokenData,
                                  Integer responseCode,
                                  String responseMsg) {
        if (tokenData == null || StringUtils.isAnyBlank(tokenData.getAccessToken(), tokenData.getRefreshToken())) {
            return;
        }
        try {
            newRequiresNewTemplate().executeWithoutResult(status ->
                    mtAccessTokenHistoryMapper.insert(buildHistory(eventType, shopId, tokenData, responseCode, responseMsg)));
            log.info("美团token历史已保存，eventType={}, shopId={}, opBizCode={}, accessTokenFp={}",
                    eventType, shopId, tokenData.getOpBizCode(), mtTokenCryptoService.fingerprint(tokenData.getAccessToken()));
        } catch (Exception e) {
            log.error("美团token历史保存失败，eventType={}, shopId={}, opBizCode={}, accessTokenFp={}",
                    eventType,
                    shopId,
                    tokenData.getOpBizCode(),
                    mtTokenCryptoService.fingerprint(tokenData.getAccessToken()),
                    e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreFromHistory(Long historyId) {
        if (historyId == null) {
            throw new IllegalArgumentException("historyId must not be null");
        }
        MtAccessTokenHistory history = mtAccessTokenHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new BusinessException("token历史记录不存在");
        }
        restoreToken(history);
        log.warn("美团token人工恢复完成，historyId={}, shopId={}, opBizCode={}, accessTokenFp={}",
                historyId, history.getShopId(), history.getOpBizCode(), history.getAccessTokenFingerprint());
    }

    private MtAccessTokenHistory buildHistory(String eventType,
                                              Long shopId,
                                              MeituanTokenData tokenData,
                                              Integer responseCode,
                                              String responseMsg) {
        LocalDateTime now = LocalDateTime.now();
        MtAccessTokenHistory history = new MtAccessTokenHistory();
        history.setShopId(shopId);
        history.setOpBizCode(tokenData.getOpBizCode());
        history.setEventType(StringUtils.defaultIfBlank(eventType, EVENT_TYPE_AUTH_CALLBACK));
        history.setEventStatus(EVENT_STATUS_SUCCESS);
        history.setAccessTokenCipher(mtTokenCryptoService.encrypt(tokenData.getAccessToken()));
        history.setRefreshTokenCipher(mtTokenCryptoService.encrypt(tokenData.getRefreshToken()));
        history.setAccessTokenFingerprint(mtTokenCryptoService.fingerprint(tokenData.getAccessToken()));
        history.setRefreshTokenFingerprint(mtTokenCryptoService.fingerprint(tokenData.getRefreshToken()));
        history.setExpiresAt(now.plusSeconds(tokenData.getExpireIn()));
        history.setResponseCode(responseCode);
        history.setResponseMsg(StringUtils.left(responseMsg, 1000));
        history.setCreateTime(now);
        history.setUpdateTime(now);
        return history;
    }

    private void restoreToken(MtAccessTokenHistory history) {
        String accessToken = mtTokenCryptoService.decrypt(history.getAccessTokenCipher());
        String refreshToken = mtTokenCryptoService.decrypt(history.getRefreshTokenCipher());
        if (StringUtils.isAnyBlank(accessToken, refreshToken, history.getOpBizCode())) {
            throw new BusinessException("token历史记录缺少可恢复字段");
        }

        LocalDateTime now = LocalDateTime.now();
        MtAccessToken current = mtAccessTokenMapper.selectByOpBizCode(history.getOpBizCode());
        if (current == null) {
            current = new MtAccessToken();
            current.setCreateTime(now);
            current.setOrderPushBlocked(null);
            current.setOrderPushLastSyncAt(null);
            current.setOrderPushLastSyncError(null);
        }
        current.setAccessToken(accessToken);
        current.setRefreshToken(refreshToken);
        current.setOpBizCode(history.getOpBizCode());
        current.setShopId(resolveShopId(history));
        current.setExpiresAt(history.getExpiresAt());
        current.setUpdateTime(now);

        if (current.getId() == null) {
            mtAccessTokenMapper.insert(current);
            return;
        }
        mtAccessTokenMapper.updateById(current);
    }

    private Long resolveShopId(MtAccessTokenHistory history) {
        if (history.getShopId() != null) {
            return history.getShopId();
        }
        MtShop shop = mtShopService.findByEpoiId(history.getOpBizCode());
        if (shop == null) {
            throw new BusinessException("token历史记录无法匹配门店");
        }
        return shop.getId();
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }
}
