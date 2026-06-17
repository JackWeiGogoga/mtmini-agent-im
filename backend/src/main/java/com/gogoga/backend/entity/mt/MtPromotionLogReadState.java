package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店推广日志已读状态
 *
 * @author system
 * @since 2026-05-11
 */
@Data
public class MtPromotionLogReadState {

    private Long id;

    private Long shopId;

    private Long lastReadRecordId;

    private LocalDateTime lastReadAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
