package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.vo.MtH5CommentReplyLogVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtH5CommentReplyLogServiceTest {

    @Mock
    private MtCommentMapper commentMapper;

    @Test
    void sensitiveTypeFiveHidesCustomerText() {
        MtComment comment = comment(5);
        when(commentMapper.selectLogsByShopId(100L)).thenReturn(List.of(comment));

        PageInfo<MtH5CommentReplyLogVO> result = service().listLogs(shop(), 1, 20);

        assertEquals("评价内容因敏感词已屏蔽", result.getList().get(0).getCustomerMessage());
        assertNull(result.getList().get(0).getAddComment());
    }

    @Test
    void visibleTypesKeepCustomerText() {
        MtComment comment = comment(10);
        when(commentMapper.selectLogsByShopId(100L)).thenReturn(List.of(comment));

        PageInfo<MtH5CommentReplyLogVO> result = service().listLogs(shop(), 1, 20);

        assertEquals("原始评价", result.getList().get(0).getCustomerMessage());
        assertEquals("原始追评", result.getList().get(0).getAddComment());
    }

    private MtH5CommentReplyLogService service() {
        return new MtH5CommentReplyLogService(commentMapper);
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        return shop;
    }

    private MtComment comment(int commentType) {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setCommentId(9001L);
        comment.setCommentType(commentType);
        comment.setCommentContent("原始评价");
        comment.setAddComment("原始追评");
        comment.setProductScore(5);
        return comment;
    }
}
