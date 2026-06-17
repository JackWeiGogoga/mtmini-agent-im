package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UserShopBatchBindResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer totalCount;

    private Integer successCount;

    private Integer failureCount;

    private List<String> successPoiIds;

    private List<FailureItem> failureItems;

    @Data
    public static class FailureItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private String poiId;

        private String errorMessage;
    }
}
