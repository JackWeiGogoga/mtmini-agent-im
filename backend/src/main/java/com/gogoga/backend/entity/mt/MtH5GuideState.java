package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * H5 门店引导状态
 *
 * @author system
 * @since 2026-05-12
 */
@Data
public class MtH5GuideState {

    private Long id;

    private Long shopId;

    private String guideCode;

    private Boolean completed;

    private LocalDateTime completedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
