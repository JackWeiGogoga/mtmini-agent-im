package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.dto.CommentLogAnalysisPreviewDTO;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.param.CommentReplyTestParam;
import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.CommentReplyKnowledgeService;
import com.gogoga.backend.service.CommentLogAnalysisService;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.MtCommentReplyContentService;
import com.gogoga.backend.service.UserShopBindingService;
import com.gogoga.backend.util.CommentImageUrlUtil;
import com.gogoga.backend.vo.CommentReplyImageUploadVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentPreviewVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 管理后台商户私有知识库接口。
 */
@RestController
@RequestMapping("/api/system/comment-reply-knowledge/merchant")
@RequiredArgsConstructor
@RequiresLogin
@RequiresPermission("comment:merchant-knowledge:list")
@Slf4j
public class AdminMerchantKnowledgeController {

    private static final long REPLY_TEST_IMAGE_URL_EXPIRE_SECONDS = 1800L;

    private final UserShopBindingService userShopBindingService;
    private final CommentReplyKnowledgeService commentReplyKnowledgeService;
    private final CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService;
    private final MtCommentReplyContentService mtCommentReplyContentService;
    private final CommentLogAnalysisService commentLogAnalysisService;
    private final CosStorageService cosStorageService;
    private final MtProperties mtProperties;

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

    @PostMapping("/documents/preview")
    @RequiresPermission("comment:merchant-knowledge:add")
    public ResultVO<CommentReplyKnowledgeDocumentPreviewVO> previewDocument(
            @RequestParam Long shopId,
            @RequestParam("file") MultipartFile file
    ) {
        requireVisibleShop(shopId);
        return ResultVO.success(commentReplyKnowledgeDocumentService.previewShopDocument(shopId, file));
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

    @PostMapping("/reply-test")
    public ResultVO<CommentReplyContentResultDTO> testReply(
            @RequestParam Long shopId,
            @Valid @RequestBody CommentReplyTestParam param
    ) {
        long startNanos = System.nanoTime();
        MtShop shop = requireVisibleShop(shopId);
        MtComment comment = new MtComment();
        comment.setId(null);
        comment.setShopId(shop.getId());
        comment.setPoiId(shop.getPoiId());
        comment.setEpoiId(shop.getEpoiId());
        comment.setCommentId(System.currentTimeMillis());
        comment.setProductScore(param.getProductScore());
        comment.setPackingScore(param.getPackingScore());
        comment.setCommentContent(param.getCommentContent());
        comment.setAddComment(param.getAddComment());
        List<String> imageUrls = CommentImageUrlUtil.parseImageUrls(
                param.getCommentPictures(),
                mtProperties.getComment().getKnowledge().getVisionMaxImageCount()
        );
        comment.setCommentPictures(String.join(",", imageUrls));
        comment.setImageSummaryText(param.getImageSummaryText());
        try {
            CommentReplyContentResultDTO result = mtCommentReplyContentService.previewReply(shop, comment);
            if (result != null && result.getImageSummaryText() != null) {
                comment.setImageSummaryText(result.getImageSummaryText());
            }
            attachAnalysisPreview(result, comment);
            if (result != null) {
                result.setTotalDurationMs(durationMs(startNanos));
            }
            log.info("测试回复生成完成，shopId={}, commentId={}, replyDurationMs={}, analysisDurationMs={}, totalDurationMs={}",
                    shopId,
                    comment.getCommentId(),
                    result == null ? null : result.getReplyDurationMs(),
                    result == null ? null : result.getAnalysisDurationMs(),
                    result == null ? null : result.getTotalDurationMs());
            return ResultVO.success(result);
        } finally {
            cleanupTemporaryImages(shopId, param.getTemporaryImageObjectKeys());
        }
    }

    private void attachAnalysisPreview(CommentReplyContentResultDTO result, MtComment comment) {
        if (result == null) {
            return;
        }
        CommentLogAnalysisPreviewDTO preview = commentLogAnalysisService.preview(comment);
        if (preview == null) {
            return;
        }
        result.setAnalysisText(preview.getAnalysisText());
        result.setAnalysisModel(preview.getModel());
        result.setAnalysisPromptTokens(preview.getPromptTokens());
        result.setAnalysisCompletionTokens(preview.getCompletionTokens());
        result.setAnalysisTotalTokens(preview.getTotalTokens());
        result.setAnalysisPowerAmount(preview.getEstimatedPowerAmount());
        result.setAnalysisDurationMs(preview.getDurationMs());
        result.setAnalysisErrorMessage(preview.getErrorMessage());
    }

    private void cleanupTemporaryImages(Long shopId, List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        String prefix = StringUtils.stripEnd(mtProperties.getComment().getKnowledge().getVisionTempImageKeyPrefix(), "/")
                + "/" + shopId + "/";
        objectKeys.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .filter(objectKey -> StringUtils.startsWith(objectKey, prefix))
                .forEach(objectKey -> {
                    try {
                        cosStorageService.deleteObject(objectKey);
                        log.info("已清理测试回复临时图片，shopId={}, objectKey={}", shopId, objectKey);
                    } catch (Exception e) {
                        log.warn("清理测试回复临时图片失败，shopId={}, objectKey={}", shopId, objectKey, e);
                    }
                });
    }

    @PostMapping("/reply-test/images/upload")
    @RequiresPermission("comment:merchant-knowledge:add")
    public ResultVO<List<CommentReplyImageUploadVO>> uploadReplyTestImages(
            @RequestParam Long shopId,
            @RequestParam("files") MultipartFile[] files
    ) {
        requireVisibleShop(shopId);
        if (files == null || files.length == 0) {
            throw new BusinessException("请先选择图片");
        }
        int maxCount = mtProperties.getComment().getKnowledge().getVisionMaxImageCount();
        if (files.length > maxCount) {
            throw new BusinessException("最多上传 " + maxCount + " 张图片");
        }
        List<CommentReplyImageUploadVO> result = new ArrayList<>();
        for (MultipartFile file : files) {
            validateImageFile(file);
            CosStorageService.StoredObject storedObject = cosStorageService.uploadObject(
                    mtProperties.getComment().getKnowledge().getVisionTempImageKeyPrefix(),
                    String.valueOf(shopId),
                    file
            );
            CommentReplyImageUploadVO vo = new CommentReplyImageUploadVO();
            vo.setFileName(file.getOriginalFilename());
            vo.setObjectKey(storedObject.getObjectKey());
            vo.setUrl(cosStorageService.resolvePresignedObjectAccessUrl(
                    storedObject.getObjectKey(),
                    REPLY_TEST_IMAGE_URL_EXPIRE_SECONDS,
                    storedObject.getAccessUrl()
            ));
            result.add(vo);
        }
        return ResultVO.success(result);
    }

    private MtShop requireVisibleShop(Long shopId) {
        return userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("图片不能为空");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!List.of("image/jpeg", "image/jpg", "image/png", "image/webp").contains(contentType)) {
            throw new BusinessException("仅支持 JPG、PNG、WebP 图片");
        }
        if (file.getSize() > 10L * 1024L * 1024L) {
            throw new BusinessException("单张图片最大 10MB");
        }
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }
}
