package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImAutoReplySetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MtImAutoReplySettingMapper {

    MtImAutoReplySetting selectByShopId(@Param("shopId") Long shopId);

    int insert(MtImAutoReplySetting setting);

    int updateById(MtImAutoReplySetting setting);
}
