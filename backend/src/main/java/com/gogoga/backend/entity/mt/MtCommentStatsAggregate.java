package com.gogoga.backend.entity.mt;

import lombok.Data;

/**
 * 门店评价统计聚合结果。
 */
@Data
public class MtCommentStatsAggregate {

    private Integer goodCommentCount;

    private Integer neutralCommentCount;

    private Integer badCommentCount;

    private Integer repliedBadCommentCount;
}
