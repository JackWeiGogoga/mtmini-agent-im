package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.RoleMenu;

import java.util.List;

/**
 * 角色菜单关联 Mapper 接口
 *
 * @author system
 * @since 2025-01-21
 */
@Mapper
public interface RoleMenuMapper {

    /**
     * 插入角色菜单关联
     *
     * @param roleMenu 角色菜单关联实体
     * @return 影响行数
     */
    int insert(RoleMenu roleMenu);

    /**
     * 批量插入角色菜单关联
     *
     * @param list 角色菜单关联列表
     * @return 影响行数
     */
    int batchInsert(@Param("list") List<RoleMenu> list);

    /**
     * 根据角色ID删除关联
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    int deleteByRoleId(@Param("roleId") String roleId);

    /**
     * 根据菜单ID删除关联
     *
     * @param menuId 菜单ID
     * @return 影响行数
     */
    int deleteByMenuId(@Param("menuId") String menuId);

    /**
     * 根据角色ID查询菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<String> selectMenuIdsByRoleId(@Param("roleId") String roleId);

    /**
     * 根据菜单ID查询角色ID列表
     *
     * @param menuId 菜单ID
     * @return 角色ID列表
     */
    List<String> selectRoleIdsByMenuId(@Param("menuId") String menuId);
}
