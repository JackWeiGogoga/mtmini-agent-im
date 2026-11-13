package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.sms.SmsNotificationDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信逐手机号投递 Mapper。
 */
@Mapper
public interface SmsNotificationDeliveryMapper {

    SmsNotificationDelivery selectById(@Param("id") Long id);

    List<SmsNotificationDelivery> selectByEventId(@Param("eventId") Long eventId);

    int insert(SmsNotificationDelivery delivery);

    List<Long> selectExecutableIds(@Param("availableAt") LocalDateTime availableAt, @Param("limit") int limit);

    int claimPendingById(@Param("id") Long id,
                         @Param("workerId") String workerId,
                         @Param("claimedAt") LocalDateTime claimedAt);

    int markSent(@Param("id") Long id,
                 @Param("providerRequestId") String providerRequestId,
                 @Param("providerSerialNo") String providerSerialNo,
                 @Param("providerCode") String providerCode,
                 @Param("providerMessage") String providerMessage,
                 @Param("sentAt") LocalDateTime sentAt);

    int markCancelled(@Param("id") Long id,
                      @Param("finishedAt") LocalDateTime finishedAt,
                      @Param("reason") String reason);

    int deferFromProcessing(@Param("id") Long id,
                            @Param("availableAt") LocalDateTime availableAt,
                            @Param("reason") String reason,
                            @Param("updatedAt") LocalDateTime updatedAt);

    int rescheduleFromProcessing(@Param("id") Long id,
                                 @Param("retryCount") int retryCount,
                                 @Param("availableAt") LocalDateTime availableAt,
                                 @Param("lastError") String lastError,
                                 @Param("providerCode") String providerCode,
                                 @Param("providerMessage") String providerMessage,
                                 @Param("updatedAt") LocalDateTime updatedAt);

    int markDead(@Param("id") Long id,
                 @Param("retryCount") int retryCount,
                 @Param("finishedAt") LocalDateTime finishedAt,
                 @Param("lastError") String lastError,
                 @Param("providerCode") String providerCode,
                 @Param("providerMessage") String providerMessage);

    List<SmsNotificationDelivery> selectStaleProcessing(@Param("claimedBefore") LocalDateTime claimedBefore,
                                                        @Param("limit") int limit);
}
