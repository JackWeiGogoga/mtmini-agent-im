package com.gogoga.backend.service;

import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.vo.KnowledgeChunkVO;
import com.gogoga.backend.vo.KnowledgeDocumentVO;
import com.gogoga.backend.vo.KnowledgeDocumentPreviewVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeDocumentService {

    List<KnowledgeDocumentVO> listCurrentShopDocuments();

    List<KnowledgeDocumentVO> listShopDocuments(Long shopId);

    KnowledgeDocumentPreviewVO previewCurrentShopDocument(MultipartFile file);

    KnowledgeDocumentPreviewVO previewShopDocument(Long shopId, MultipartFile file);

    KnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file);

    KnowledgeDocumentVO uploadShopDocument(Long shopId, MultipartFile file);

    void deleteCurrentShopDocument(Long id);

    void deleteShopDocument(Long shopId, Long id);

    List<KnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId);

    List<KnowledgeChunkVO> listShopDocumentChunks(Long shopId, Long documentId);

    KnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled);

    KnowledgeChunkVO updateShopChunkEnabled(Long shopId, Long chunkId, Boolean enabled);

    void deleteCurrentShopChunk(Long chunkId);

    void deleteShopChunk(Long shopId, Long chunkId);

    void executeDocumentParseTask(MtKnowledgeDocumentParseTaskPayload payload);
}
