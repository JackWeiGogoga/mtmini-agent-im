package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.ImAutoReplyLogQueryParam;
import com.gogoga.backend.param.ImConversationQueryParam;
import com.gogoga.backend.service.ImReplyLogQueryService;
import com.gogoga.backend.vo.ImAutoReplyLogVO;
import com.gogoga.backend.vo.ImConversationHistoryPageVO;
import com.gogoga.backend.vo.ImConversationListVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system/im-auto-replies")
@RequiredArgsConstructor
@RequiresLogin
@RequiresPermission("system:im-auto-reply:list")
public class AdminImAutoReplyController {

    private final ImReplyLogQueryService queryService;

    @GetMapping("/messages")
    public ResultVO<PageInfo<ImAutoReplyLogVO>> messages(ImAutoReplyLogQueryParam param) {
        return ResultVO.success(queryService.listReplyLogs(param, null));
    }

    @GetMapping("/conversations")
    public ResultVO<PageInfo<ImConversationListVO>> conversations(ImConversationQueryParam param) {
        return ResultVO.success(queryService.listConversations(param, null));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResultVO<ImConversationHistoryPageVO> history(
            @PathVariable Long conversationId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime cursorTime,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        return ResultVO.success(queryService.history(conversationId, null, cursorTime, cursorId, pageSize));
    }
}
