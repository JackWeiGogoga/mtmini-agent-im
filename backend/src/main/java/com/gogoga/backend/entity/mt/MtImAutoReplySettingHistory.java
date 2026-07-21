package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店 IM 自动回复配置历史。
 */
@Data
public class MtImAutoReplySettingHistory {

    private Long id;
    private Long settingId;
    private Long shopId;
    private Integer versionNo;
    private Boolean autoReplyEnabled;
    private String changeSource;
    private String operatorUserId;
    private String operatorName;
    private String changeReason;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private LocalDateTime createTime;
}
