package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImBroadcastSettingHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MtImBroadcastSettingHistoryMapper {

    Integer selectMaxVersionNoByShopId(@Param("shopId") Long shopId);

    int insert(MtImBroadcastSettingHistory history);

    /**
     * 按门店倒序查询变更历史，供总控台抽屉分页展示。
     */
    List<MtImBroadcastSettingHistory> selectByShopId(@Param("shopId") Long shopId);
}
