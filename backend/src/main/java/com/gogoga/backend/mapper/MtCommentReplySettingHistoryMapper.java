package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentReplySettingHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 门店评价自动回复配置历史 Mapper。
 */
@Mapper
public interface MtCommentReplySettingHistoryMapper {

    Integer selectMaxVersionNoByShopId(@Param("shopId") Long shopId);

    int insert(MtCommentReplySettingHistory history);
}
