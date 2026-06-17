package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzShop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 有赞店铺 Mapper
 */
@Mapper
public interface YzShopMapper {

    YzShop selectById(@Param("id") Long id);

    YzShop selectByKdtId(@Param("kdtId") Long kdtId);

    int insert(YzShop shop);

    int updateById(YzShop shop);
}
