package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtKnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 门店知识条目 Mapper。
 */
@Mapper
public interface MtKnowledgeItemMapper {

    MtKnowledgeItem selectById(@Param("id") Long id);

    List<MtKnowledgeItem> selectByShopId(@Param("shopId") Long shopId);

    int countEnabledQaByShopId(@Param("shopId") Long shopId);

    int countAvailableShopItems(@Param("shopId") Long shopId);

    int insert(MtKnowledgeItem item);

    int updateById(MtKnowledgeItem item);

    int softDeleteByIdAndShopId(@Param("id") Long id, @Param("shopId") Long shopId);

    int softDeleteByShopId(@Param("shopId") Long shopId);
}
