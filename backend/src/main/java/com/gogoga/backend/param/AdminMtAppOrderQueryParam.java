package com.gogoga.backend.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台美团订阅订单查询参数
 */
@Data
public class AdminMtAppOrderQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;

    private String orderViewId;

    private String poiId;

    private String shopName;

    private Integer payStatus;

    private String rightStatus;

    private String version;

    private String versionName;

    private String targetUserId;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderStartTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderEndTime;

    private Integer pageNum;

    private Integer pageSize;
}
