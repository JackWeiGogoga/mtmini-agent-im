package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzAppAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 有赞应用授权 Mapper
 */
@Mapper
public interface YzAppAuthMapper {

    YzAppAuth selectByNaturalKey(
            @Param("orderNo") String orderNo,
            @Param("kdtId") Long kdtId,
            @Param("appId") Long appId
    );

    int insert(YzAppAuth auth);

    int updateById(YzAppAuth auth);
}
