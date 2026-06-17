package com.gogoga.backend.param;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台美团订单结算统计查询参数。
 */
@Data
public class AdminMtAppOrderSettlementQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 统计月份，格式 yyyy-MM。
     */
    private String statsMonth;

    /**
     * 美团门店 ID。
     */
    private String poiId;

    private Integer pageNum;

    private Integer pageSize;
}
