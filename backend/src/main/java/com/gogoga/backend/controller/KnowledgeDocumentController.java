package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.vo.KnowledgeChunkVO;
import com.gogoga.backend.vo.KnowledgeDocumentVO;
import com.gogoga.backend.vo.KnowledgeDocumentPreviewVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * H5 门店知识文档接口。
 */
@RestController
@RequestMapping("/api/h5/knowledge/documents")
@RequiredArgsConstructor
@RequiresLogin
@RequiresShopRight({MtShopRightCodeConstant.VIP_CODE, MtShopRightCodeConstant.H5_PROMOTION})
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping
    public ResultVO<List<KnowledgeDocumentVO>> listDocuments() {
        return ResultVO.success(knowledgeDocumentService.listCurrentShopDocuments());
    }

    @PostMapping("/upload")
    public ResultVO<KnowledgeDocumentVO> uploadDocument(@RequestParam("file") MultipartFile file) {
        return ResultVO.success(knowledgeDocumentService.uploadCurrentShopDocument(file));
    }

    @PostMapping("/preview")
    public ResultVO<KnowledgeDocumentPreviewVO> previewDocument(@RequestParam("file") MultipartFile file) {
        return ResultVO.success(knowledgeDocumentService.previewCurrentShopDocument(file));
    }

    @DeleteMapping("/{id}")
    public ResultVO<Void> deleteDocument(@PathVariable Long id) {
        knowledgeDocumentService.deleteCurrentShopDocument(id);
        return ResultVO.success();
    }

    @GetMapping("/{id}/chunks")
    public ResultVO<List<KnowledgeChunkVO>> listChunks(@PathVariable Long id) {
        return ResultVO.success(knowledgeDocumentService.listCurrentShopDocumentChunks(id));
    }

    @PutMapping("/chunks/{chunkId}/enabled")
    public ResultVO<KnowledgeChunkVO> updateChunkEnabled(
            @PathVariable Long chunkId,
            @RequestParam Boolean enabled
    ) {
        return ResultVO.success(knowledgeDocumentService.updateCurrentShopChunkEnabled(chunkId, enabled));
    }

    @DeleteMapping("/chunks/{chunkId}")
    public ResultVO<Void> deleteChunk(@PathVariable Long chunkId) {
        knowledgeDocumentService.deleteCurrentShopChunk(chunkId);
        return ResultVO.success();
    }
}
