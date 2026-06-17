package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.AdminUserDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserDataScopeMapper {

    AdminUserDataScope selectByUserId(@Param("userId") String userId);
}
