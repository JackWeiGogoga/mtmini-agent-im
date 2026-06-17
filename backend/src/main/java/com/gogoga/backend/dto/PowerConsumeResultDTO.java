package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 算力消费结果
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class PowerConsumeResultDTO {

    /**
     * 是否命中幂等
     */
    private boolean duplicate;

    /**
     * 消费后余额
     */
    private Integer balanceAfter;

    /**
     * 消费流水号
     */
    private String consumeNo;
}
