package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentComplaint;
import com.gogoga.backend.entity.mt.MtCommentImFollowUp;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtCommentComplaintMapper;
import com.gogoga.backend.mapper.MtCommentImFollowUpMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoReplyTaskPayload;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentProcessingRetryServiceTest {

    @Mock private CommentProcessingLogService logService;
    @Mock private MtCommentMapper commentMapper;
    @Mock private MtCommentComplaintMapper complaintMapper;
    @Mock private MtCommentImFollowUpMapper imFollowUpMapper;
    @Mock private MtAsyncTaskService asyncTaskService;

    private CommentProcessingRetryService service;

    @BeforeEach
    void setUp() {
        service = new CommentProcessingRetryService(
                logService,
                commentMapper,
                complaintMapper,
                imFollowUpMapper,
                asyncTaskService,
                new MtProperties()
        );
    }

    @Test
    void retriesFailedAutoReplyWithOriginalIdempotencyKey() {
        when(logService.getDetail(1L)).thenReturn(log("FAILED", null, null));
        when(commentMapper.selectById(1L)).thenReturn(comment());
        when(commentMapper.resetAutoReplyForManualRetry(eq(1L), any())).thenReturn(1);

        service.retryAutoReply(1L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(asyncTaskService).enqueueOrResetForManualRetry(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY),
                eq("comment:9001"),
                eq("100"),
                payloadCaptor.capture(),
                eq(3)
        );
        MtCommentAutoReplyTaskPayload payload = (MtCommentAutoReplyTaskPayload) payloadCaptor.getValue();
        assertEquals(100L, payload.getShopId());
        assertEquals(9001L, payload.getCommentId());
    }

    @Test
    void retriesComplaintFromGeneratedStageWithoutRegenerating() {
        when(logService.getDetail(1L)).thenReturn(log(null, "FAILED", null));
        when(commentMapper.selectById(1L)).thenReturn(comment());
        MtCommentComplaint complaint = new MtCommentComplaint();
        complaint.setId(20L);
        complaint.setReportTypeCode(47);
        complaint.setReportContent("申诉内容");
        when(complaintMapper.selectByShopIdAndCommentId(100L, 9001L)).thenReturn(complaint);
        when(complaintMapper.resetFailedForManualRetry(eq(20L), eq("GENERATED"), any())).thenReturn(1);
        when(commentMapper.resetComplaintForManualRetry(eq(1L), eq("GENERATED"), any())).thenReturn(1);

        service.retryComplaint(1L);

        verify(asyncTaskService).enqueueOrResetForManualRetry(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT),
                eq("comment-complaint:9001"),
                eq("100"),
                any(),
                eq(3)
        );
    }

    @Test
    void retriesImCreateGroupFromGeneratedStageAndClearsFailedState() {
        when(logService.getDetail(1L)).thenReturn(log(null, null, "FAILED"));
        when(commentMapper.selectById(1L)).thenReturn(comment());
        MtCommentImFollowUp followUp = new MtCommentImFollowUp();
        followUp.setId(30L);
        followUp.setMessageContent("请提供订单信息");
        when(imFollowUpMapper.selectByShopIdAndCommentId(100L, 9001L)).thenReturn(followUp);
        when(imFollowUpMapper.resetFailedForManualRetry(eq(30L), eq("GENERATED"), any())).thenReturn(1);
        when(commentMapper.resetImFollowUpForManualRetry(eq(1L), eq("GENERATED"), eq(null), any())).thenReturn(1);

        service.retryImFollowUp(1L);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(asyncTaskService).enqueueOrResetForManualRetry(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_IM_FOLLOW_UP),
                eq("comment-im-follow-up:9001"),
                eq("100"),
                payloadCaptor.capture(),
                eq(0)
        );
        assertEquals(9001L, ((MtCommentAutoImFollowUpTaskPayload) payloadCaptor.getValue()).getCommentId());
    }

    @Test
    void retriesImSendFromExistingGroupWithoutCreatingAnotherGroup() {
        when(logService.getDetail(1L)).thenReturn(log(null, null, "FAILED"));
        when(commentMapper.selectById(1L)).thenReturn(comment());
        MtCommentImFollowUp followUp = new MtCommentImFollowUp();
        followUp.setId(31L);
        followUp.setMessageContent("请提供订单信息");
        followUp.setGroupId(8899L);
        when(imFollowUpMapper.selectByShopIdAndCommentId(100L, 9001L)).thenReturn(followUp);
        when(imFollowUpMapper.resetFailedForManualRetry(eq(31L), eq("GROUP_CREATED"), any())).thenReturn(1);
        when(commentMapper.resetImFollowUpForManualRetry(eq(1L), eq("GROUP_CREATED"), eq(8899L), any()))
                .thenReturn(1);

        service.retryImFollowUp(1L);

        verify(asyncTaskService).enqueueOrResetForManualRetry(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_IM_FOLLOW_UP),
                eq("comment-im-follow-up:9001"),
                eq("100"),
                any(),
                eq(0)
        );
    }

    @Test
    void rejectsRetryWhenBusinessStatusIsNotFailed() {
        when(logService.getDetail(1L)).thenReturn(log("SUCCESS", null, null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.retryAutoReply(1L));

        assertEquals(409, exception.getCode());
        verify(commentMapper, never()).resetAutoReplyForManualRetry(any(), any());
        verify(asyncTaskService, never()).enqueueOrResetForManualRetry(any(), any(), any(), any(), any(Integer.class));
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setShopId(100L);
        comment.setCommentId(9001L);
        return comment;
    }

    private CommentProcessingLogVO log(String replyStatus, String complaintStatus, String imStatus) {
        CommentProcessingLogVO log = new CommentProcessingLogVO();
        log.setId(1L);
        log.setAutoReplyStatus(replyStatus);
        log.setComplaintStatus(complaintStatus);
        log.setImFollowUpStatus(imStatus);
        return log;
    }
}
