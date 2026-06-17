package com.gogoga.backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.param.MenuCreateParam;
import com.gogoga.backend.param.MenuUpdateParam;
import com.gogoga.backend.service.MenuService;
import com.gogoga.backend.vo.ResultVO;

import java.util.List;

/**
 * 菜单管理控制器
 *
 * @author system
 * @since 2025-01-21
 */
@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
@RequiresLogin
public class MenuManageController {

    private final MenuService menuService;

    /**
     * 获取菜单树形列表
     *
     * @return 菜单树
     */
    @GetMapping("/tree")
    @RequiresPermission("system:menu:list")
    public ResultVO<List<Menu>> getMenuTree() {
        List<Menu> menuTree = menuService.getMenuTree();
        return ResultVO.success(menuTree);
    }

    /**
     * 获取所有菜单列表（平铺）
     *
     * @return 菜单列表
     */
    @GetMapping("/all")
    @RequiresPermission("system:menu:list")
    public ResultVO<List<Menu>> listAll() {
        List<Menu> menus = menuService.listAllMenus();
        return ResultVO.success(menus);
    }

    /**
     * 根据菜单ID获取菜单详情
     *
     * @param menuId 菜单ID
     * @return 菜单详情
     */
    @GetMapping("/{menuId}")
    @RequiresPermission("system:menu:query")
    public ResultVO<Menu> getById(@PathVariable String menuId) {
        Menu menu = menuService.getMenuById(menuId);
        return ResultVO.success(menu);
    }

    /**
     * 创建菜单
     *
     * @param param 创建参数
     * @return 创建结果
     */
    @PostMapping
    @RequiresPermission("system:menu:add")
    public ResultVO<String> create(@Valid @RequestBody MenuCreateParam param) {
        String menuId = menuService.createMenu(param);
        return ResultVO.success(menuId);
    }

    /**
     * 更新菜单
     *
     * @param param 更新参数
     * @return 更新结果
     */
    @PutMapping
    @RequiresPermission("system:menu:edit")
    public ResultVO<Void> update(@Valid @RequestBody MenuUpdateParam param) {
        menuService.updateMenu(param);
        return ResultVO.success();
    }

    /**
     * 删除菜单
     *
     * @param menuId 菜单ID
     * @return 删除结果
     */
    @DeleteMapping("/{menuId}")
    @RequiresPermission("system:menu:delete")
    public ResultVO<Void> delete(@PathVariable String menuId) {
        menuService.deleteMenu(menuId);
        return ResultVO.success();
    }

    /**
     * 更新菜单状态
     *
     * @param menuId 菜单ID
     * @param status 状态
     * @return 更新结果
     */
    @PatchMapping("/{menuId}/status")
    @RequiresPermission("system:menu:edit")
    public ResultVO<Void> updateStatus(@PathVariable String menuId, @RequestParam Integer status) {
        menuService.updateMenuStatus(menuId, status);
        return ResultVO.success();
    }

    /**
     * 获取角色菜单树（用于权限分配时的选择）
     *
     * @param roleId 角色ID（可选，用于标记已选中的菜单）
     * @return 菜单树
     */
    @GetMapping("/role-tree")
    @RequiresPermission("system:menu:list")
    public ResultVO<List<Menu>> getRoleMenuTree(@RequestParam(required = false) String roleId) {
        List<Menu> menuTree = menuService.getMenuTreeForRole(roleId);
        return ResultVO.success(menuTree);
    }
}
