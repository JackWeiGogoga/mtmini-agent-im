package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.KnowledgeQaParam;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.service.KnowledgeService;
import com.gogoga.backend.service.UserShopBindingService;
import com.gogoga.backend.vo.KnowledgeChunkVO;
import com.gogoga.backend.vo.KnowledgeDocumentPreviewVO;
import com.gogoga.backend.vo.KnowledgeDocumentVO;
import com.gogoga.backend.vo.KnowledgeItemVO;
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
 * 管理后台门店知识库接口。
 */
@RestController
@RequestMapping("/api/system/knowledge/merchant")
@RequiredArgsConstructor
@RequiresLogin
@RequiresPermission("knowledge:list")
public class AdminMerchantKnowledgeController {

    private final UserShopBindingService userShopBindingService;
    private final KnowledgeService knowledgeService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping("/items")
    public ResultVO<List<KnowledgeItemVO>> listQa(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeService.listShopQa(shopId));
    }

    @PostMapping("/items")
    @RequiresPermission("knowledge:add")
    public ResultVO<KnowledgeItemVO> createQa(@RequestParam Long shopId,
                                               @Valid @RequestBody KnowledgeQaParam param) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeService.createShopQa(shopId, param));
    }

    @PutMapping("/items/{id}")
    @RequiresPermission("knowledge:edit")
    public ResultVO<KnowledgeItemVO> updateQa(@RequestParam Long shopId,
                                               @PathVariable Long id,
                                               @Valid @RequestBody KnowledgeQaParam param) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeService.updateShopQa(shopId, id, param));
    }

    @DeleteMapping("/items/all")
    @RequiresPermission("knowledge:delete")
    public ResultVO<Void> clearAll(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        knowledgeService.clearShopKnowledge(shopId);
        return ResultVO.success();
    }

    @DeleteMapping("/items/{id}")
    @RequiresPermission("knowledge:delete")
    public ResultVO<Void> deleteQa(@RequestParam Long shopId, @PathVariable Long id) {
        requireVisibleShop(shopId);
        knowledgeService.deleteShopQa(shopId, id);
        return ResultVO.success();
    }

    @GetMapping("/documents")
    public ResultVO<List<KnowledgeDocumentVO>> listDocuments(@RequestParam Long shopId) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeDocumentService.listShopDocuments(shopId));
    }

    @PostMapping("/documents/upload")
    @RequiresPermission("knowledge:add")
    public ResultVO<KnowledgeDocumentVO> uploadDocument(@RequestParam Long shopId,
                                                         @RequestParam("file") MultipartFile file) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeDocumentService.uploadShopDocument(shopId, file));
    }

    @PostMapping("/documents/preview")
    @RequiresPermission("knowledge:add")
    public ResultVO<KnowledgeDocumentPreviewVO> previewDocument(@RequestParam Long shopId,
                                                                 @RequestParam("file") MultipartFile file) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeDocumentService.previewShopDocument(shopId, file));
    }

    @DeleteMapping("/documents/{id}")
    @RequiresPermission("knowledge:delete")
    public ResultVO<Void> deleteDocument(@RequestParam Long shopId, @PathVariable Long id) {
        requireVisibleShop(shopId);
        knowledgeDocumentService.deleteShopDocument(shopId, id);
        return ResultVO.success();
    }

    @GetMapping("/documents/{id}/chunks")
    public ResultVO<List<KnowledgeChunkVO>> listChunks(@RequestParam Long shopId, @PathVariable Long id) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeDocumentService.listShopDocumentChunks(shopId, id));
    }

    @PutMapping("/documents/chunks/{chunkId}/enabled")
    @RequiresPermission("knowledge:edit")
    public ResultVO<KnowledgeChunkVO> updateChunkEnabled(@RequestParam Long shopId,
                                                          @PathVariable Long chunkId,
                                                          @RequestParam Boolean enabled) {
        requireVisibleShop(shopId);
        return ResultVO.success(knowledgeDocumentService.updateShopChunkEnabled(shopId, chunkId, enabled));
    }

    @DeleteMapping("/documents/chunks/{chunkId}")
    @RequiresPermission("knowledge:delete")
    public ResultVO<Void> deleteChunk(@RequestParam Long shopId, @PathVariable Long chunkId) {
        requireVisibleShop(shopId);
        knowledgeDocumentService.deleteShopChunk(shopId, chunkId);
        return ResultVO.success();
    }

    private void requireVisibleShop(Long shopId) {
        userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
    }
}
