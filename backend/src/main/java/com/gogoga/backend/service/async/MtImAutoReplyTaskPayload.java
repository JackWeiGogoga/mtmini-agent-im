package com.gogoga.backend.service.async;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MtImAutoReplyTaskPayload {
    private Long autoReplyId;
    private Long triggerMessageId;
    private Long conversationId;
    private Long sessionId;
    private Long shopId;
}
