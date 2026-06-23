package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplyKnowledgeScopeEnum;
import com.gogoga.backend.enums.CommentReplyKnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeDocumentMapper;
import com.gogoga.backend.param.CommentReplyQaKnowledgeParam;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplyKnowledgeService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentReplyKnowledgeServiceImpl implements CommentReplyKnowledgeService {

    private final MtH5ShopService mtH5ShopService;
    private final MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;
    private final MtReplyKnowledgeDocumentMapper mtReplyKnowledgeDocumentMapper;
    private final MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;
    private final CommentReplyEmbeddingService commentReplyEmbeddingService;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final MtProperties mtProperties;

    @Override
    public List<CommentReplyKnowledgeItemVO> listCurrentShopQa() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return listShopQa(shop.getId());
    }

    @Override
    public List<CommentReplyKnowledgeItemVO> listShopQa(Long shopId) {
        requireShopId(shopId);
        return mtReplyKnowledgeItemMapper.selectByShopId(shopId, CommentReplyKnowledgeSourceTypeEnum.QA.name())
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO createCurrentShopQa(CommentReplyQaKnowledgeParam param) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return createShopQa(shop.getId(), param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO createShopQa(Long shopId, CommentReplyQaKnowledgeParam param) {
        requireShopId(shopId);
        int count = mtReplyKnowledgeItemMapper.countEnabledQaByShopId(shopId);
        int maxCount = mtProperties.getComment().getKnowledge().getMaxQaItemsPerShop();
        if (count >= maxCount) {
            throw new BusinessException("每个商户最多上传 " + maxCount + " 条问答知识");
        }
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment());
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setShopId(shopId);
        item.setScope(CommentReplyKnowledgeScopeEnum.SHOP.name());
        item.setSourceType(CommentReplyKnowledgeSourceTypeEnum.QA.name());
        item.setSentiment(sentiment.name());
        item.setTitle(StringUtils.trim(param.getTitle()));
        item.setQuestion(StringUtils.trim(param.getQuestion()));
        item.setAnswer(StringUtils.trim(param.getAnswer()));
        item.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
        item.setDeleted(false);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        mtReplyKnowledgeItemMapper.insert(item);
        rebuildQaChunk(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO updateCurrentShopQa(Long id, CommentReplyQaKnowledgeParam param) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return updateShopQa(shop.getId(), id, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO updateShopQa(Long shopId, Long id, CommentReplyQaKnowledgeParam param) {
        requireShopId(shopId);
        MtReplyKnowledgeItem item = requireCurrentShopQa(id, shopId);
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment());
        item.setSentiment(sentiment.name());
        item.setTitle(StringUtils.trim(param.getTitle()));
        item.setQuestion(StringUtils.trim(param.getQuestion()));
        item.setAnswer(StringUtils.trim(param.getAnswer()));
        item.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
        item.setUpdateTime(LocalDateTime.now());
        mtReplyKnowledgeItemMapper.updateById(item);
        rebuildQaChunk(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopQa(Long id) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        deleteShopQa(shop.getId(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopQa(Long shopId, Long id) {
        requireShopId(shopId);
        MtReplyKnowledgeItem item = requireCurrentShopQa(id, shopId);
        mtReplyKnowledgeItemMapper.softDeleteByIdAndShopId(item.getId(), shopId);
        knowledgeVectorStore.deleteBySource(CommentReplyKnowledgeSourceTypeEnum.QA.name(), item.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCurrentShopKnowledge() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        clearShopKnowledge(shop.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearShopKnowledge(Long shopId) {
        requireShopId(shopId);
        LocalDateTime now = LocalDateTime.now();
        mtReplyKnowledgeItemMapper.softDeleteByShopId(shopId);
        mtReplyKnowledgeDocumentMapper.softDeleteByShopId(shopId, now);
        mtReplyKnowledgeChunkMapper.softDeleteByShopId(shopId, now);
    }

    private void rebuildQaChunk(MtReplyKnowledgeItem item) {
        knowledgeVectorStore.deleteBySource(CommentReplyKnowledgeSourceTypeEnum.QA.name(), item.getId());
        if (!Boolean.TRUE.equals(item.getEnabled())) {
            return;
        }
        String text = buildQaChunkText(item);
        float[] embedding = commentReplyEmbeddingService.embed(text);
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
        chunk.setShopId(item.getShopId());
        chunk.setSourceType(CommentReplyKnowledgeSourceTypeEnum.QA.name());
        chunk.setSourceId(item.getId());
        chunk.setItemId(item.getId());
        chunk.setDocumentId(null);
        chunk.setSentiment(item.getSentiment());
        chunk.setChunkText(text);
        chunk.setEmbeddingProvider(knowledge.getEmbeddingProvider());
        chunk.setEmbeddingModel(knowledge.getEmbeddingModel());
        chunk.setEmbeddingDim(embedding.length);
        chunk.setEmbeddingBlob(EmbeddingVectorUtil.toFloat32Blob(embedding));
        chunk.setTokenCount(estimateTokenCount(text));
        chunk.setEnabled(true);
        chunk.setDeleted(false);
        chunk.setCreateTime(now);
        chunk.setUpdateTime(now);
        knowledgeVectorStore.upsertChunk(chunk);
    }

    private String buildQaChunkText(MtReplyKnowledgeItem item) {
        return "标题：" + StringUtils.defaultString(item.getTitle()) + "\n"
                + "评论类型：" + StringUtils.defaultString(item.getSentiment()) + "\n"
                + "问题：" + StringUtils.defaultString(item.getQuestion()) + "\n"
                + "回答：" + StringUtils.defaultString(item.getAnswer());
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, StringUtils.length(text) / 2);
    }

    private MtReplyKnowledgeItem requireCurrentShopQa(Long id, Long shopId) {
        if (id == null) {
            throw new BusinessException("知识ID不能为空");
        }
        MtReplyKnowledgeItem item = mtReplyKnowledgeItemMapper.selectById(id);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())
                || !shopId.equals(item.getShopId())
                || !StringUtils.equals(item.getSourceType(), CommentReplyKnowledgeSourceTypeEnum.QA.name())) {
            throw new BusinessException(404, "问答知识不存在");
        }
        return item;
    }

    private void requireShopId(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
    }

    private CommentReplySentimentEnum requireSentiment(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        try {
            return CommentReplySentimentEnum.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("评论类型不合法");
        }
    }

    private CommentReplyKnowledgeItemVO toVO(MtReplyKnowledgeItem item) {
        CommentReplyKnowledgeItemVO vo = new CommentReplyKnowledgeItemVO();
        vo.setId(item.getId());
        vo.setShopId(item.getShopId());
        vo.setScope(item.getScope());
        vo.setSourceType(item.getSourceType());
        vo.setSentiment(item.getSentiment());
        vo.setTitle(item.getTitle());
        vo.setQuestion(item.getQuestion());
        vo.setAnswer(item.getAnswer());
        vo.setEnabled(item.getEnabled());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }
}
