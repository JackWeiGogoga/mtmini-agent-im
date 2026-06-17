package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.param.AssignMenusParam;
import com.gogoga.backend.param.RoleCreateParam;
import com.gogoga.backend.param.RoleUpdateParam;
import com.gogoga.backend.service.RoleService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.RoleVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 *
 * @author system
 * @since 2025-01-21
 */
@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
@RequiresLogin
public class RoleManageController {

    private final RoleService roleService;

    /**
     * 获取角色列表（分页）
     *
     * @param roleName 角色名称（可选，模糊查询）
     * @param status   状态（可选）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页角色列表
     */
    @GetMapping
    @RequiresPermission("system:role:list")
    public ResultVO<PageInfo<Role>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageInfo<Role> pageInfo = roleService.listRoles(roleName, status, pageNum, pageSize);
        return ResultVO.success(pageInfo);
    }

    /**
     * 获取所有角色列表（用于下拉选择）
     *
     * @return 角色列表
     */
    @GetMapping("/all")
    @RequiresPermission("system:role:list")
    public ResultVO<List<Role>> listAll() {
        List<Role> roles = roleService.listAllRoles();
        return ResultVO.success(roles);
    }

    /**
     * 根据角色ID获取角色详情
     *
     * @param roleId 角色ID
     * @return 角色详情
     */
    @GetMapping("/{roleId}")
    @RequiresPermission("system:role:query")
    public ResultVO<RoleVO> getById(@PathVariable String roleId) {
        RoleVO roleVO = roleService.getRoleDetail(roleId);
        return ResultVO.success(roleVO);
    }

    /**
     * 创建角色
     *
     * @param param 创建参数
     * @return 创建结果
     */
    @PostMapping
    @RequiresPermission("system:role:add")
    public ResultVO<String> create(@Valid @RequestBody RoleCreateParam param) {
        String roleId = roleService.createRole(param);
        return ResultVO.success(roleId);
    }

    /**
     * 更新角色
     *
     * @param param 更新参数
     * @return 更新结果
     */
    @PutMapping
    @RequiresPermission("system:role:edit")
    public ResultVO<Void> update(@Valid @RequestBody RoleUpdateParam param) {
        roleService.updateRole(param);
        return ResultVO.success();
    }

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     * @return 删除结果
     */
    @DeleteMapping("/{roleId}")
    @RequiresPermission("system:role:delete")
    public ResultVO<Void> delete(@PathVariable String roleId) {
        roleService.deleteRole(roleId);
        return ResultVO.success();
    }

    /**
     * 更新角色状态
     *
     * @param roleId 角色ID
     * @param status 状态
     * @return 更新结果
     */
    @PatchMapping("/{roleId}/status")
    @RequiresPermission("system:role:edit")
    public ResultVO<Void> updateStatus(@PathVariable String roleId, @RequestParam Integer status) {
        roleService.updateRoleStatus(roleId, status);
        return ResultVO.success();
    }

    /**
     * 分配角色菜单权限
     *
     * @param param 分配参数
     * @return 分配结果
     */
    @PostMapping("/assign-menus")
    @RequiresPermission("system:role:edit")
    public ResultVO<Void> assignMenus(@Valid @RequestBody AssignMenusParam param) {
        roleService.assignMenus(param.getRoleId(), param.getMenuIds());
        return ResultVO.success();
    }

    /**
     * 获取角色菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    @GetMapping("/{roleId}/menus")
    @RequiresPermission("system:role:query")
    public ResultVO<List<String>> getRoleMenus(@PathVariable String roleId) {
        List<String> menuIds = roleService.getRoleMenuIds(roleId);
        return ResultVO.success(menuIds);
    }
}
