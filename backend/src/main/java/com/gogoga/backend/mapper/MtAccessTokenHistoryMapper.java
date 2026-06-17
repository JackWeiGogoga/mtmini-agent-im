package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtAccessTokenHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 美团 token 历史 Mapper。
 */
@Mapper
public interface MtAccessTokenHistoryMapper {

    int insert(MtAccessTokenHistory history);

    MtAccessTokenHistory selectById(@Param("id") Long id);
}
