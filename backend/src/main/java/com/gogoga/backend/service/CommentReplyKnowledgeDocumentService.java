package com.gogoga.backend.service;

import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CommentReplyKnowledgeDocumentService {

    List<CommentReplyKnowledgeDocumentVO> listCurrentShopDocuments();

    List<CommentReplyKnowledgeDocumentVO> listShopDocuments(Long shopId);

    CommentReplyKnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file);

    CommentReplyKnowledgeDocumentVO uploadShopDocument(Long shopId, MultipartFile file);

    void deleteCurrentShopDocument(Long id);

    void deleteShopDocument(Long shopId, Long id);

    List<CommentReplyKnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId);

    List<CommentReplyKnowledgeChunkVO> listShopDocumentChunks(Long shopId, Long documentId);

    CommentReplyKnowledgeChunkVO updateCurrentShopChunkSentiment(Long chunkId, CommentReplyKnowledgeChunkUpdateParam param);

    CommentReplyKnowledgeChunkVO updateShopChunkSentiment(Long shopId, Long chunkId, CommentReplyKnowledgeChunkUpdateParam param);

    CommentReplyKnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled);

    CommentReplyKnowledgeChunkVO updateShopChunkEnabled(Long shopId, Long chunkId, Boolean enabled);

    void deleteCurrentShopChunk(Long chunkId);

    void deleteShopChunk(Long shopId, Long chunkId);

    void executeDocumentParseTask(MtKnowledgeDocumentParseTaskPayload payload);
}
