package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 两方会话统一消息事实，既包含美团回调，也包含内部生成的 AI 回复。
 */
@Data
public class MtImMessage {

    private Long id;
    private Long shopId;
    private Long conversationId;
    private Long sessionId;
    private String recordSource;
    private String senderRole;
    private Long replyToMessageId;
    private Long autoReplyId;
    private String deliveryStatus;
    private LocalDateTime eventTime;
    private String callbackMsgId;
    private String callbackMsgType;
    private Long developerId;
    private Integer businessId;
    private String opBizCode;
    private String epoiId;
    private Long callbackTimestamp;
    private Boolean signVerified;
    private Integer bizType;
    private String extendedField;
    private String innerSig;
    private Long innerTimestamp;
    private Long platformMsgId;
    private Long appId;
    private String appPoiCode;
    private Integer msgType;
    private Integer msgSource;
    private Long messageCts;
    private Long orderId;
    private Long openUserId;
    private Integer sceneTag;
    private String sceneName;
    private Integer hasReply;
    private String msgContentCiphertext;
    private String msgContentPlaintext;
    private String msgContentHash;
    private String parseStatus;
    private String decryptStatus;
    private String errorCode;
    private String errorSummary;
    private String rawMessageJson;
    private String rawPushContentJson;
    private LocalDateTime receivedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
