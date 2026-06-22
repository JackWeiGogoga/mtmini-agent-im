package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentReplySetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店评价自动回复配置 Mapper。
 */
@Mapper
public interface MtCommentReplySettingMapper {

    MtCommentReplySetting selectByShopId(@Param("shopId") Long shopId);

    int insert(MtCommentReplySetting setting);

    int updateById(MtCommentReplySetting setting);
}
