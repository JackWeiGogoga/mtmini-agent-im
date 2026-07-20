package com.gogoga.backend.service.async;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MtImBroadcastSendTaskPayload {
    private Long broadcastTaskId;
    private Long shopId;
    private Integer shardNo;
}
