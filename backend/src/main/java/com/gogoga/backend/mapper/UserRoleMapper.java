package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.UserRole;

import java.util.List;

/**
 * 用户角色关联 Mapper 接口
 *
 * @author system
 * @since 2025-01-21
 */
@Mapper
public interface UserRoleMapper {

    /**
     * 插入用户角色关联
     *
     * @param userRole 用户角色关联实体
     * @return 影响行数
     */
    int insert(UserRole userRole);

    /**
     * 幂等插入用户角色关联
     *
     * @param userRole 用户角色关联实体
     * @return 影响行数
     */
    int insertIgnoreDuplicate(UserRole userRole);

    /**
     * 批量插入用户角色关联
     *
     * @param list 用户角色关联列表
     * @return 影响行数
     */
    int batchInsert(@Param("list") List<UserRole> list);

    /**
     * 根据用户ID删除关联
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") String userId);

    /**
     * 根据角色ID删除关联
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    int deleteByRoleId(@Param("roleId") String roleId);

    /**
     * 根据用户ID查询角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<String> selectRoleIdsByUserId(@Param("userId") String userId);

    /**
     * 根据角色ID查询用户ID列表
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    List<String> selectUserIdsByRoleId(@Param("roleId") String roleId);
}
