package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import com.gogoga.backend.mapper.MtPromotionSettingHistoryMapper;
import com.gogoga.backend.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 门店推广配置历史服务。
 */
@Service
@RequiredArgsConstructor
public class MtPromotionSettingHistoryService {

    public static final String SOURCE_H5 = "H5";
    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    private final MtPromotionSettingHistoryMapper mtPromotionSettingHistoryMapper;

    public void recordIfChanged(
            MtPromotionSetting previous,
            MtPromotionSetting next,
            String changeSource,
            String changeReason
    ) {
        if (next == null || next.getShopId() == null) {
            return;
        }
        if (!hasMeaningfulChange(previous, next)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MtPromotionSettingHistory history = new MtPromotionSettingHistory();
        history.setSettingId(next.getId());
        history.setShopId(next.getShopId());
        history.setVersionNo(nextVersionNo(next.getShopId()));
        history.setPromotionEnabled(Boolean.TRUE.equals(next.getPromotionEnabled()));
        history.setStrategyConfigured(Boolean.TRUE.equals(next.getStrategyConfigured()));
        history.setStrategyConfigJson(normalizeBlank(next.getStrategyConfigJson()));
        history.setStrategyConfigHash(strategyConfigHash(next.getStrategyConfigJson()));
        history.setChangeSource(StringUtils.defaultIfBlank(changeSource, SOURCE_SYSTEM));
        LoginUser loginUser = UserContext.getLoginUser();
        history.setOperatorUserId(loginUser == null ? null : loginUser.getUserId());
        history.setOperatorName(loginUser == null ? null : StringUtils.firstNonBlank(loginUser.getUserName(), loginUser.getUserId()));
        history.setChangeReason(StringUtils.left(changeReason, 255));
        history.setCreateTime(now);
        history.setUpdateTime(now);
        mtPromotionSettingHistoryMapper.insert(history);
    }

    public List<MtPromotionSettingHistory> listLatestByShopId(Long shopId, int limit) {
        if (shopId == null) {
            return List.of();
        }
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        List<MtPromotionSettingHistory> rows = mtPromotionSettingHistoryMapper.selectLatestByShopId(shopId, safeLimit);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        rows.forEach(this::applyOperatorDisplayName);
        return rows;
    }

    public static String strategyConfigHash(String strategyConfigJson) {
        String normalized = normalizeBlank(strategyConfigJson);
        if (normalized == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    public static MtPromotionSetting copySetting(MtPromotionSetting source) {
        if (source == null) {
            return null;
        }
        MtPromotionSetting copy = new MtPromotionSetting();
        copy.setId(source.getId());
        copy.setShopId(source.getShopId());
        copy.setPromotionEnabled(source.getPromotionEnabled());
        copy.setBalanceReminderEnabled(source.getBalanceReminderEnabled());
        copy.setStrategyConfigJson(source.getStrategyConfigJson());
        copy.setStrategyConfigured(source.getStrategyConfigured());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private boolean hasMeaningfulChange(MtPromotionSetting previous, MtPromotionSetting next) {
        if (previous == null) {
            return true;
        }
        return !Objects.equals(Boolean.TRUE.equals(previous.getPromotionEnabled()), Boolean.TRUE.equals(next.getPromotionEnabled()))
                || !Objects.equals(Boolean.TRUE.equals(previous.getStrategyConfigured()), Boolean.TRUE.equals(next.getStrategyConfigured()))
                || !StringUtils.equals(normalizeBlank(previous.getStrategyConfigJson()), normalizeBlank(next.getStrategyConfigJson()));
    }

    private Integer nextVersionNo(Long shopId) {
        Integer currentMax = mtPromotionSettingHistoryMapper.selectMaxVersionNoByShopId(shopId);
        return currentMax == null ? 1 : currentMax + 1;
    }

    private void applyOperatorDisplayName(MtPromotionSettingHistory history) {
        if (history == null) {
            return;
        }
        String fallback = switch (StringUtils.defaultString(history.getChangeSource()).toUpperCase()) {
            case SOURCE_H5 -> "商家端";
            case SOURCE_ADMIN -> "后台";
            case SOURCE_SYSTEM -> "系统";
            default -> "未知";
        };
        history.setOperatorDisplayName(StringUtils.firstNonBlank(
                history.getOperatorName(),
                history.getOperatorUserId(),
                fallback
        ));
    }

    private static String normalizeBlank(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }
}
