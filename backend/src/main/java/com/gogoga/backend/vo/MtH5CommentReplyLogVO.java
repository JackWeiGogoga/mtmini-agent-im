package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * H5 评价自动回复日志。
 */
@Data
public class MtH5CommentReplyLogVO {

    private Long id;
    private Long commentId;
    private String commentTime;
    private LocalDateTime replyTime;
    private String sentiment;
    private Integer score;
    private String customerMessage;
    private String addComment;
    private List<String> imageUrls;
    private String aiReplyText;
    private String analysisText;
    private String autoReplyStatus;
}
