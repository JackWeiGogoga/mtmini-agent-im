package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评论回复知识条目 Mapper。
 */
@Mapper
public interface MtReplyKnowledgeItemMapper {

    MtReplyKnowledgeItem selectById(@Param("id") Long id);

    List<MtReplyKnowledgeItem> selectOfficialTemplates(@Param("sentiment") String sentiment);

    List<MtReplyKnowledgeItem> selectOfficialTemplatesForAdmin(@Param("sentiment") String sentiment);

    List<MtReplyKnowledgeItem> selectOfficialTemplatesIncludingDeleted(@Param("sentiment") String sentiment);

    List<MtReplyKnowledgeItem> selectByShopId(@Param("shopId") Long shopId, @Param("sourceType") String sourceType);

    int countEnabledQaByShopId(@Param("shopId") Long shopId);

    int countAvailableShopItems(@Param("shopId") Long shopId);

    int insert(MtReplyKnowledgeItem item);

    int updateById(MtReplyKnowledgeItem item);

    int softDeleteByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId);
}
