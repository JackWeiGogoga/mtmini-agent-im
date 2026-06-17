package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.Menu;

import java.util.List;

/**
 * 菜单 Mapper 接口
 *
 * @author system
 * @since 2025-01-21
 */
@Mapper
public interface MenuMapper {

    /**
     * 插入菜单
     *
     * @param menu 菜单实体
     * @return 影响行数
     */
    int insert(Menu menu);

    /**
     * 根据菜单ID更新菜单
     *
     * @param menu 菜单实体
     * @return 影响行数
     */
    int updateByMenuId(Menu menu);

    /**
     * 根据菜单ID查询菜单
     *
     * @param menuId 菜单ID
     * @return 菜单实体
     */
    Menu selectByMenuId(@Param("menuId") String menuId);

    /**
     * 查询所有菜单列表
     *
     * @return 菜单列表
     */
    List<Menu> selectAll();

    /**
     * 查询所有启用的菜单
     *
     * @return 菜单列表
     */
    List<Menu> selectAllEnabled();

    /**
     * 根据父ID查询子菜单
     *
     * @param parentId 父菜单ID
     * @return 菜单列表
     */
    List<Menu> selectByParentId(@Param("parentId") String parentId);

    /**
     * 更新菜单状态
     *
     * @param menuId 菜单ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("menuId") String menuId, @Param("status") Integer status);

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     * @return 影响行数
     */
    int deleteByMenuId(@Param("menuId") String menuId);

    /**
     * 根据角色ID查询菜单列表
     *
     * @param roleId 角色ID
     * @return 菜单列表
     */
    List<Menu> selectByRoleId(@Param("roleId") String roleId);

    /**
     * 根据用户ID查询菜单列表（含权限）
     *
     * @param userId 用户ID
     * @return 菜单列表
     */
    List<Menu> selectByUserId(@Param("userId") String userId);

    /**
     * 查询用户权限标识列表
     *
     * @param userId 用户ID
     * @return 权限标识列表
     */
    List<String> selectPermsByUserId(@Param("userId") String userId);
}
