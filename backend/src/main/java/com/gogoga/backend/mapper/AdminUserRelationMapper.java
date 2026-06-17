package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserRelationMapper {

    List<String> selectDirectSubordinateUserIds(@Param("managerUserId") String managerUserId);
}
