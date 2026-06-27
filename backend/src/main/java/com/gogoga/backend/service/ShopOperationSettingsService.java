package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtCommentReplySetting;
import com.gogoga.backend.entity.mt.MtCommentReplySettingHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtCommentReplySettingHistoryMapper;
import com.gogoga.backend.mapper.MtCommentReplySettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 门店操作开关服务。
 */
@Service
@RequiredArgsConstructor
public class ShopOperationSettingsService {

    public static final String CHANGE_SOURCE_H5 = "H5";
    public static final String CHANGE_SOURCE_ADMIN = "ADMIN";
    public static final String CHANGE_SOURCE_SYSTEM = "SYSTEM";

    private final MtShopMapper mtShopMapper;
    private final MtCommentReplySettingMapper mtCommentReplySettingMapper;
    private final MtCommentReplySettingHistoryMapper mtCommentReplySettingHistoryMapper;

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

    public boolean isAutoReplyEnabled(MtCommentReplySetting setting) {
        return setting == null || !Boolean.FALSE.equals(setting.getAutoReplyEnabled());
    }

    public boolean isNegativeCommentAppealEnabled(Long shopId) {
        MtCommentReplySetting setting = resolveSetting(shopId);
        return setting != null && Boolean.TRUE.equals(setting.getNegativeCommentAppealEnabled());
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
        MtCommentReplySetting before = resolveSetting(shopId);
        MtCommentReplySetting after = copySetting(before);
        after.setAutoReplyEnabled(Boolean.TRUE.equals(param.getAutoReplyEnabled()));
        after.setNegativeCommentAppealEnabled(Boolean.TRUE.equals(param.getNegativeCommentAppealEnabled()));
        after.setUpdateTime(now);

        if (after.getId() == null) {
            after.setCreateTime(now);
            mtCommentReplySettingMapper.insert(after);
        } else {
            int updated = mtCommentReplySettingMapper.updateById(after);
            if (updated <= 0) {
                throw new BusinessException("更新门店开关失败");
            }
        }
        appendHistory(before, after, changeSource, changeReason, now);
        return toVO(after);
    }

    private MtCommentReplySetting resolveSetting(Long shopId) {
        if (shopId == null) {
            return null;
        }
        MtCommentReplySetting setting = mtCommentReplySettingMapper.selectByShopId(shopId);
        if (setting != null) {
            return setting;
        }
        MtCommentReplySetting defaults = new MtCommentReplySetting();
        defaults.setShopId(shopId);
        defaults.setAutoReplyEnabled(true);
        defaults.setNegativeCommentAppealEnabled(false);
        return defaults;
    }

    private MtCommentReplySetting copySetting(MtCommentReplySetting source) {
        MtCommentReplySetting copy = new MtCommentReplySetting();
        if (source == null) {
            copy.setAutoReplyEnabled(true);
            copy.setNegativeCommentAppealEnabled(false);
            return copy;
        }
        copy.setId(source.getId());
        copy.setShopId(source.getShopId());
        copy.setAutoReplyEnabled(source.getAutoReplyEnabled());
        copy.setNegativeCommentAppealEnabled(source.getNegativeCommentAppealEnabled());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateTime(source.getUpdateTime());
        return copy;
    }

    private ShopOperationSettingsVO toVO(MtCommentReplySetting setting) {
        if (setting == null) {
            return null;
        }
        ShopOperationSettingsVO vo = new ShopOperationSettingsVO();
        vo.setShopId(setting.getShopId());
        vo.setAutoReplyEnabled(isAutoReplyEnabled(setting));
        vo.setNegativeCommentAppealEnabled(Boolean.TRUE.equals(setting.getNegativeCommentAppealEnabled()));
        return vo;
    }

    private void appendHistory(MtCommentReplySetting before,
                               MtCommentReplySetting after,
                               String changeSource,
                               String changeReason,
                               LocalDateTime now) {
        Integer currentMaxVersion = mtCommentReplySettingHistoryMapper.selectMaxVersionNoByShopId(after.getShopId());
        MtCommentReplySettingHistory history = new MtCommentReplySettingHistory();
        history.setSettingId(after.getId());
        history.setShopId(after.getShopId());
        history.setVersionNo((currentMaxVersion == null ? 0 : currentMaxVersion) + 1);
        history.setAutoReplyEnabled(Boolean.TRUE.equals(after.getAutoReplyEnabled()));
        history.setNegativeCommentAppealEnabled(Boolean.TRUE.equals(after.getNegativeCommentAppealEnabled()));
        history.setChangeSource(changeSource == null || changeSource.isBlank() ? CHANGE_SOURCE_SYSTEM : changeSource);
        history.setOperatorUserId(UserContext.getUserId());
        history.setOperatorName(UserContext.getUserName());
        history.setChangeReason(changeReason);
        history.setBeforeSnapshotJson(JsonUtil.toJson(toVO(before)));
        history.setAfterSnapshotJson(JsonUtil.toJson(toVO(after)));
        history.setCreateTime(now);
        mtCommentReplySettingHistoryMapper.insert(history);
    }
}
