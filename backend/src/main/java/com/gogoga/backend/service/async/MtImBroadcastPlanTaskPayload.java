package com.gogoga.backend.service.async;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MtImBroadcastPlanTaskPayload {
    private Long broadcastTaskId;
    private Long shopId;
    private Long settingId;
}
