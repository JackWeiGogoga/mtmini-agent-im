package com.gogoga.backend.service;

import java.util.List;

import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.param.MenuCreateParam;
import com.gogoga.backend.param.MenuUpdateParam;

/**
 * 菜单服务接口
 *
 * @author system
 * @since 2025-01-21
 */
public interface MenuService {

    /**
     * 创建菜单
     *
     * @param param 创建参数
     * @return 菜单ID
     */
    String createMenu(MenuCreateParam param);

    /**
     * 更新菜单
     *
     * @param param 更新参数
     */
    void updateMenu(MenuUpdateParam param);

    /**
     * 根据菜单ID查询菜单
     *
     * @param menuId 菜单ID
     * @return 菜单实体
     */
    Menu getMenuById(String menuId);

    /**
     * 获取菜单树形结构
     *
     * @return 菜单树
     */
    List<Menu> getMenuTree();

    /**
     * 获取角色权限分配的菜单树
     *
     * @param roleId 角色ID（可选）
     * @return 菜单树
     */
    List<Menu> getMenuTreeForRole(String roleId);

    /**
     * 查询所有菜单（平铺结构）
     *
     * @return 菜单列表
     */
    List<Menu> listAllMenus();

    /**
     * 更新菜单状态
     *
     * @param menuId 菜单ID
     * @param status 状态
     */
    void updateMenuStatus(String menuId, Integer status);

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     */
    void deleteMenu(String menuId);
}
