package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.entity.mt.MtImBroadcastSetting;
import com.gogoga.backend.entity.mt.MtImBroadcastSettingHistory;
import com.gogoga.backend.enums.ImBroadcastRepeatModeEnum;
import com.gogoga.backend.enums.ImBroadcastSettingStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImBroadcastSettingHistoryMapper;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.param.ImBroadcastSettingUpdateParam;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.ImBroadcastSettingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 门店 IM 定时群发配置服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImBroadcastSettingService {

    public static final String CHANGE_TYPE_SAVE = "SAVE";
    public static final String CHANGE_TYPE_DELETE = "DELETE";
    public static final String CHANGE_SOURCE_H5 = "H5";
    public static final String CHANGE_SOURCE_ADMIN = "ADMIN";
    public static final String CHANGE_SOURCE_SYSTEM = "SYSTEM";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MIN_WEEK_DAY = 1;
    private static final int MAX_WEEK_DAY = 7;

    private final MtImBroadcastSettingMapper settingMapper;
    private final MtImBroadcastSettingHistoryMapper historyMapper;
    private final SensitiveWordService sensitiveWordService;
    private final MtProperties mtProperties;

    public ImBroadcastSettingVO getSettings(Long shopId) {
        return toVO(shopId, settingMapper.selectByShopId(shopId));
    }

    /**
     * 保存门店群发配置。同一门店只保留一条，重复保存即覆盖并重新启用。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImBroadcastSettingVO saveSettings(Long shopId, ImBroadcastSettingUpdateParam param, String changeSource) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        String weekDays = normalizeWeekDays(param.getWeekDays());
        ImBroadcastRepeatModeEnum repeatMode = ImBroadcastRepeatModeEnum.fromName(param.getRepeatMode())
                .orElseThrow(() -> new BusinessException("发送频率不合法"));
        LocalTime sendTime = parseSendTime(param.getSendTime());
        String content = normalizeContent(param.getContent());

        LocalDateTime now = LocalDateTime.now();
        MtImBroadcastSetting existing = settingMapper.selectByShopId(shopId);
        // 下面会原地改写 existing，快照必须在此之前取，否则 before 与 after 会是同一份数据。
        ImBroadcastSettingVO before = toVO(shopId, existing);
        MtImBroadcastSetting setting = existing == null ? new MtImBroadcastSetting() : existing;
        setting.setShopId(shopId);
        setting.setEnabled(true);
        setting.setWeekDays(weekDays);
        setting.setRepeatMode(repeatMode.name());
        setting.setSendTime(sendTime);
        setting.setContent(content);
        setting.setOperatorUserId(UserContext.getUserId());
        setting.setOperatorName(UserContext.getUserName());
        setting.setUpdateTime(now);
        // 重新配置视为一次全新的定时计划，清空触发水位以免当天被误判为已触发。
        // 但若配置的时间点今天已经过去，必须把水位落在今天，否则下一轮扫描会判定「已到点」立刻补发一批。
        setting.setLastTriggerDate(sendTime.isAfter(now.toLocalTime()) ? null : now.toLocalDate());

        if (setting.getId() == null) {
            setting.setTotalSentCount(0L);
            setting.setCreateTime(now);
            settingMapper.insert(setting);
        } else if (settingMapper.update(setting) <= 0) {
            throw new BusinessException("保存群发配置失败");
        }
        ImBroadcastSettingVO after = toVO(shopId, setting);
        appendHistory(setting, CHANGE_TYPE_SAVE, changeSource, before, after, now);
        log.info("门店群发配置已保存，shopId={}, weekDays={}, repeatMode={}, sendTime={}",
                shopId, weekDays, repeatMode.name(), sendTime);
        return after;
    }

    /**
     * 删除定时群发配置，清空当前定时任务。
     *
     * <p>只清空计划、不删除行，累计群发条数属于门店的历史口径，不随配置增删归零。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ImBroadcastSettingVO deleteSettings(Long shopId, String changeSource) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        MtImBroadcastSetting existing = settingMapper.selectByShopId(shopId);
        ImBroadcastSettingVO before = toVO(shopId, existing);
        settingMapper.clearSchedule(shopId);
        ImBroadcastSettingVO after = getSettings(shopId);
        if (existing != null) {
            // 删除会把字段清空，历史行里记的是被删掉的那一版配置，否则删除记录将毫无信息量。
            MtImBroadcastSetting deleted = new MtImBroadcastSetting();
            deleted.setId(existing.getId());
            deleted.setShopId(shopId);
            deleted.setEnabled(false);
            deleted.setWeekDays(existing.getWeekDays());
            deleted.setRepeatMode(existing.getRepeatMode());
            deleted.setSendTime(existing.getSendTime());
            deleted.setContent(existing.getContent());
            appendHistory(deleted, CHANGE_TYPE_DELETE, changeSource, before, after, LocalDateTime.now());
        }
        log.info("门店群发配置已清空，shopId={}", shopId);
        return after;
    }

    /**
     * 供首页展示的累计成功群发条数。
     */
    public long getTotalSentCount(Long shopId) {
        if (shopId == null) {
            return 0L;
        }
        MtImBroadcastSetting setting = settingMapper.selectByShopId(shopId);
        return setting == null || setting.getTotalSentCount() == null ? 0L : setting.getTotalSentCount();
    }

    public List<Integer> parseWeekDays(String weekDays) {
        if (StringUtils.isBlank(weekDays)) {
            return Collections.emptyList();
        }
        return Arrays.stream(weekDays.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }

    private String normalizeWeekDays(List<Integer> weekDays) {
        if (weekDays == null || weekDays.isEmpty()) {
            throw new BusinessException("请至少选择一个发送日期");
        }
        TreeSet<Integer> distinct = new TreeSet<>();
        for (Integer day : weekDays) {
            if (day == null || day < MIN_WEEK_DAY || day > MAX_WEEK_DAY) {
                throw new BusinessException("发送日期不合法");
            }
            distinct.add(day);
        }
        return distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private LocalTime parseSendTime(String sendTime) {
        try {
            return LocalTime.parse(StringUtils.trim(sendTime), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException("发送时间格式应为 HH:mm:ss");
        }
    }

    /**
     * 内容在配置阶段就做敏感词拦截，避免到点后整批跳过、商家却无感知。
     */
    private String normalizeContent(String content) {
        String trimmed = StringUtils.trim(content);
        if (StringUtils.isBlank(trimmed)) {
            throw new BusinessException("请填写发送的内容");
        }
        int maxLength = mtProperties.getImBroadcast().getMaxContentLength();
        if (trimmed.length() > maxLength) {
            throw new BusinessException("发送内容最多" + maxLength + "字");
        }
        SensitiveWordHitDTO hit = sensitiveWordService.detect(trimmed);
        if (hit != null && hit.isHit()) {
            throw new BusinessException("发送内容包含敏感信息，请修改后再试");
        }
        return trimmed;
    }

    private void appendHistory(MtImBroadcastSetting setting,
                               String changeType,
                               String changeSource,
                               ImBroadcastSettingVO before,
                               ImBroadcastSettingVO after,
                               LocalDateTime now) {
        Integer maxVersion = historyMapper.selectMaxVersionNoByShopId(setting.getShopId());
        MtImBroadcastSettingHistory history = new MtImBroadcastSettingHistory();
        history.setSettingId(setting.getId());
        history.setShopId(setting.getShopId());
        history.setVersionNo((maxVersion == null ? 0 : maxVersion) + 1);
        history.setChangeType(changeType);
        history.setChangeSource(StringUtils.isBlank(changeSource) ? CHANGE_SOURCE_SYSTEM : changeSource);
        history.setEnabled(Boolean.TRUE.equals(setting.getEnabled()));
        history.setWeekDays(setting.getWeekDays());
        history.setRepeatMode(setting.getRepeatMode());
        history.setSendTime(setting.getSendTime());
        history.setContent(setting.getContent());
        history.setOperatorUserId(UserContext.getUserId());
        history.setOperatorName(UserContext.getUserName());
        history.setBeforeSnapshotJson(JsonUtil.toJson(before));
        history.setAfterSnapshotJson(JsonUtil.toJson(after));
        history.setCreateTime(now);
        historyMapper.insert(history);
    }

    private ImBroadcastSettingVO toVO(Long shopId, MtImBroadcastSetting setting) {
        ImBroadcastSettingVO vo = new ImBroadcastSettingVO();
        vo.setShopId(shopId);
        if (setting == null || setting.getId() == null) {
            vo.setConfigured(false);
            vo.setEnabled(false);
            vo.setWeekDays(new ArrayList<>());
            vo.setTotalSentCount(0L);
            applyStatus(vo, ImBroadcastSettingStatusEnum.NONE);
            return vo;
        }
        // 「一次」触发后会被置为 enabled=0，此时按钮回到未配置态（发送/取消），与手动删除表现一致；
        // 但字段值仍原样回显，商家再次打开弹窗能在上次配置的基础上改，而不是面对一张空表单。
        boolean enabled = Boolean.TRUE.equals(setting.getEnabled());
        vo.setConfigured(enabled);
        vo.setEnabled(enabled);
        vo.setWeekDays(parseWeekDays(setting.getWeekDays()));
        vo.setRepeatMode(setting.getRepeatMode());
        vo.setSendTime(setting.getSendTime() == null ? null : setting.getSendTime().format(TIME_FORMATTER));
        vo.setContent(setting.getContent());
        vo.setTotalSentCount(setting.getTotalSentCount() == null ? 0L : setting.getTotalSentCount());
        vo.setLastTriggerDate(setting.getLastTriggerDate());
        applyStatus(vo, resolveStatus(setting, enabled));
        return vo;
    }

    /**
     * 区分 {@code enabled=0} 的两种成因：显式删除会把星期与文案清空，「一次」失效则原样保留。
     */
    private ImBroadcastSettingStatusEnum resolveStatus(MtImBroadcastSetting setting, boolean enabled) {
        if (enabled) {
            return ImBroadcastSettingStatusEnum.ACTIVE;
        }
        boolean onceExecuted = ImBroadcastRepeatModeEnum.ONCE.name().equals(setting.getRepeatMode())
                && StringUtils.isNotBlank(setting.getWeekDays())
                && setting.getLastTriggerDate() != null;
        return onceExecuted ? ImBroadcastSettingStatusEnum.ONCE_COMPLETED : ImBroadcastSettingStatusEnum.NONE;
    }

    private void applyStatus(ImBroadcastSettingVO vo, ImBroadcastSettingStatusEnum status) {
        vo.setStatus(status.name());
        vo.setStatusName(status.getDesc());
    }
}
