package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImMessage;
import com.gogoga.backend.enums.ImMessageSenderRoleEnum;
import com.gogoga.backend.mapper.MtImMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImAutoReplyContextServiceTest {

    @Mock private MtImMessageMapper messageMapper;

    @Test
    void keepsAtMostOneHundredRoundsFromCurrentSession() {
        MtProperties properties = new MtProperties();
        properties.getImAutoReply().setHistoryMaxRounds(100);
        properties.getImAutoReply().setMaxContextChars(100000);
        List<MtImMessage> newestFirst = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            MtImMessage message = new MtImMessage();
            message.setSenderRole(i % 2 == 0
                    ? ImMessageSenderRoleEnum.USER.name() : ImMessageSenderRoleEnum.AI.name());
            message.setMsgContentPlaintext("消息" + i);
            newestFirst.add(message);
        }
        when(messageMapper.selectRecentBySession(9L, 400)).thenReturn(newestFirst);

        ImAutoReplyContextService.Context context = new ImAutoReplyContextService(properties, messageMapper).build(9L);

        // 最新用户消息加 99 组历史问答；最老用户消息之前的孤立 AI 回复不会进入上下文。
        assertEquals(199, context.messages().size());
        assertEquals(100, context.userMessages().size());
        verify(messageMapper).selectRecentBySession(9L, 400);
    }

    @Test
    void cropsOldestMessagesByCharacterBudget() {
        MtProperties properties = new MtProperties();
        properties.getImAutoReply().setMaxContextChars(5);
        MtImMessage newest = message(ImMessageSenderRoleEnum.USER, "1234");
        MtImMessage older = message(ImMessageSenderRoleEnum.AI, "5678");
        when(messageMapper.selectRecentBySession(9L, 400)).thenReturn(List.of(newest, older));

        ImAutoReplyContextService.Context context = new ImAutoReplyContextService(properties, messageMapper).build(9L);

        assertEquals(1, context.messages().size());
        assertEquals("1234", context.messages().get(0).content());
    }

    @Test
    void limitsConsecutiveUserMessagesToConfiguredRoundCount() {
        MtProperties properties = new MtProperties();
        properties.getImAutoReply().setHistoryMaxRounds(3);
        properties.getImAutoReply().setMaxContextChars(1000);
        List<MtImMessage> newestFirst = List.of(
                message(ImMessageSenderRoleEnum.USER, "最新"),
                message(ImMessageSenderRoleEnum.USER, "第二条"),
                message(ImMessageSenderRoleEnum.USER, "第三条"),
                message(ImMessageSenderRoleEnum.USER, "不应进入上下文")
        );
        when(messageMapper.selectRecentBySession(9L, 12)).thenReturn(newestFirst);

        ImAutoReplyContextService.Context context = new ImAutoReplyContextService(properties, messageMapper).build(9L);

        assertEquals(3, context.userMessages().size());
        assertEquals("第三条", context.userMessages().get(0));
        assertEquals("最新", context.userMessages().get(2));
    }

    private MtImMessage message(ImMessageSenderRoleEnum role, String content) {
        MtImMessage message = new MtImMessage();
        message.setSenderRole(role.name());
        message.setMsgContentPlaintext(content);
        return message;
    }
}
