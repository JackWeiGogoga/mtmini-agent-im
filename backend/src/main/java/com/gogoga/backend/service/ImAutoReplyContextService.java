package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.ImAutoReplyChatMessage;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.mapper.MtImMessageMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 只读取当前 Session，构建最多 100 轮且受字符预算约束的短期上下文。
 */
@Service
@RequiredArgsConstructor
public class ImAutoReplyContextService {

    private final MtProperties mtProperties;
    private final MtImMessageMapper messageMapper;

    public Context build(Long sessionId) {
        int maxRounds = Math.max(mtProperties.getImAutoReply().getHistoryMaxRounds(), 1);
        int maxMessages = Math.min(maxRounds * 4, 1000);
        List<MtImMessage> newestFirst = messageMapper.selectRecentBySession(sessionId, maxMessages);
        if (newestFirst == null || newestFirst.isEmpty()) {
            return new Context(List.of(), List.of());
        }
        int remainingChars = Math.max(mtProperties.getImAutoReply().getMaxContextChars(), 1);
        int selectedUserRounds = 0;
        List<MtImMessage> selected = new ArrayList<>();
        for (MtImMessage message : newestFirst) {
            String content = StringUtils.trimToNull(message.getMsgContentPlaintext());
            if (content == null) {
                continue;
            }
            if (!selected.isEmpty() && content.length() > remainingChars) {
                break;
            }
            MtImMessage copy = new MtImMessage();
            copy.setSenderRole(message.getSenderRole());
            copy.setMsgContentPlaintext(content.length() > remainingChars
                    ? StringUtils.right(content, remainingChars) : content);
            selected.add(copy);
            remainingChars -= copy.getMsgContentPlaintext().length();
            if (ImMessageSenderRoleEnum.USER.name().equals(message.getSenderRole())) {
                selectedUserRounds++;
            }
            if (remainingChars <= 0 || selectedUserRounds >= maxRounds) {
                break;
            }
        }
        Collections.reverse(selected);
        List<ImAutoReplyChatMessage> chatMessages = new ArrayList<>();
        List<String> userMessages = new ArrayList<>();
        for (MtImMessage message : selected) {
            if (ImMessageSenderRoleEnum.USER.name().equals(message.getSenderRole())) {
                chatMessages.add(new ImAutoReplyChatMessage("user", message.getMsgContentPlaintext()));
                userMessages.add(message.getMsgContentPlaintext());
            } else if (ImMessageSenderRoleEnum.AI.name().equals(message.getSenderRole())
                    || ImMessageSenderRoleEnum.MERCHANT.name().equals(message.getSenderRole())) {
                chatMessages.add(new ImAutoReplyChatMessage("assistant", message.getMsgContentPlaintext()));
            }
        }
        return new Context(List.copyOf(chatMessages), List.copyOf(userMessages));
    }

    public record Context(List<ImAutoReplyChatMessage> messages, List<String> userMessages) {
        public List<String> latestUserMessages(int limit) {
            if (userMessages == null || userMessages.isEmpty() || limit <= 0) {
                return List.of();
            }
            return userMessages.subList(Math.max(userMessages.size() - limit, 0), userMessages.size());
        }
    }
}
