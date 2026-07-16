package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentComplaint;
import com.gogoga.backend.entity.mt.MtCommentImFollowUp;
import com.gogoga.backend.enums.CommentComplaintStatusEnum;
import com.gogoga.backend.enums.CommentImFollowUpStatusEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtCommentComplaintMapper;
import com.gogoga.backend.mapper.MtCommentImFollowUpMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.service.async.MtCommentAsyncTaskKeys;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoImFollowUpTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoReplyTaskPayload;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理后台评价处理失败任务手动重试。
 */
@Service
@RequiredArgsConstructor
public class CommentProcessingRetryService {

    private final CommentProcessingLogService commentProcessingLogService;
    private final MtCommentMapper mtCommentMapper;
    private final MtCommentComplaintMapper mtCommentComplaintMapper;
    private final MtCommentImFollowUpMapper mtCommentImFollowUpMapper;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtProperties mtProperties;

    @Transactional(rollbackFor = Exception.class)
    public void retryAutoReply(Long id) {
        CommentProcessingLogVO log = requireLog(id);
        requireFailed(log.getAutoReplyStatus(), "只有自动回复失败的评价才能重试");
        MtComment comment = requireComment(id);
        LocalDateTime now = LocalDateTime.now();
        if (mtCommentMapper.resetAutoReplyForManualRetry(id, now) != 1) {
            throw new BusinessException(409, "自动回复状态已变化，请刷新后重试");
        }
        MtCommentAutoReplyTaskPayload payload = new MtCommentAutoReplyTaskPayload();
        payload.setShopId(comment.getShopId());
        payload.setCommentId(comment.getCommentId());
        mtAsyncTaskService.enqueueOrResetForManualRetry(
                MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY,
                MtCommentAsyncTaskKeys.autoReply(comment.getCommentId()),
                String.valueOf(comment.getShopId()),
                payload,
                mtProperties.getAsyncTask().getCommentAutoReply().getMaxRetryCount()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryComplaint(Long id) {
        CommentProcessingLogVO log = requireLog(id);
        requireFailed(log.getComplaintStatus(), "只有差评申诉失败的评价才能重试");
        MtComment comment = requireComment(id);
        MtCommentComplaint record = mtCommentComplaintMapper.selectByShopIdAndCommentId(
                comment.getShopId(), comment.getCommentId());
        String resumeStatus = canReuseComplaint(record)
                ? CommentComplaintStatusEnum.GENERATED.name()
                : CommentComplaintStatusEnum.PENDING.name();
        LocalDateTime now = LocalDateTime.now();
        if (record != null && mtCommentComplaintMapper.resetFailedForManualRetry(
                record.getId(), resumeStatus, now) != 1) {
            throw new BusinessException(409, "差评申诉状态已变化，请刷新后重试");
        }
        if (mtCommentMapper.resetComplaintForManualRetry(id, resumeStatus, now) != 1) {
            throw new BusinessException(409, "差评申诉状态已变化，请刷新后重试");
        }
        MtCommentAutoComplaintTaskPayload payload = new MtCommentAutoComplaintTaskPayload();
        payload.setShopId(comment.getShopId());
        payload.setCommentId(comment.getCommentId());
        mtAsyncTaskService.enqueueOrResetForManualRetry(
                MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT,
                MtCommentAsyncTaskKeys.complaint(comment.getCommentId()),
                String.valueOf(comment.getShopId()),
                payload,
                mtProperties.getAsyncTask().getCommentAutoComplaint().getMaxRetryCount()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryImFollowUp(Long id) {
        CommentProcessingLogVO log = requireLog(id);
        requireFailed(log.getImFollowUpStatus(), "只有群聊跟进失败的评价才能重试");
        MtComment comment = requireComment(id);
        MtCommentImFollowUp record = mtCommentImFollowUpMapper.selectByShopIdAndCommentId(
                comment.getShopId(), comment.getCommentId());
        String resumeStatus = resolveImResumeStatus(record);
        LocalDateTime now = LocalDateTime.now();
        if (record != null && mtCommentImFollowUpMapper.resetFailedForManualRetry(
                record.getId(), resumeStatus, now) != 1) {
            throw new BusinessException(409, "群聊跟进状态已变化，请刷新后重试");
        }
        Long groupId = record == null ? null : record.getGroupId();
        if (mtCommentMapper.resetImFollowUpForManualRetry(id, resumeStatus, groupId, now) != 1) {
            throw new BusinessException(409, "群聊跟进状态已变化，请刷新后重试");
        }
        MtCommentAutoImFollowUpTaskPayload payload = new MtCommentAutoImFollowUpTaskPayload();
        payload.setShopId(comment.getShopId());
        payload.setCommentId(comment.getCommentId());
        mtAsyncTaskService.enqueueOrResetForManualRetry(
                MtAsyncTaskTypeEnum.COMMENT_AUTO_IM_FOLLOW_UP,
                MtCommentAsyncTaskKeys.imFollowUp(comment.getCommentId()),
                String.valueOf(comment.getShopId()),
                payload,
                mtProperties.getAsyncTask().getCommentAutoImFollowUp().getMaxRetryCount()
        );
    }

    private CommentProcessingLogVO requireLog(Long id) {
        if (id == null) {
            throw new BusinessException(400, "评价日志ID不能为空");
        }
        return commentProcessingLogService.getDetail(id);
    }

    private MtComment requireComment(Long id) {
        MtComment comment = mtCommentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评价处理日志不存在");
        }
        return comment;
    }

    private void requireFailed(String status, String message) {
        if (!StringUtils.equals(status, "FAILED")) {
            throw new BusinessException(409, message);
        }
    }

    private boolean canReuseComplaint(MtCommentComplaint record) {
        return record != null
                && record.getReportTypeCode() != null
                && StringUtils.isNotBlank(record.getReportContent());
    }

    private String resolveImResumeStatus(MtCommentImFollowUp record) {
        if (record != null && record.getGroupId() != null) {
            return CommentImFollowUpStatusEnum.GROUP_CREATED.name();
        }
        if (record != null && StringUtils.isNotBlank(record.getMessageContent())) {
            return CommentImFollowUpStatusEnum.GENERATED.name();
        }
        return CommentImFollowUpStatusEnum.PENDING.name();
    }
}
