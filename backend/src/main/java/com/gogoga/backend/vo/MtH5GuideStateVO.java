package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * H5 引导状态视图对象
 *
 * @author system
 * @since 2026-05-12
 */
@Data
public class MtH5GuideStateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String guideCode;

    private Boolean completed;

    private LocalDateTime completedAt;
}
