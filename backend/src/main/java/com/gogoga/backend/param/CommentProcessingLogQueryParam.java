package com.gogoga.backend.param;

import lombok.Data;

/**
 * 管理后台评价处理日志查询参数。
 */
@Data
public class CommentProcessingLogQueryParam {

    private String commentTimeFrom;
    private String commentTimeTo;
    private String poiId;
    private String shopKeyword;
    private Long commentId;
    private String keyword;
    private String sentiment;
    private String autoReplyStatus;
    private String complaintStatus;
    private String imFollowUpStatus;
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}
