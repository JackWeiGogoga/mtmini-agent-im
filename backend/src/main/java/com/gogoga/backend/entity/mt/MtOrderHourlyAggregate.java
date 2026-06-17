package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 门店订单小时聚合结果
 */
@Data
public class MtOrderHourlyAggregate {

    private LocalDate orderDate;

    private Integer hourBucket;

    private Integer orderCount;

    private BigDecimal incomeAmount;
}
