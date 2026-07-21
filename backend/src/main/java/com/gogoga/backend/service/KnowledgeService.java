package com.gogoga.backend.service;

import com.gogoga.backend.param.KnowledgeQaParam;
import com.gogoga.backend.vo.KnowledgeItemVO;

import java.util.List;

public interface KnowledgeService {

    List<KnowledgeItemVO> listCurrentShopQa();

    List<KnowledgeItemVO> listShopQa(Long shopId);

    KnowledgeItemVO createCurrentShopQa(KnowledgeQaParam param);

    KnowledgeItemVO createShopQa(Long shopId, KnowledgeQaParam param);

    KnowledgeItemVO updateCurrentShopQa(Long id, KnowledgeQaParam param);

    KnowledgeItemVO updateShopQa(Long shopId, Long id, KnowledgeQaParam param);

    void deleteCurrentShopQa(Long id);

    void deleteShopQa(Long shopId, Long id);

    void clearCurrentShopKnowledge();

    void clearShopKnowledge(Long shopId);
}
