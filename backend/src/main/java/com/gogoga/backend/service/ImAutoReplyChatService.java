package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.dto.ImAutoReplyChatResult;
import com.gogoga.backend.exception.BusinessException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面向 IM 自动回复的 Chat 请求组装与响应解析。
 */
@Service
@RequiredArgsConstructor
public class ImAutoReplyChatService {

    private final MtProperties mtProperties;
    private final OpenAiCompatibleChatService chatService;

    public ImAutoReplyChatResult generate(String systemPrompt,
                                          String ragContext,
                                          List<ImAutoReplyChatMessage> history) {
        MtProperties.ImAutoReply config = mtProperties.getImAutoReply();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(config.getChatModel())
                .temperature(config.getTemperature())
                .maxCompletionTokens(Math.max(config.getMaxCompletionTokens(), 1))
                .addSystemMessage(systemPrompt);
        if (StringUtils.isNotBlank(ragContext)) {
            builder.addSystemMessage(ragContext);
        }
        addHistory(builder, history);
        return parse(chatService.createChatCompletion(
                config.getModelProvider(), config.getModelBaseUrl(), config.getModelApiKey(), builder.build()));
    }

    public ImAutoReplyChatResult rewrite(String systemPrompt,
                                         String ragContext,
                                         List<ImAutoReplyChatMessage> history,
                                         String unsafeReply) {
        MtProperties.ImAutoReply config = mtProperties.getImAutoReply();
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(config.getChatModel())
                .temperature(config.getTemperature())
                .maxCompletionTokens(Math.max(config.getMaxCompletionTokens(), 1))
                .addSystemMessage(systemPrompt);
        if (StringUtils.isNotBlank(ragContext)) {
            builder.addSystemMessage(ragContext);
        }
        addHistory(builder, history);
        builder.addAssistantMessage(StringUtils.defaultString(unsafeReply));
        builder.addUserMessage("上一版答复未通过内容安全检查。请保留有用事实，重新给出合规、简洁的客服答复，不解释改写过程。");
        return parse(chatService.createChatCompletion(
                config.getModelProvider(), config.getModelBaseUrl(), config.getModelApiKey(), builder.build()));
    }

    private void addHistory(ChatCompletionCreateParams.Builder builder, List<ImAutoReplyChatMessage> history) {
        if (history == null) {
            return;
        }
        for (ImAutoReplyChatMessage message : history) {
            if (message == null || StringUtils.isBlank(message.content())) {
                continue;
            }
            if ("assistant".equals(message.role())) {
                builder.addAssistantMessage(message.content());
            } else {
                builder.addUserMessage(message.content());
            }
        }
    }

    private ImAutoReplyChatResult parse(ChatCompletion completion) {
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new BusinessException(502, "Chat 服务响应缺少候选答复");
        }
        String content = completion.choices().get(0).message().content().orElse(null);
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(502, "Chat 服务响应正文为空");
        }
        CompletionUsage usage = completion.usage().orElseThrow(
                () -> new BusinessException(502, "Chat 服务响应缺少 token 用量"));
        return new ImAutoReplyChatResult(
                content.trim(), safeInt(usage.promptTokens()), safeInt(usage.completionTokens()));
    }

    private int safeInt(long value) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new BusinessException(502, "Chat 服务 token 用量非法");
        }
        return (int) value;
    }
}
