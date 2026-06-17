package com.gogoga.backend.dto;

import lombok.Data;

/**
 * 算力消费请求
 *
 * @author system
 * @since 2026-03-23
 */
@Data
public class PowerConsumeRequestDTO {

    /**
     * 店铺 ID
     */
    private Long shopId;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源 ID
     */
    private String sourceId;

    /**
     * 幂等请求号
     */
    private String requestNo;

    /**
     * 消费算力
     */
    private Integer powerAmount;

    /**
     * 备注
     */
    private String remark;
}
