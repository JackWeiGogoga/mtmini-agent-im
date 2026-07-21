package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtKnowledgeItem;
import com.gogoga.backend.enums.KnowledgeCategoryEnum;
import com.gogoga.backend.enums.KnowledgeSourceTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtKnowledgeDocumentMapper;
import com.gogoga.backend.mapper.MtKnowledgeItemMapper;
import com.gogoga.backend.param.KnowledgeQaParam;
import com.gogoga.backend.service.KnowledgeEmbeddingService;
import com.gogoga.backend.service.KnowledgeService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.SensitiveWordService;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import com.gogoga.backend.vo.KnowledgeItemVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final MtH5ShopService mtH5ShopService;
    private final MtKnowledgeItemMapper mtKnowledgeItemMapper;
    private final MtKnowledgeDocumentMapper mtKnowledgeDocumentMapper;
    private final MtKnowledgeChunkMapper mtKnowledgeChunkMapper;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final MtProperties mtProperties;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public List<KnowledgeItemVO> listCurrentShopQa() {
        return listShopQa(mtH5ShopService.getCurrentShop().getId());
    }

    @Override
    public List<KnowledgeItemVO> listShopQa(Long shopId) {
        requireShopId(shopId);
        return mtKnowledgeItemMapper.selectByShopId(shopId).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeItemVO createCurrentShopQa(KnowledgeQaParam param) {
        return createShopQa(mtH5ShopService.getCurrentShop().getId(), param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeItemVO createShopQa(Long shopId, KnowledgeQaParam param) {
        requireShopId(shopId);
        int maxCount = mtProperties.getKnowledge().getMaxQaItemsPerShop();
        if (mtKnowledgeItemMapper.countEnabledQaByShopId(shopId) >= maxCount) {
            throw new BusinessException("每个商户最多上传 " + maxCount + " 条问答知识");
        }
        LocalDateTime now = LocalDateTime.now();
        MtKnowledgeItem item = new MtKnowledgeItem();
        item.setShopId(shopId);
        applyParam(item, param);
        item.setDeleted(false);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        mtKnowledgeItemMapper.insert(item);
        rebuildQaChunk(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeItemVO updateCurrentShopQa(Long id, KnowledgeQaParam param) {
        return updateShopQa(mtH5ShopService.getCurrentShop().getId(), id, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeItemVO updateShopQa(Long shopId, Long id, KnowledgeQaParam param) {
        requireShopId(shopId);
        MtKnowledgeItem item = requireCurrentShopQa(id, shopId);
        applyParam(item, param);
        item.setUpdateTime(LocalDateTime.now());
        mtKnowledgeItemMapper.updateById(item);
        rebuildQaChunk(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopQa(Long id) {
        deleteShopQa(mtH5ShopService.getCurrentShop().getId(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopQa(Long shopId, Long id) {
        MtKnowledgeItem item = requireCurrentShopQa(id, shopId);
        mtKnowledgeItemMapper.softDeleteByIdAndShopId(item.getId(), shopId);
        knowledgeVectorStore.deleteBySource(KnowledgeSourceTypeEnum.QA.name(), item.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCurrentShopKnowledge() {
        clearShopKnowledge(mtH5ShopService.getCurrentShop().getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearShopKnowledge(Long shopId) {
        requireShopId(shopId);
        LocalDateTime now = LocalDateTime.now();
        mtKnowledgeItemMapper.softDeleteByShopId(shopId);
        mtKnowledgeDocumentMapper.softDeleteByShopId(shopId, now);
        mtKnowledgeChunkMapper.softDeleteByShopId(shopId, now);
    }

    private void applyParam(MtKnowledgeItem item, KnowledgeQaParam param) {
        if (param == null) {
            throw new BusinessException("知识参数不能为空");
        }
        String text = "标题：" + StringUtils.defaultString(param.getTitle()) + "\n"
                + "问题：" + StringUtils.defaultString(param.getQuestion()) + "\n"
                + "回答：" + StringUtils.defaultString(param.getAnswer());
        sensitiveWordService.validateKnowledgeText(
                item.getShopId(), KnowledgeSourceTypeEnum.QA.name(), text);
        item.setTitle(StringUtils.trim(param.getTitle()));
        item.setQuestion(StringUtils.trim(param.getQuestion()));
        item.setAnswer(StringUtils.trim(param.getAnswer()));
        item.setCategory(KnowledgeCategoryEnum.fromValue(param.getCategory()).name());
        item.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
    }

    private void rebuildQaChunk(MtKnowledgeItem item) {
        knowledgeVectorStore.deleteBySource(KnowledgeSourceTypeEnum.QA.name(), item.getId());
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return;
        }
        String text = "标题：" + StringUtils.defaultString(item.getTitle()) + "\n"
                + "问题：" + StringUtils.defaultString(item.getQuestion()) + "\n"
                + "回答：" + StringUtils.defaultString(item.getAnswer());
        float[] embedding = knowledgeEmbeddingService.embed(text);
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        MtKnowledgeChunk chunk = new MtKnowledgeChunk();
        chunk.setShopId(item.getShopId());
        chunk.setSourceType(KnowledgeSourceTypeEnum.QA.name());
        chunk.setSourceId(item.getId());
        chunk.setItemId(item.getId());
        chunk.setChunkText(text);
        chunk.setCategory(KnowledgeCategoryEnum.fromValue(item.getCategory()).name());
        chunk.setEmbeddingProvider(knowledge.getEmbeddingProvider());
        chunk.setEmbeddingModel(knowledge.getEmbeddingModel());
        chunk.setEmbeddingDim(embedding.length);
        chunk.setEmbeddingBlob(EmbeddingVectorUtil.toFloat32Blob(embedding));
        chunk.setTokenCount(Math.max(1, text.length() / 2));
        chunk.setEnabled(true);
        chunk.setDeleted(false);
        chunk.setCreateTime(now);
        chunk.setUpdateTime(now);
        knowledgeVectorStore.upsertChunk(chunk);
    }

    private MtKnowledgeItem requireCurrentShopQa(Long id, Long shopId) {
        requireShopId(shopId);
        MtKnowledgeItem item = id == null ? null : mtKnowledgeItemMapper.selectById(id);
        if (item == null || Boolean.TRUE.equals(item.getDeleted()) || !shopId.equals(item.getShopId())) {
            throw new BusinessException(404, "问答知识不存在");
        }
        return item;
    }

    private void requireShopId(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
    }

    private KnowledgeItemVO toVO(MtKnowledgeItem item) {
        KnowledgeItemVO vo = new KnowledgeItemVO();
        vo.setId(item.getId());
        vo.setShopId(item.getShopId());
        vo.setTitle(item.getTitle());
        vo.setQuestion(item.getQuestion());
        vo.setAnswer(item.getAnswer());
        vo.setCategory(KnowledgeCategoryEnum.fromValue(item.getCategory()).name());
        vo.setEnabled(item.getEnabled());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }
}
