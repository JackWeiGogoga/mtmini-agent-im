package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.SalesUserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SalesUserProfileMapper {

    int insert(SalesUserProfile profile);

    int updateById(SalesUserProfile profile);

    SalesUserProfile selectByUserId(@Param("userId") String userId);

    int clearReceiptCodeUrl(@Param("userId") String userId);
}
