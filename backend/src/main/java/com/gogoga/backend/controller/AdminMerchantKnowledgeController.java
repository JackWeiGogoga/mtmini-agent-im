package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.CommentReplyKnowledgeService;
import com.gogoga.backend.service.UserShopBindingService;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理后台商户私有知识库接口。
 */
@RestController
@RequestMapping("/api/system/comment-reply-knowledge/merchant")
@RequiredArgsConstructor
@RequiresLogin
@RequiresPermission("comment:merchant-knowledge:list")
public class AdminMerchantKnowledgeController {

    private final UserShopBindingService userShopBindingService;
    private final CommentReplyKnowledgeService commentReplyKnowledgeService;
    private final CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService;

    @GetMapping("/items")
    public ResultVO<List<CommentReplyKnowledgeItemVO>> listQa(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeService.listShopQa(shopId));
    }

    @PostMapping("/items")
    @RequiresPermission("comment:merchant-knowledge:add")
    public ResultVO<CommentReplyKnowledgeItemVO> createQa(
            @RequestParam Long shopId,
            @Valid @RequestBody CommentReplyQaKnowledgeParam param
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeService.createShopQa(shopId, param));
    }

    @PutMapping("/items/{id}")
    @RequiresPermission("comment:merchant-knowledge:edit")
    public ResultVO<CommentReplyKnowledgeItemVO> updateQa(
            @RequestParam Long shopId,
            @PathVariable Long id,
            @Valid @RequestBody CommentReplyQaKnowledgeParam param
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeService.updateShopQa(shopId, id, param));
    }

    @DeleteMapping("/items/all")
    @RequiresPermission("comment:merchant-knowledge:delete")
    public ResultVO<Void> clearAll(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        commentReplyKnowledgeService.clearShopKnowledge(shopId);
        return ResultVO.success();
    }

    @DeleteMapping("/items/{id}")
    @RequiresPermission("comment:merchant-knowledge:delete")
    public ResultVO<Void> deleteQa(@RequestParam Long shopId, @PathVariable Long id) {
        requireVisibleShop(shopId);
        commentReplyKnowledgeService.deleteShopQa(shopId, id);
        return ResultVO.success();
    }

    @GetMapping("/documents")
    public ResultVO<List<CommentReplyKnowledgeDocumentVO>> listDocuments(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.listShopDocuments(shopId));
    }

    @PostMapping("/documents/upload")
    @RequiresPermission("comment:merchant-knowledge:add")
    public ResultVO<CommentReplyKnowledgeDocumentVO> uploadDocument(
            @RequestParam Long shopId,
            @RequestParam("file") MultipartFile file
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.uploadShopDocument(shopId, file));
    }

    @DeleteMapping("/documents/{id}")
    @RequiresPermission("comment:merchant-knowledge:delete")
    public ResultVO<Void> deleteDocument(@RequestParam Long shopId, @PathVariable Long id) {
        requireVisibleShop(shopId);
        commentReplyKnowledgeDocumentService.deleteShopDocument(shopId, id);
        return ResultVO.success();
    }

    @GetMapping("/documents/{id}/chunks")
    public ResultVO<List<CommentReplyKnowledgeChunkVO>> listChunks(
            @RequestParam Long shopId,
            @PathVariable Long id
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.listShopDocumentChunks(shopId, id));
    }

    @PutMapping("/documents/chunks/{chunkId}/sentiment")
    @RequiresPermission("comment:merchant-knowledge:edit")
    public ResultVO<CommentReplyKnowledgeChunkVO> updateChunkSentiment(
            @RequestParam Long shopId,
            @PathVariable Long chunkId,
            @Valid @RequestBody CommentReplyKnowledgeChunkUpdateParam param
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.updateShopChunkSentiment(shopId, chunkId, param));
    }

    @PutMapping("/documents/chunks/{chunkId}/enabled")
    @RequiresPermission("comment:merchant-knowledge:edit")
    public ResultVO<CommentReplyKnowledgeChunkVO> updateChunkEnabled(
            @RequestParam Long shopId,
            @PathVariable Long chunkId,
            @RequestParam Boolean enabled
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.updateShopChunkEnabled(shopId, chunkId, enabled));
    }

    @DeleteMapping("/documents/chunks/{chunkId}")
    @RequiresPermission("comment:merchant-knowledge:delete")
    public ResultVO<Void> deleteChunk(@RequestParam Long shopId, @PathVariable Long chunkId) {
        requireVisibleShop(shopId);
        commentReplyKnowledgeDocumentService.deleteShopChunk(shopId, chunkId);
        return ResultVO.success();
    }

    private void requireVisibleShop(Long shopId) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
    }
}
