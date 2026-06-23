package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * H5 评论回复知识文档接口。
 */
@RestController
@RequestMapping("/api/h5/comment-reply-knowledge/documents")
@RequiredArgsConstructor
@RequiresLogin
@RequiresShopRight({MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION})
public class CommentReplyKnowledgeDocumentController {

    private final CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService;

    @GetMapping
    public ResultVO<List<CommentReplyKnowledgeDocumentVO>> listDocuments() {
        return ResultVO.success(commentReplyKnowledgeDocumentService.listCurrentShopDocuments());
    }

    @PostMapping("/upload")
    public ResultVO<CommentReplyKnowledgeDocumentVO> uploadDocument(@RequestParam("file") MultipartFile file) {
        return ResultVO.success(commentReplyKnowledgeDocumentService.uploadCurrentShopDocument(file));
    }

    @DeleteMapping("/{id}")
    public ResultVO<Void> deleteDocument(@PathVariable Long id) {
        commentReplyKnowledgeDocumentService.deleteCurrentShopDocument(id);
        return ResultVO.success();
    }

    @GetMapping("/{id}/chunks")
    public ResultVO<List<CommentReplyKnowledgeChunkVO>> listChunks(@PathVariable Long id) {
        return ResultVO.success(commentReplyKnowledgeDocumentService.listCurrentShopDocumentChunks(id));
    }

    @PutMapping("/chunks/{chunkId}/sentiment")
    public ResultVO<CommentReplyKnowledgeChunkVO> updateChunkSentiment(
            @PathVariable Long chunkId,
            @Valid @RequestBody CommentReplyKnowledgeChunkUpdateParam param
    ) {
        return ResultVO.success(commentReplyKnowledgeDocumentService.updateCurrentShopChunkSentiment(chunkId, param));
    }

    @PutMapping("/chunks/{chunkId}/enabled")
    public ResultVO<CommentReplyKnowledgeChunkVO> updateChunkEnabled(
            @PathVariable Long chunkId,
            @RequestParam Boolean enabled
    ) {
        return ResultVO.success(commentReplyKnowledgeDocumentService.updateCurrentShopChunkEnabled(chunkId, enabled));
    }

    @DeleteMapping("/chunks/{chunkId}")
    public ResultVO<Void> deleteChunk(@PathVariable Long chunkId) {
        commentReplyKnowledgeDocumentService.deleteCurrentShopChunk(chunkId);
        return ResultVO.success();
    }
}
