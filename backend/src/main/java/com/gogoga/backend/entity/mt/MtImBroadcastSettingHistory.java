package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 门店 IM 群发配置历史。
 */
@Data
public class MtImBroadcastSettingHistory {

    private Long id;
    private Long settingId;
    private Long shopId;
    private Integer versionNo;
    /**
     * SAVE=保存配置；DELETE=清空配置。
     */
    private String changeType;
    /**
     * H5/ADMIN/SYSTEM。
     */
    private String changeSource;
    private Boolean enabled;
    private String weekDays;
    private String repeatMode;
    private LocalTime sendTime;
    private String content;
    private String operatorUserId;
    private String operatorName;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDateTime createTime;
}
