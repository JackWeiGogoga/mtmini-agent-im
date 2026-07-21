package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtImConversation;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImConversationMapper;
import com.gogoga.backend.mapper.MtImReplyQueryMapper;
import com.gogoga.backend.param.ImAutoReplyLogQueryParam;
import com.gogoga.backend.vo.ImAutoReplyLogVO;
import com.gogoga.backend.vo.ImConversationHistoryPageVO;
import com.gogoga.backend.vo.ImConversationMessageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImReplyLogQueryServiceTest {

    @Mock private MtImReplyQueryMapper queryMapper;
    @Mock private MtImConversationMapper conversationMapper;

    @Test
    void rejectsCrossShopConversationHistoryAccess() {
        MtImConversation conversation = new MtImConversation();
        conversation.setId(9L);
        conversation.setShopId(1L);
        when(conversationMapper.selectById(9L)).thenReturn(conversation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().history(9L, 2L, null, null, 30));

        assertEquals(403, exception.getCode());
        verify(queryMapper, never()).selectHistoryBefore(9L, null, null, 31);
    }

    @Test
    void returnsChronologicalMessagesWithOlderCompositeCursorAcrossSessions() {
        MtImConversation conversation = new MtImConversation();
        conversation.setId(9L);
        conversation.setShopId(1L);
        when(conversationMapper.selectById(9L)).thenReturn(conversation);
        LocalDateTime newestTime = LocalDateTime.of(2026, 7, 19, 12, 0);
        LocalDateTime olderTime = LocalDateTime.of(2026, 7, 19, 11, 0);
        ImConversationMessageVO newest = message(3L, 2L, newestTime);
        ImConversationMessageVO older = message(2L, 1L, olderTime);
        ImConversationMessageVO extra = message(1L, 1L, olderTime.minusMinutes(1));
        when(queryMapper.selectHistoryBefore(9L, null, null, 3)).thenReturn(List.of(newest, older, extra));

        ImConversationHistoryPageVO result = service().history(9L, 1L, null, null, 2);

        assertTrue(result.getHasMore());
        assertEquals(List.of(older, newest), result.getList());
        assertEquals(olderTime, result.getNextCursorTime());
        assertEquals(2L, result.getNextCursorId());
        assertEquals(1L, result.getList().get(0).getSessionId());
        assertEquals(2L, result.getList().get(1).getSessionId());
    }

    @Test
    void rejectsHalfCompositeCursor() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().history(9L, 1L, LocalDateTime.now(), null, 30));
        assertEquals(400, exception.getCode());
    }

    @Test
    void fillsCustomerPhonesOnEachReplyLogRow() {
        ImAutoReplyLogVO withPhone = new ImAutoReplyLogVO();
        withPhone.setUserContent("我的电话 138-0013-8000，麻烦快点");
        ImAutoReplyLogVO withoutPhone = new ImAutoReplyLogVO();
        withoutPhone.setUserContent("订单号 999138001380009999 到哪了");
        when(queryMapper.selectReplyLogs(any())).thenReturn(List.of(withPhone, withoutPhone));

        List<ImAutoReplyLogVO> rows = service().listReplyLogs(new ImAutoReplyLogQueryParam(), 1L).getList();

        assertEquals(List.of("13800138000"), rows.get(0).getUserPhones());
        assertEquals(List.of(), rows.get(1).getUserPhones());
    }

    private ImReplyLogQueryService service() {
        return new ImReplyLogQueryService(queryMapper, conversationMapper);
    }

    private ImConversationMessageVO message(Long id, Long sessionId, LocalDateTime eventTime) {
        ImConversationMessageVO message = new ImConversationMessageVO();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setEventTime(eventTime);
        return message;
    }
}
