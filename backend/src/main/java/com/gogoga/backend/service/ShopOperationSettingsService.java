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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

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
    public static final String CHANGE_REASON_CONTACT_PHONE_UPDATED = "CONTACT_PHONE_UPDATED";
    private static final boolean DEFAULT_AUTO_REPLY_ENABLED = false;
    private static final int MAX_CONTACT_PHONE_LENGTH = 32;
    private static final int MIN_CONTACT_PHONE_DIGITS = 5;
    private static final Pattern CONTACT_PHONE_PATTERN = Pattern.compile("[0-9+\\-()（） ,#*]+");

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
        MtImAutoReplySetting before = requireSetting(shopId);
        boolean desiredEnabled = Boolean.TRUE.equals(param.getAutoReplyEnabled());
        if (isAutoReplyEnabled(before) == desiredEnabled) {
            return toVO(before);
        }
        MtImAutoReplySetting after = copySetting(before);
        after.setAutoReplyEnabled(desiredEnabled);
        return persist(before, after, changeSource, changeReason, "更新门店开关失败");
    }

    /**
     * 更新门店自定义联系方式；传空表示清除，回退到门店电话。
     */
    @Transactional(rollbackFor = Exception.class)
    public ShopOperationSettingsVO updateCustomContactPhone(Long shopId,
                                                            String customContactPhone,
                                                            String changeSource) {
        MtImAutoReplySetting before = requireSetting(shopId);
        String desiredPhone = normalizeContactPhone(customContactPhone);
        if (StringUtils.equals(StringUtils.trimToNull(before.getCustomContactPhone()), desiredPhone)) {
            return toVO(before);
        }
        MtImAutoReplySetting after = copySetting(before);
        after.setCustomContactPhone(desiredPhone);
        return persist(before, after, changeSource, CHANGE_REASON_CONTACT_PHONE_UPDATED, "更新门店联系方式失败");
    }

    /**
     * 解析门店实际生效的联系方式：自定义 &gt; 门店电话 &gt; 门店联系方式，都没有时返回 null。
     */
    public String resolveEffectiveContactPhone(Long shopId) {
        if (shopId == null) {
            return null;
        }
        return resolveEffectiveContactPhone(shopId, resolveSetting(shopId).getCustomContactPhone());
    }

    /**
     * 校验并归一化联系方式：只保留数字与常见分隔符号，且必须含足够位数，避免把说明文字当号码发给顾客。
     */
    private String normalizeContactPhone(String raw) {
        String trimmed = StringUtils.trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_CONTACT_PHONE_LENGTH) {
            throw new BusinessException("联系方式长度不能超过 " + MAX_CONTACT_PHONE_LENGTH + " 个字符");
        }
        if (!CONTACT_PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException("联系方式只能包含数字和 + - ( ) 空格 , # * 等符号");
        }
        long digits = trimmed.chars().filter(Character::isDigit).count();
        if (digits < MIN_CONTACT_PHONE_DIGITS) {
            throw new BusinessException("联系方式至少需要 " + MIN_CONTACT_PHONE_DIGITS + " 位数字");
        }
        return trimmed;
    }

    private MtImAutoReplySetting requireSetting(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        if (mtShopMapper.selectById(shopId) == null) {
            throw new BusinessException("门店不存在");
        }
        return resolveSetting(shopId);
    }

    private ShopOperationSettingsVO persist(MtImAutoReplySetting before,
                                            MtImAutoReplySetting after,
                                            String changeSource,
                                            String changeReason,
                                            String failureMessage) {
        LocalDateTime now = LocalDateTime.now();
        after.setUpdateTime(now);
        if (after.getId() == null) {
            after.setCreateTime(now);
            mtImAutoReplySettingMapper.insert(after);
        } else {
            int updated = mtImAutoReplySettingMapper.updateById(after);
            if (updated <= 0) {
                throw new BusinessException(failureMessage);
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
        copy.setCustomContactPhone(source.getCustomContactPhone());
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
        vo.setCustomContactPhone(StringUtils.trimToNull(setting.getCustomContactPhone()));
        vo.setEffectiveContactPhone(
                resolveEffectiveContactPhone(setting.getShopId(), setting.getCustomContactPhone()));
        return vo;
    }

    /**
     * 自定义 &gt; 门店电话(phone) &gt; 门店联系方式(contact_phone)。
     *
     * <p>自定义值已在写入时校验过，门店电话来自美团同步与商家资料，可能是任意文本，
     * 这里同样只接受能当号码用的内容，避免把脏数据拼进发给顾客的回复里。</p>
     */
    private String resolveEffectiveContactPhone(Long shopId, String customContactPhone) {
        String custom = StringUtils.trimToNull(customContactPhone);
        if (custom != null) {
            return custom;
        }
        if (shopId == null) {
            return null;
        }
        MtShop shop = mtShopMapper.selectById(shopId);
        if (shop == null) {
            return null;
        }
        String phone = usableShopPhone(shop.getPhone());
        return phone != null ? phone : usableShopPhone(shop.getContactPhone());
    }

    private String usableShopPhone(String raw) {
        String trimmed = StringUtils.trimToNull(raw);
        if (trimmed == null || trimmed.length() > MAX_CONTACT_PHONE_LENGTH
                || !CONTACT_PHONE_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.chars().filter(Character::isDigit).count() >= MIN_CONTACT_PHONE_DIGITS ? trimmed : null;
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
