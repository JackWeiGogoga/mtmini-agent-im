package com.gogoga.backend.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ImAutoReplyLogVO {
    private Long autoReplyId;
    private Long shopId;
    private String shopName;
    private String poiId;
    private String epoiId;
    private Long conversationId;
    private Long sessionId;
    private Long openUserId;
    private Long orderId;
    private Long userMessageId;
    /** 顾客消息类型，见 ImMessageTypeEnum。图片消息的正文是图片 URL，前端据此渲染成图片而不是链接文本。 */
    private Integer userMsgType;
    private String userContent;
    /** 从顾客消息正文里识别出的手机号，查询时实时计算，不落库。 */
    private List<String> userPhones;
    private LocalDateTime userMessageTime;
    private Long aiMessageId;
    private String aiContent;
    private LocalDateTime aiMessageTime;
    private String deliveryStatus;
    private String status;
    private String skipReason;
    private Boolean ragUsed;
    private Integer ragChunkCount;
    private BigDecimal ragMaxScore;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer powerAmount;
    private Integer rewriteCount;
    private LocalDateTime sentAt;
    private String lastErrorCode;
    private LocalDateTime createTime;
}
