package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImBroadcastSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MtImBroadcastSettingMapper {

    int insert(MtImBroadcastSetting setting);

    int update(MtImBroadcastSetting setting);

    MtImBroadcastSetting selectById(@Param("id") Long id);

    MtImBroadcastSetting selectByShopId(@Param("shopId") Long shopId);

    /**
     * 清空定时计划但保留行本身，使 total_sent_count 这类累计口径不因删除配置而丢失。
     */
    int clearSchedule(@Param("shopId") Long shopId);

    /**
     * 分页扫描已启用配置，供调度任务按 id 游标遍历。
     */
    List<MtImBroadcastSetting> selectEnabledAfterId(@Param("afterId") Long afterId,
                                                    @Param("limit") int limit);

    /**
     * 由调度任务抢占触发权：仅当 last_trigger_date 仍是旧值时才更新，保证同一天只触发一次。
     */
    int markTriggered(@Param("id") Long id,
                      @Param("triggerDate") LocalDate triggerDate,
                      @Param("previousTriggerDate") LocalDate previousTriggerDate);

    int bindLastTask(@Param("id") Long id, @Param("taskId") Long taskId);

    int disable(@Param("id") Long id);

    int increaseTotalSentCount(@Param("id") Long id, @Param("delta") int delta);
}
