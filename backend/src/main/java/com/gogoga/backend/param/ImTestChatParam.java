package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 知识库测试聊天请求：无状态生成，对话历史由前端持有并随请求带上。
 */
@Data
public class ImTestChatParam {

    /**
     * 测试会话ID：前端每个对话生成一次，用于记录按会话分组。
     */
    @Size(max = 64, message = "会话ID过长")
    private String sessionId;

    /**
     * 本轮顾客输入文本。
     */
    @NotBlank(message = "测试消息不能为空")
    @Size(max = 2000, message = "测试消息过长")
    private String message;

    /**
     * 之前的对话历史（不含本轮），按时间正序。
     */
    private List<HistoryMessage> history;

    @Data
    public static class HistoryMessage {

        /**
         * user=顾客，assistant=AI客服。
         */
        private String role;

        private String content;
    }
}
