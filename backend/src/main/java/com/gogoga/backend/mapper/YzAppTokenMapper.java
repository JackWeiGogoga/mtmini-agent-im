package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzAppToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 有赞应用 token Mapper
 */
@Mapper
public interface YzAppTokenMapper {

    YzAppToken selectById(@Param("id") Long id);

    YzAppToken selectByAuthorityId(@Param("authorityId") String authorityId);

    List<YzAppToken> selectRefreshCandidates(@Param("expiresBefore") LocalDateTime expiresBefore);

    int insert(YzAppToken token);

    int updateById(YzAppToken token);

    int linkShopByKdtId(@Param("kdtId") Long kdtId, @Param("yzShopId") Long yzShopId);
}
