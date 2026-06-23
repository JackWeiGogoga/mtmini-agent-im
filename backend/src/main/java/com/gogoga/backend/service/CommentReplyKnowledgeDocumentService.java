package com.gogoga.backend.service;

import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CommentReplyKnowledgeDocumentService {

    List<CommentReplyKnowledgeDocumentVO> listCurrentShopDocuments();

    CommentReplyKnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file);

    void deleteCurrentShopDocument(Long id);

    List<CommentReplyKnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId);

    CommentReplyKnowledgeChunkVO updateCurrentShopChunkSentiment(Long chunkId, CommentReplyKnowledgeChunkUpdateParam param);

    CommentReplyKnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled);

    void deleteCurrentShopChunk(Long chunkId);

    void executeDocumentParseTask(MtKnowledgeDocumentParseTaskPayload payload);
}
