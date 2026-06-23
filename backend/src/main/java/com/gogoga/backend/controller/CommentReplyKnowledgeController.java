package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.service.CommentReplyKnowledgeService;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * H5 评论回复知识库接口。
 */
@RestController
@RequestMapping("/api/h5/comment-reply-knowledge/items")
@RequiredArgsConstructor
@RequiresLogin
@RequiresShopRight({MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION})
public class CommentReplyKnowledgeController {

    private final CommentReplyKnowledgeService commentReplyKnowledgeService;

    @GetMapping
    public ResultVO<List<CommentReplyKnowledgeItemVO>> listQa() {
        return ResultVO.success(commentReplyKnowledgeService.listCurrentShopQa());
    }

    @PostMapping
    public ResultVO<CommentReplyKnowledgeItemVO> createQa(@Valid @RequestBody CommentReplyQaKnowledgeParam param) {
        return ResultVO.success(commentReplyKnowledgeService.createCurrentShopQa(param));
    }

    @PutMapping("/{id}")
    public ResultVO<CommentReplyKnowledgeItemVO> updateQa(
            @PathVariable Long id,
            @Valid @RequestBody CommentReplyQaKnowledgeParam param
    ) {
        return ResultVO.success(commentReplyKnowledgeService.updateCurrentShopQa(id, param));
    }

    @DeleteMapping("/all")
    public ResultVO<Void> clearAllKnowledge() {
        commentReplyKnowledgeService.clearCurrentShopKnowledge();
        return ResultVO.success();
    }

    @DeleteMapping("/{id}")
    public ResultVO<Void> deleteQa(@PathVariable Long id) {
        commentReplyKnowledgeService.deleteCurrentShopQa(id);
        return ResultVO.success();
    }
}
