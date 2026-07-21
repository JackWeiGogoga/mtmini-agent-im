package com.gogoga.backend.param;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class ImAutoReplyLogQueryParam {
    private Long shopId;
    private String shopKeyword;
    private Long openUserId;
    private Long orderId;
    private String status;
    private Boolean ragUsed;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
