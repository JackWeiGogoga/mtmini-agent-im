package com.gogoga.backend.param;

import lombok.Data;

@Data
public class UserBehaviorAnalyticsQueryParam {

    private String keyword;

    private String statsRange;

    private String sortField;

    private String sortOrder;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
