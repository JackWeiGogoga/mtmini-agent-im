package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.Role;

import java.util.List;

/**
 * 角色 Mapper 接口
 *
 * @author system
 * @since 2025-01-21
 */
@Mapper
public interface RoleMapper {

    /**
     * 插入角色
     *
     * @param role 角色实体
     * @return 影响行数
     */
    int insert(Role role);

    /**
     * 根据角色ID更新角色
     *
     * @param role 角色实体
     * @return 影响行数
     */
    int updateByRoleId(Role role);

    /**
     * 根据角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色实体
     */
    Role selectByRoleId(@Param("roleId") String roleId);

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 角色实体
     */
    Role selectByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 查询角色列表
     *
     * @param keyword 搜索关键词（角色编码或名称）
     * @param status  状态
     * @return 角色列表
     */
    List<Role> selectList(@Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * 查询所有启用的角色
     *
     * @return 角色列表
     */
    List<Role> selectAllEnabled();

    /**
     * 更新角色状态
     *
     * @param roleId 角色ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("roleId") String roleId, @Param("status") Integer status);

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    int deleteByRoleId(@Param("roleId") String roleId);

    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> selectByUserId(@Param("userId") String userId);

    /**
     * 根据角色ID列表查询角色
     *
     * @param roleIds 角色ID列表
     * @return 角色列表
     */
    List<Role> selectByRoleIds(@Param("roleIds") List<String> roleIds);
}
