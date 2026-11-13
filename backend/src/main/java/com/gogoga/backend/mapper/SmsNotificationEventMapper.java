package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信通知事件 Mapper。
 */
@Mapper
public interface SmsNotificationEventMapper {

    SmsNotificationEvent selectById(@Param("id") Long id);

    SmsNotificationEvent selectBySceneAndBizKey(@Param("scene") String scene, @Param("bizKey") String bizKey);

    int insert(SmsNotificationEvent event);

    List<Long> selectExecutableIds(@Param("availableAt") LocalDateTime availableAt, @Param("limit") int limit);

    int claimPendingById(@Param("id") Long id,
                         @Param("workerId") String workerId,
                         @Param("claimedAt") LocalDateTime claimedAt);

    int markTerminal(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("finishedAt") LocalDateTime finishedAt,
                     @Param("lastError") String lastError);

    int rescheduleFromProcessing(@Param("id") Long id,
                                 @Param("retryCount") int retryCount,
                                 @Param("availableAt") LocalDateTime availableAt,
                                 @Param("lastError") String lastError,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    int markDead(@Param("id") Long id,
                 @Param("retryCount") int retryCount,
                 @Param("finishedAt") LocalDateTime finishedAt,
                 @Param("lastError") String lastError);

    List<SmsNotificationEvent> selectStaleProcessing(@Param("claimedBefore") LocalDateTime claimedBefore,
                                                     @Param("limit") int limit);
}
