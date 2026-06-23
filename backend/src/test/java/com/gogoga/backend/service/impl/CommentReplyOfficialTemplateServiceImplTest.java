package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentReplyOfficialTemplateServiceImplTest {

    @Mock
    private MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;

    @Test
    void returnsRandomEnabledTemplateBySentiment() {
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setId(1L);
        item.setAnswer("感谢认可，欢迎下次光临");
        when(mtReplyKnowledgeItemMapper.selectOfficialTemplates("GOOD")).thenReturn(List.of(item));
        CommentReplyOfficialTemplateServiceImpl service = new CommentReplyOfficialTemplateServiceImpl(
                mtReplyKnowledgeItemMapper,
                new MtProperties()
        );

        assertEquals(item, service.selectRandomEnabledTemplate(CommentReplySentimentEnum.GOOD));
    }

    @Test
    void returnsNullForUnknownSentiment() {
        CommentReplyOfficialTemplateServiceImpl service = new CommentReplyOfficialTemplateServiceImpl(
                mtReplyKnowledgeItemMapper,
                new MtProperties()
        );

        assertNull(service.selectRandomEnabledTemplate(CommentReplySentimentEnum.UNKNOWN));
    }

    @Test
    void fallsBackToDefaultReplyWhenTemplateMissing() {
        MtProperties properties = new MtProperties();
        properties.getComment().setDefaultReply("感谢您的评价");
        when(mtReplyKnowledgeItemMapper.selectOfficialTemplates("BAD")).thenReturn(List.of());
        CommentReplyOfficialTemplateServiceImpl service = new CommentReplyOfficialTemplateServiceImpl(
                mtReplyKnowledgeItemMapper,
                properties
        );

        assertEquals("感谢您的评价", service.selectRandomReplyOrDefault(CommentReplySentimentEnum.BAD));
    }
}
