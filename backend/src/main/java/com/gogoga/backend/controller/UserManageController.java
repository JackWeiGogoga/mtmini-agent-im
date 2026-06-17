package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AssignRolesParam;
import com.gogoga.backend.param.UserCreateParam;
import com.gogoga.backend.param.UserUpdateParam;
import com.gogoga.backend.service.UserService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.UserVO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 *
 * @author system
 * @since 2025-01-21
 */
@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
@RequiresLogin
public class UserManageController {

    private final UserService userService;

    /**
     * 获取用户列表（分页，包含角色等详细信息）
     *
     * @param username 用户名（可选，模糊查询）
     * @param status   状态（可选）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页用户列表
     */
    @GetMapping
    @RequiresPermission("system:user:list")
    public ResultVO<PageInfo<UserVO>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageInfo<UserVO> pageInfo = userService.listUsersWithDetail(username, status, pageNum, pageSize);
        return ResultVO.success(pageInfo);
    }

    /**
     * 根据用户ID获取用户详情
     *
     * @param userId 用户ID
     * @return 用户详情
     */
    @GetMapping("/{userId}")
    @RequiresPermission("system:user:query")
    public ResultVO<UserVO> getById(@PathVariable String userId) {
        UserVO userVO = userService.getUserDetail(userId);
        return ResultVO.success(userVO);
    }

    /**
     * 创建用户
     *
     * @param param 创建参数
     * @return 创建结果
     */
    @PostMapping
    @RequiresPermission("system:user:add")
    public ResultVO<String> create(@Valid @RequestBody UserCreateParam param) {
        String userId = userService.createUser(param);
        return ResultVO.success(userId);
    }

    /**
     * 更新用户
     *
     * @param param 更新参数
     * @return 更新结果
     */
    @PutMapping
    @RequiresPermission("system:user:edit")
    public ResultVO<Void> update(@Valid @RequestBody UserUpdateParam param) {
        userService.updateUser(param);
        return ResultVO.success();
    }

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}")
    @RequiresPermission("system:user:delete")
    public ResultVO<Void> delete(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResultVO.success();
    }

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 更新结果
     */
    @PatchMapping("/{userId}/status")
    @RequiresPermission("system:user:edit")
    public ResultVO<Void> updateStatus(@PathVariable String userId, @RequestParam Integer status) {
        userService.updateUserStatus(userId, status);
        return ResultVO.success();
    }

    /**
     * 分配用户角色
     *
     * @param param 分配参数
     * @return 分配结果
     */
    @PostMapping("/assign-roles")
    @RequiresPermission("system:user:edit")
    public ResultVO<Void> assignRoles(@Valid @RequestBody AssignRolesParam param) {
        userService.assignRoles(param.getUserId(), param.getRoleIds());
        return ResultVO.success();
    }

    /**
     * 获取用户角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @GetMapping("/{userId}/roles")
    @RequiresPermission("system:user:query")
    public ResultVO<List<String>> getUserRoles(@PathVariable String userId) {
        List<String> roleIds = userService.getUserRoleIds(userId);
        return ResultVO.success(roleIds);
    }
}
