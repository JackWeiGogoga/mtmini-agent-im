package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 定时群发对单个顾客的发送明细。
 */
@Data
public class MtImBroadcastRecord {

    private Long id;
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
    private LocalDateTime updateTime;
}
