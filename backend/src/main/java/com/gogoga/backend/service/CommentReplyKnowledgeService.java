package com.gogoga.backend.service;

import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;

import java.util.List;

public interface CommentReplyKnowledgeService {

    List<CommentReplyKnowledgeItemVO> listCurrentShopQa();

    CommentReplyKnowledgeItemVO createCurrentShopQa(CommentReplyQaKnowledgeParam param);

    CommentReplyKnowledgeItemVO updateCurrentShopQa(Long id, CommentReplyQaKnowledgeParam param);

    void deleteCurrentShopQa(Long id);
}
