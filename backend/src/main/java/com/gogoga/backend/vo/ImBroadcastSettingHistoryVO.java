package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群发配置变更历史视图。
 */
@Data
public class ImBroadcastSettingHistoryVO {

    private Long id;

    private Long shopId;

    private Integer versionNo;

    /**
     * SAVE=保存配置；DELETE=清空配置。
     */
    private String changeType;

    private String changeTypeName;

    /**
     * H5/ADMIN/SYSTEM。
     */
    private String changeSource;

    private Boolean enabled;

    /**
     * 变更后触发星期，1=周一 … 7=周日；DELETE 记录里是被删掉那一版的值。
     */
    private List<Integer> weekDays;

    private String repeatMode;

    /**
     * HH:mm:ss。
     */
    private String sendTime;

    private String content;

    private String operatorUserId;

    private String operatorName;

    private LocalDateTime createTime;
}
