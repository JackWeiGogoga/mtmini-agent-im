package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImBroadcastSetting;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.enums.ImBroadcastRepeatModeEnum;
import com.gogoga.backend.enums.ImBroadcastTaskStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.service.async.MtImBroadcastPlanTaskPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 群发定时调度：扫描已启用配置，到点建立批次并投递编排任务。
 *
 * <p>调度本身只做判断与建批次，扫描目标人群这类重活交给异步任务，保证每分钟一轮扫描足够轻。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtImBroadcastScheduleService {

    public static final String SCHEDULE_LOCK_NAME = "mt-im-broadcast-schedule";

    private final MtProperties mtProperties;
    private final MtImBroadcastSettingMapper settingMapper;
    private final MtImBroadcastTaskMapper taskMapper;
    private final MtAsyncTaskService asyncTaskService;

    public void dispatchDueBroadcasts() {
        if (!mtProperties.getImBroadcast().isEnabled()) {
            return;
        }
        int pageSize = Math.max(mtProperties.getImBroadcast().getSchedulePageSize(), 1);
        LocalDateTime now = LocalDateTime.now();
        long afterId = 0L;
        int dispatched = 0;

        while (true) {
            List<MtImBroadcastSetting> page = settingMapper.selectEnabledAfterId(afterId, pageSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (MtImBroadcastSetting setting : page) {
                afterId = setting.getId();
                try {
                    if (tryTrigger(setting, now)) {
                        dispatched++;
                    }
                } catch (RuntimeException e) {
                    // 单个门店触发失败不应中断整轮调度。
                    log.error("群发触发失败，shopId={}, settingId={}", setting.getShopId(), setting.getId(), e);
                }
            }
            if (page.size() < pageSize) {
                break;
            }
        }
        if (dispatched > 0) {
            log.info("群发调度本轮触发批次数，count={}", dispatched);
        }
    }

    private boolean tryTrigger(MtImBroadcastSetting setting, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (today.equals(setting.getLastTriggerDate())) {
            return false;
        }
        if (!matchesWeekDay(setting.getWeekDays(), today)) {
            return false;
        }
        if (setting.getSendTime() == null || now.toLocalTime().isBefore(dueTime(setting))) {
            return false;
        }

        // 抢占当天的触发权：并发实例中只有一个 UPDATE 能命中旧水位。
        if (settingMapper.markTriggered(setting.getId(), today, setting.getLastTriggerDate()) <= 0) {
            return false;
        }

        MtImBroadcastTask task = buildTask(setting, today);
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            log.info("群发批次已存在，跳过重复创建，shopId={}, triggerDate={}", setting.getShopId(), today);
            return false;
        }
        settingMapper.bindLastTask(setting.getId(), task.getId());

        if (ImBroadcastRepeatModeEnum.ONCE.name().equals(setting.getRepeatMode())) {
            // 「一次」发完即失效，在建批次时就关闭，避免后续轮次重复触发。
            settingMapper.disable(setting.getId());
        }

        asyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.IM_BROADCAST_PLAN,
                "IM_BROADCAST_PLAN:" + task.getId(),
                String.valueOf(setting.getShopId()),
                new MtImBroadcastPlanTaskPayload(task.getId(), setting.getShopId(), setting.getId()),
                LocalDateTime.now(),
                mtProperties.getImBroadcast().getPlanTask().getMaxRetryCount()
        );
        log.info("群发批次已创建，broadcastTaskId={}, shopId={}, triggerDate={}, planTime={}",
                task.getId(), setting.getShopId(), today, setting.getSendTime());
        return true;
    }

    /**
     * 按 shopId 哈希把触发点打散若干秒，削平大量门店同配整点时间造成的洪峰。
     */
    private LocalTime dueTime(MtImBroadcastSetting setting) {
        int jitterRange = Math.max(mtProperties.getImBroadcast().getTriggerJitterSeconds(), 0);
        if (jitterRange == 0) {
            return setting.getSendTime();
        }
        long jitter = Math.floorMod(setting.getShopId(), jitterRange);
        return setting.getSendTime().plusSeconds(jitter);
    }

    private boolean matchesWeekDay(String weekDays, LocalDate today) {
        if (StringUtils.isBlank(weekDays)) {
            return false;
        }
        String target = String.valueOf(today.getDayOfWeek().getValue());
        for (String day : weekDays.split(",")) {
            if (target.equals(day.trim())) {
                return true;
            }
        }
        return false;
    }

    private MtImBroadcastTask buildTask(MtImBroadcastSetting setting, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();
        MtImBroadcastTask task = new MtImBroadcastTask();
        task.setShopId(setting.getShopId());
        task.setSettingId(setting.getId());
        task.setTriggerDate(today);
        task.setPlanTime(setting.getSendTime());
        task.setContent(setting.getContent());
        task.setWeekDays(setting.getWeekDays());
        task.setRepeatMode(setting.getRepeatMode());
        task.setStatus(ImBroadcastTaskStatusEnum.PENDING.name());
        task.setTargetCount(0);
        task.setTruncatedCount(0);
        task.setShardCount(0);
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        return task;
    }
}
