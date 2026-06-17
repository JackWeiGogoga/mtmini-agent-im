package com.gogoga.backend.service;

import com.gogoga.backend.client.YouzanTokenClient;
import com.gogoga.backend.client.YouzanTokenResponse;
import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.youzan.YzAppToken;
import com.gogoga.backend.mapper.YzAppTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 有赞 token 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouzanTokenService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
    private static final long DEFAULT_REFRESH_EXPIRES_SECONDS = Duration.ofDays(28).toSeconds();

    private final YouzanTokenClient youzanTokenClient;
    private final YzAppTokenMapper yzAppTokenMapper;
    private final YouzanProperties properties;

    @Transactional(rollbackFor = Exception.class)
    public YzAppToken exchangeCode(String code) {
        YouzanTokenResponse response = youzanTokenClient.exchangeCode(code);
        if (!response.isSuccess()) {
            throw new IllegalStateException(StringUtils.defaultIfBlank(response.getMessage(), "有赞授权码换取 token 失败"));
        }
        return saveOrUpdateToken(response, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public YzAppToken saveOrUpdateToken(YouzanTokenResponse response, boolean refresh) {
        LocalDateTime now = LocalDateTime.now();
        YzAppToken existing = StringUtils.isBlank(response.getAuthorityId())
                ? null
                : yzAppTokenMapper.selectByAuthorityId(response.getAuthorityId());
        YzAppToken target = existing == null ? new YzAppToken() : existing;
        if (existing == null) {
            target.setCreateTime(now);
        }
        target.setAuthorityId(response.getAuthorityId());
        target.setKdtId(response.getKdtId());
        target.setAccessToken(response.getAccessToken());
        target.setRefreshToken(response.getRefreshToken());
        target.setScope(response.getScope());
        LocalDateTime expiresAt = toExpiry(now, response.getExpiresAtMillis(), response.getExpiresIn());
        target.setExpiresIn(toExpiresIn(now, expiresAt, response.getExpiresIn()));
        target.setExpiresAt(expiresAt);
        Long refreshExpiresIn = firstNonNull(
                response.getRefreshExpiresIn(),
                StringUtils.isBlank(response.getRefreshToken()) ? null : DEFAULT_REFRESH_EXPIRES_SECONDS
        );
        LocalDateTime refreshExpiresAt = toExpiry(now, response.getRefreshExpiresAtMillis(), refreshExpiresIn);
        target.setRefreshExpiresIn(toExpiresIn(now, refreshExpiresAt, refreshExpiresIn));
        target.setRefreshExpiresAt(refreshExpiresAt);
        target.setStatus(YouzanConstant.TOKEN_STATUS_ACTIVE);
        target.setLastRefreshAt(refresh ? now : target.getLastRefreshAt());
        target.setRawResponseJson(response.getRawResponseJson());
        target.setLastErrorMsg(null);
        target.setUpdateTime(now);
        if (existing == null) {
            yzAppTokenMapper.insert(target);
        } else {
            yzAppTokenMapper.updateById(target);
        }
        log.info("保存有赞 token 成功，authorityId={}, kdtId={}, accessToken={}",
                response.getAuthorityId(), response.getKdtId(), mask(response.getAccessToken()));
        return target;
    }

    public void refreshExpiringTokens() {
        LocalDateTime expiresBefore = LocalDateTime.now().plusSeconds(properties.getToken().getRefreshBeforeExpirySeconds());
        List<YzAppToken> candidates = yzAppTokenMapper.selectRefreshCandidates(expiresBefore);
        for (YzAppToken candidate : candidates) {
            refreshOne(candidate);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkShopByKdtId(Long kdtId, Long yzShopId) {
        if (kdtId == null || yzShopId == null) {
            return;
        }
        yzAppTokenMapper.linkShopByKdtId(kdtId, yzShopId);
    }

    void refreshOne(YzAppToken token) {
        if (token == null || StringUtils.isBlank(token.getRefreshToken())) {
            return;
        }
        YouzanTokenResponse response = youzanTokenClient.refreshToken(token.getRefreshToken());
        if (response.isSuccess()) {
            saveOrUpdateToken(response, true);
            return;
        }
        token.setStatus(YouzanConstant.TOKEN_STATUS_REFRESH_FAILED);
        token.setLastErrorMsg(StringUtils.left(StringUtils.defaultIfBlank(response.getMessage(), "有赞 token 刷新失败"), 1000));
        token.setUpdateTime(LocalDateTime.now());
        yzAppTokenMapper.updateById(token);
        log.warn("刷新有赞 token 失败，id={}, authorityId={}, code={}, msg={}",
                token.getId(), token.getAuthorityId(), response.getCode(), response.getMessage());
    }

    private LocalDateTime toExpiry(LocalDateTime now, Long epochMillis, Long seconds) {
        if (epochMillis != null && epochMillis > 0) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), DEFAULT_ZONE_ID);
        }
        if (seconds == null || seconds <= 0) {
            return null;
        }
        return now.plusSeconds(seconds);
    }

    private Long toExpiresIn(LocalDateTime now, LocalDateTime expiresAt, Long fallbackSeconds) {
        if (expiresAt == null) {
            return fallbackSeconds;
        }
        long seconds = Duration.between(now, expiresAt).getSeconds();
        return Math.max(seconds, 0L);
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    public String mask(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
