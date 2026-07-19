package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtImAutoReplySetting;
import com.gogoga.backend.entity.mt.MtImAutoReplySettingHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImAutoReplySettingHistoryMapper;
import com.gogoga.backend.mapper.MtImAutoReplySettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 门店操作开关服务。
 */
@Service
@RequiredArgsConstructor
public class ShopOperationSettingsService {

    public static final String CHANGE_SOURCE_AUTH = "AUTH";
    public static final String CHANGE_SOURCE_H5 = "H5";
    public static final String CHANGE_SOURCE_ADMIN = "ADMIN";
    public static final String CHANGE_SOURCE_SYSTEM = "SYSTEM";
    public static final String CHANGE_REASON_POWER_BALANCE_NOT_ENOUGH = "POWER_BALANCE_NOT_ENOUGH";
    private static final boolean DEFAULT_AUTO_REPLY_ENABLED = false;

    private final MtShopMapper mtShopMapper;
    private final MtImAutoReplySettingMapper mtImAutoReplySettingMapper;
    private final MtImAutoReplySettingHistoryMapper mtImAutoReplySettingHistoryMapper;

    public ShopOperationSettingsVO buildSettings(MtShop shop) {
        if (shop == null) {
            return null;
        }
        return buildSettings(shop.getId());
    }

    public ShopOperationSettingsVO buildSettings(Long shopId) {
        if (shopId == null) {
            return null;
        }
        return toVO(resolveSetting(shopId));
    }

    public boolean isAutoReplyEnabled(Long shopId) {
        return isAutoReplyEnabled(resolveSetting(shopId));
    }

    public boolean isAutoReplyEnabled(MtImAutoReplySetting setting) {
        return setting != null && Boolean.TRUE.equals(setting.getAutoReplyEnabled());
    }

    /**
     * 算力不足时只关闭本地 IM 自动回复开关。
     *
     * <p>使用独立事务，确保调用方的算力消费事务回滚时，关停结果仍能保留。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ShopOperationSettingsVO disableImAutoReplyForInsufficientPower(Long shopId) {
        MtImAutoReplySetting current = resolveSetting(shopId);
        if (!isAutoReplyEnabled(current)) {
            return toVO(current);
        }
        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(false);
        return updateSettings(
                shopId,
                param,
                CHANGE_SOURCE_SYSTEM,
                CHANGE_REASON_POWER_BALANCE_NOT_ENOUGH
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopOperationSettingsVO updateSettings(Long shopId,
                                                  ShopOperationSettingsUpdateParam param,
                                                  String changeSource) {
        return updateSettings(shopId, param, changeSource, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopOperationSettingsVO updateSettings(Long shopId,
                                                  ShopOperationSettingsUpdateParam param,
                                                  String changeSource,
                                                  String changeReason) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        MtShop shop = mtShopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException("门店不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        MtImAutoReplySetting before = resolveSetting(shopId);
        boolean desiredEnabled = Boolean.TRUE.equals(param.getAutoReplyEnabled());
        if (isAutoReplyEnabled(before) == desiredEnabled) {
            return toVO(before);
        }
        MtImAutoReplySetting after = copySetting(before);
        after.setAutoReplyEnabled(desiredEnabled);
        after.setUpdateTime(now);

        if (after.getId() == null) {
            after.setCreateTime(now);
            mtImAutoReplySettingMapper.insert(after);
        } else {
            int updated = mtImAutoReplySettingMapper.updateById(after);
            if (updated <= 0) {
                throw new BusinessException("更新门店开关失败");
            }
        }
        appendHistory(before, after, changeSource, changeReason, now);
        return toVO(after);
    }

    private MtImAutoReplySetting resolveSetting(Long shopId) {
        if (shopId == null) {
            return null;
        }
        MtImAutoReplySetting setting = mtImAutoReplySettingMapper.selectByShopId(shopId);
        if (setting != null) {
            return setting;
        }
        MtImAutoReplySetting defaults = new MtImAutoReplySetting();
        defaults.setShopId(shopId);
        defaults.setAutoReplyEnabled(DEFAULT_AUTO_REPLY_ENABLED);
        return defaults;
    }

    private MtImAutoReplySetting copySetting(MtImAutoReplySetting source) {
        MtImAutoReplySetting copy = new MtImAutoReplySetting();
        if (source == null) {
            copy.setAutoReplyEnabled(DEFAULT_AUTO_REPLY_ENABLED);
            return copy;
        }
        copy.setId(source.getId());
        copy.setShopId(source.getShopId());
        copy.setAutoReplyEnabled(source.getAutoReplyEnabled());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private ShopOperationSettingsVO toVO(MtImAutoReplySetting setting) {
        if (setting == null) {
            return null;
        }
        ShopOperationSettingsVO vo = new ShopOperationSettingsVO();
        vo.setShopId(setting.getShopId());
        vo.setAutoReplyEnabled(isAutoReplyEnabled(setting));
        return vo;
    }

    private void appendHistory(MtImAutoReplySetting before,
                               MtImAutoReplySetting after,
                               String changeSource,
                               String changeReason,
                               LocalDateTime now) {
        Integer currentMaxVersion = mtImAutoReplySettingHistoryMapper.selectMaxVersionNoByShopId(after.getShopId());
        MtImAutoReplySettingHistory history = new MtImAutoReplySettingHistory();
        history.setSettingId(after.getId());
        history.setShopId(after.getShopId());
        history.setVersionNo((currentMaxVersion == null ? 0 : currentMaxVersion) + 1);
        history.setAutoReplyEnabled(Boolean.TRUE.equals(after.getAutoReplyEnabled()));
        history.setChangeSource(changeSource == null || changeSource.isBlank() ? CHANGE_SOURCE_SYSTEM : changeSource);
        history.setOperatorUserId(UserContext.getUserId());
        history.setOperatorName(UserContext.getUserName());
        history.setChangeReason(changeReason);
        history.setBeforeSnapshotJson(JsonUtil.toJson(toVO(before)));
        history.setAfterSnapshotJson(JsonUtil.toJson(toVO(after)));
        history.setCreateTime(now);
        mtImAutoReplySettingHistoryMapper.insert(history);
    }
}
