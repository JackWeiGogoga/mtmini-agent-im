package com.gogoga.backend.dto;

/**
 * 自动回复模型正文与实际 token 用量。
 */
public record ImAutoReplyChatResult(String content, int promptTokens, int completionTokens) {

    public ImAutoReplyChatResult plusUsage(ImAutoReplyChatResult other) {
        if (other == null) {
            return this;
        }
        return new ImAutoReplyChatResult(
                other.content(),
                promptTokens + other.promptTokens(),
                completionTokens + other.completionTokens()
        );
    }
}
