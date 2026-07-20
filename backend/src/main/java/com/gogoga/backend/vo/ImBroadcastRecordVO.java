package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台群发明细列表项。
 */
@Data
public class ImBroadcastRecordVO {
    private Long recordId;
    private Long taskId;
    private Long shopId;
    private Long conversationId;
    private Long openUserId;
    private Integer shardNo;
    private Long outboundMessageId;
    private String status;
    private String errorCode;
    private String errorSummary;
    private LocalDateTime sentAt;
    private LocalDateTime createTime;
}
