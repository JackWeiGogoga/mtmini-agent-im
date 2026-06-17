package com.gogoga.backend.service.async;

import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动调价异步任务载荷。
 */
@Data
public class MtPromotionAutoBidTaskPayload {

    private Long shopId;

    private LocalDateTime slotTime;

    private MtPromotionAutoBidCandidate candidate;
}
