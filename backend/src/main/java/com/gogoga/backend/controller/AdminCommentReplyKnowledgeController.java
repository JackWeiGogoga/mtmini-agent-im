package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminCommentReplyOfficialKnowledgeParam;
import com.gogoga.backend.service.AdminCommentReplyKnowledgeService;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import com.gogoga.backend.vo.CommentReplyOfficialTemplateImportVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台官方评论回复知识接口。
 */
@RestController
@RequestMapping("/api/system/comment-reply-knowledge/official")
@RequiredArgsConstructor
@RequiresLogin
public class AdminCommentReplyKnowledgeController {

    private final AdminCommentReplyKnowledgeService adminCommentReplyKnowledgeService;

    @GetMapping
    @RequiresPermission("comment:reply-knowledge:list")
    public ResultVO<PageInfo<CommentReplyKnowledgeItemVO>> list(
            @RequestParam(required = false) String sentiment,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminCommentReplyKnowledgeService.listOfficialTemplates(sentiment, pageNum, pageSize));
    }

    @PostMapping
    @RequiresPermission("comment:reply-knowledge:add")
    public ResultVO<CommentReplyKnowledgeItemVO> create(@Valid @RequestBody AdminCommentReplyOfficialKnowledgeParam param) {
        return ResultVO.success(adminCommentReplyKnowledgeService.createOfficialTemplate(param));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("comment:reply-knowledge:add")
    public ResultVO<CommentReplyOfficialTemplateImportVO> importTemplates(@RequestParam("file") MultipartFile file) {
        return ResultVO.success(adminCommentReplyKnowledgeService.importOfficialTemplates(file));
    }

    @PutMapping("/{id}")
    @RequiresPermission("comment:reply-knowledge:edit")
    public ResultVO<CommentReplyKnowledgeItemVO> update(
            @PathVariable Long id,
            @Valid @RequestBody AdminCommentReplyOfficialKnowledgeParam param
    ) {
        return ResultVO.success(adminCommentReplyKnowledgeService.updateOfficialTemplate(id, param));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("comment:reply-knowledge:delete")
    public ResultVO<Void> delete(@PathVariable Long id) {
        adminCommentReplyKnowledgeService.deleteOfficialTemplate(id);
        return ResultVO.success();
    }
}
