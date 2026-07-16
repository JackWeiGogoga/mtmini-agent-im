package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.CommentProcessingLogQueryParam;
import com.gogoga.backend.service.CommentProcessingLogService;
import com.gogoga.backend.service.CommentProcessingRetryService;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台评价处理日志接口。
 */
@RestController
@RequiredArgsConstructor
@RequiresLogin
@RequestMapping("/api/system/comment-processing-logs")
public class CommentProcessingLogController {

    private final CommentProcessingLogService commentProcessingLogService;
    private final CommentProcessingRetryService commentProcessingRetryService;

    @GetMapping
    @RequiresPermission("comment:processing-log:list")
    public ResultVO<PageInfo<CommentProcessingLogVO>> list(CommentProcessingLogQueryParam query) {
        return ResultVO.success(commentProcessingLogService.list(query));
    }

    @GetMapping("/{commentId}")
    @RequiresPermission("comment:processing-log:query")
    public ResultVO<CommentProcessingLogVO> detail(@PathVariable Long commentId) {
        return ResultVO.success(commentProcessingLogService.getDetail(commentId));
    }

    @PostMapping("/{commentId}/retry/auto-reply")
    @RequiresPermission("comment:processing-log:retry")
    public ResultVO<Void> retryAutoReply(@PathVariable Long commentId) {
        commentProcessingRetryService.retryAutoReply(commentId);
        return ResultVO.success();
    }

    @PostMapping("/{commentId}/retry/complaint")
    @RequiresPermission("comment:processing-log:retry")
    public ResultVO<Void> retryComplaint(@PathVariable Long commentId) {
        commentProcessingRetryService.retryComplaint(commentId);
        return ResultVO.success();
    }

    @PostMapping("/{commentId}/retry/im-follow-up")
    @RequiresPermission("comment:processing-log:retry")
    public ResultVO<Void> retryImFollowUp(@PathVariable Long commentId) {
        commentProcessingRetryService.retryImFollowUp(commentId);
        return ResultVO.success();
    }
}
