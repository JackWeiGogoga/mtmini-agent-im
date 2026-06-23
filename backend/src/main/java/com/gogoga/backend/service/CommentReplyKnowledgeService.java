package com.gogoga.backend.service;

import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;

import java.util.List;

public interface CommentReplyKnowledgeService {

    List<CommentReplyKnowledgeItemVO> listCurrentShopQa();

    List<CommentReplyKnowledgeItemVO> listShopQa(Long shopId);

    CommentReplyKnowledgeItemVO createCurrentShopQa(CommentReplyQaKnowledgeParam param);

    CommentReplyKnowledgeItemVO createShopQa(Long shopId, CommentReplyQaKnowledgeParam param);

    CommentReplyKnowledgeItemVO updateCurrentShopQa(Long id, CommentReplyQaKnowledgeParam param);

    CommentReplyKnowledgeItemVO updateShopQa(Long shopId, Long id, CommentReplyQaKnowledgeParam param);

    void deleteCurrentShopQa(Long id);

    void deleteShopQa(Long shopId, Long id);

    void clearCurrentShopKnowledge();

    void clearShopKnowledge(Long shopId);
}
