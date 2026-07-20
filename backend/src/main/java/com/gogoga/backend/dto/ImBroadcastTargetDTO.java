package com.gogoga.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群发目标人群游标扫描的投影结果。
 */
@Data
public class ImBroadcastTargetDTO {

    private Long conversationId;
    private Long openUserId;
    private LocalDateTime lastUserMessageAt;
}
