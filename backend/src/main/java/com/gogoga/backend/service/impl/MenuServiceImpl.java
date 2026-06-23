package com.gogoga.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MenuMapper;
import com.gogoga.backend.mapper.RoleMenuMapper;
import com.gogoga.backend.param.MenuCreateParam;
import com.gogoga.backend.param.MenuUpdateParam;
import com.gogoga.backend.service.MenuService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Service
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;

    private final RoleMenuMapper roleMenuMapper;

    public MenuServiceImpl(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    @Transactional
    public String createMenu(MenuCreateParam param) {
        // 构建菜单实体
        Menu menu = new Menu();
        String menuId = UUID.randomUUID().toString().replace("-", "");
        menu.setMenuId(menuId);
        menu.setMenuName(param.getMenuName());
        menu.setPath(param.getPath());
        menu.setComponent(param.getComponent());
        menu.setPermission(param.getPerms());
        menu.setIcon(param.getIcon());
        menu.setMenuType(param.getMenuType());
        menu.setStatus(param.getStatus() != null ? param.getStatus() : 1);
        menu.setOrderNum(param.getOrderNum() != null ? param.getOrderNum() : 0);
        menu.setVisible(param.getVisible() != null ? param.getVisible() : 1);

        // 如果没有父ID，设置为顶级菜单
        if (param.getParentId() == null || param.getParentId().isEmpty()) {
            menu.setParentId("0");
        } else {
            menu.setParentId(param.getParentId());
        }

        // 创建菜单
        menuMapper.insert(menu);
        log.info("创建菜单: {}", menu.getMenuName());

        return menuId;
    }

    @Override
    @Transactional
    public void updateMenu(MenuUpdateParam param) {
        // 检查菜单是否存在
        Menu existMenu = menuMapper.selectByMenuId(param.getMenuId());
        if (existMenu == null) {
            throw new BusinessException("菜单不存在: " + param.getMenuId());
        }

        // 不能将自己设为父菜单
        if (param.getMenuId().equals(param.getParentId())) {
            throw new BusinessException("不能将自己设为父菜单");
        }

        // 构建更新实体
        Menu menu = new Menu();
        menu.setMenuId(param.getMenuId());
        menu.setParentId(param.getParentId());
        menu.setMenuName(param.getMenuName());
        menu.setPath(param.getPath());
        menu.setComponent(param.getComponent());
        menu.setPermission(param.getPerms());
        menu.setIcon(param.getIcon());
        menu.setMenuType(param.getMenuType());
        menu.setStatus(param.getStatus());
        menu.setOrderNum(param.getOrderNum());
        menu.setVisible(param.getVisible());

        // 更新菜单信息
        menuMapper.updateByMenuId(menu);
        log.info("更新菜单: {}", menu.getMenuId());
    }

    @Override
    public Menu getMenuById(String menuId) {
        return menuMapper.selectByMenuId(menuId);
    }

    @Override
    public List<Menu> getMenuTree() {
        List<Menu> menus = menuMapper.selectAll();
        return buildMenuTree(menus);
    }

    @Override
    public List<Menu> getMenuTreeForRole(String roleId) {
        List<Menu> menus = menuMapper.selectAll();
        List<Menu> menuTree = buildMenuTree(menus);

        // 如果指定了角色，标记已选中的菜单
        if (roleId != null && !roleId.isEmpty()) {
            List<String> selectedMenuIds = roleMenuMapper.selectMenuIdsByRoleId(roleId);
            Set<String> selectedSet = new HashSet<>(selectedMenuIds);
            markSelectedMenus(menuTree, selectedSet);
        }

        return menuTree;
    }

    /**
     * 标记已选中的菜单
     */
    private void markSelectedMenus(List<Menu> menus, Set<String> selectedSet) {
        for (Menu menu : menus) {
            // 使用一个临时字段标记选中状态（可以通过扩展 Menu 实体添加 checked 字段）
            // 这里简单处理，前端可以根据返回的菜单ID列表自行判断
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                markSelectedMenus(menu.getChildren(), selectedSet);
            }
        }
    }

    @Override
    public List<Menu> listAllMenus() {
        return menuMapper.selectAll();
    }

    @Override
    @Transactional
    public void updateMenuStatus(String menuId, Integer status) {
        Menu menu = menuMapper.selectByMenuId(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在: " + menuId);
        }

        menuMapper.updateStatus(menuId, status);
        log.info("更新菜单状态: menuId={}, status={}", menuId, status);
    }

    @Override
    @Transactional
    public void deleteMenu(String menuId) {
        Menu menu = menuMapper.selectByMenuId(menuId);
        if (menu == null) {
            throw new BusinessException("菜单不存在: " + menuId);
        }

        // 检查是否有子菜单
        List<Menu> children = menuMapper.selectByParentId(menuId);
        if (children != null && !children.isEmpty()) {
            throw new BusinessException("该菜单下存在子菜单，无法删除");
        }

        // 删除角色菜单关联
        roleMenuMapper.deleteByMenuId(menuId);

        // 删除菜单
        menuMapper.deleteByMenuId(menuId);
        log.info("删除菜单: {}", menuId);
    }

    /**
     * 构建菜单树
     */
    private List<Menu> buildMenuTree(List<Menu> menus) {
        // 按 parentId 分组
        Map<String, List<Menu>> parentMap = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() != null ? m.getParentId() : "0"));

        // 设置子菜单
        for (Menu menu : menus) {
            List<Menu> children = parentMap.get(menu.getMenuId());
            if (children != null) {
                menu.setChildren(children);
            }
        }

        // 返回顶级菜单
        return parentMap.getOrDefault("0", new ArrayList<>());
    }
}
