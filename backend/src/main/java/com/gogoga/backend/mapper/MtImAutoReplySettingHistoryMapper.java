package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImAutoReplySettingHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MtImAutoReplySettingHistoryMapper {

    Integer selectMaxVersionNoByShopId(@Param("shopId") Long shopId);

    int insert(MtImAutoReplySettingHistory history);
}
