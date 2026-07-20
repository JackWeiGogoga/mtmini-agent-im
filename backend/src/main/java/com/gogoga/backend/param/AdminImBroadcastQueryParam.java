package com.gogoga.backend.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 管理后台群发批次查询参数。
 */
@Data
public class AdminImBroadcastQueryParam {
    private Long shopId;
    private String shopKeyword;
    private String status;
    private String repeatMode;
    private String contentKeyword;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
