package com.gogoga.backend.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台有赞订购订单查询参数
 */
@Data
public class AdminYouzanAppOrderQueryParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    private Long kdtId;

    private String shopName;

    private String buyerKeyword;

    private String skuKeyword;

    private Integer status;

    private String useStatus;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payStartTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payEndTime;

    private Integer pageNum;

    private Integer pageSize;
}
