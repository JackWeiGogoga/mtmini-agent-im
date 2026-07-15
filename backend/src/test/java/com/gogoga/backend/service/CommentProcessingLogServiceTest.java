package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.CommentProcessingLogMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.param.CommentProcessingLogQueryParam;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentProcessingLogServiceTest {

    @Mock private CommentProcessingLogMapper processingLogMapper;
    @Mock private MtCommentMapper commentMapper;
    @Mock private AdminDataScopeService dataScopeService;
    @Captor private ArgumentCaptor<List<String>> visibleUserIdsCaptor;

    private CommentProcessingLogService service;

    @BeforeEach
    void setUp() {
        service = new CommentProcessingLogService(processingLogMapper, commentMapper, dataScopeService);
    }

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void capsPageSizeNormalizesFiltersAndAllowsAllScopeWithoutBindingFilter() {
        when(dataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(processingLogMapper.selectForAdmin(any(), isNull())).thenReturn(List.of(new CommentProcessingLogVO()));
        CommentProcessingLogQueryParam query = new CommentProcessingLogQueryParam();
        query.setPageNum(0);
        query.setPageSize(999);
        query.setShopKeyword("  门店  ");
        query.setSentiment(" bad ");
        query.setComplaintStatus(" success ");

        service.list(query);

        ArgumentCaptor<CommentProcessingLogQueryParam> captor = ArgumentCaptor.forClass(CommentProcessingLogQueryParam.class);
        verify(processingLogMapper).selectForAdmin(captor.capture(), isNull());
        assertEquals(1, captor.getValue().getPageNum());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals("门店", captor.getValue().getShopKeyword());
        assertEquals("BAD", captor.getValue().getSentiment());
        assertEquals("SUCCESS", captor.getValue().getComplaintStatus());
    }

    @Test
    void passesVisibleUserIdsForScopedAdminAndUsesDenyAllSentinelWhenEmpty() {
        when(dataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);
        when(dataScopeService.getCurrentVisibleUserIds()).thenReturn(Set.of("user-1", "user-2"));
        when(processingLogMapper.selectForAdmin(any(), any())).thenReturn(List.of());

        service.list(new CommentProcessingLogQueryParam());

        verify(processingLogMapper).selectForAdmin(any(), visibleUserIdsCaptor.capture());
        assertEquals(Set.of("user-1", "user-2"), Set.copyOf(visibleUserIdsCaptor.getValue()));
    }

    @Test
    void detailAppliesScopeAndAddsOnlySafeParsedImageUrls() {
        when(dataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.SELF);
        when(dataScopeService.getCurrentVisibleUserIds()).thenReturn(Set.of());
        CommentProcessingLogVO detail = new CommentProcessingLogVO();
        detail.setId(1L);
        when(processingLogMapper.selectByIdForAdmin(eq(1L), eq(List.of("__NO_VISIBLE_USER__")))).thenReturn(detail);
        MtComment comment = new MtComment();
        comment.setCommentPictures("https://a.example/1.jpg,ftp://unsafe.example/file");
        when(commentMapper.selectById(1L)).thenReturn(comment);

        CommentProcessingLogVO result = service.getDetail(1L);

        assertEquals(List.of("https://a.example/1.jpg"), result.getImageUrls());
    }

    @Test
    void detailReturnsNotFoundForMissingOrOutOfScopeComment() {
        when(dataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(processingLogMapper.selectByIdForAdmin(99L, null)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.getDetail(99L));

        assertEquals(404, exception.getCode());
    }
}
