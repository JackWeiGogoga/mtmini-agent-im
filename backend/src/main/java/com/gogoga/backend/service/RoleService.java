package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.param.RoleCreateParam;
import com.gogoga.backend.param.RoleUpdateParam;
import com.gogoga.backend.vo.RoleVO;

import java.util.List;

/**
 * 角色服务接口
 *
 * @author system
 * @since 2025-01-21
 */
public interface RoleService {

    /**
     * 创建角色
     *
     * @param param 创建参数
     * @return 角色ID
     */
    String createRole(RoleCreateParam param);

    /**
     * 更新角色
     *
     * @param param 更新参数
     */
    void updateRole(RoleUpdateParam param);

    /**
     * 根据角色ID查询角色
     *
     * @param roleId 角色ID
     * @return 角色实体
     */
    Role getRoleByRoleId(String roleId);

    /**
     * 获取角色详情（包含菜单ID列表）
     *
     * @param roleId 角色ID
     * @return 角色VO
     */
    RoleVO getRoleDetail(String roleId);

    /**
     * 分页查询角色列表
     *
     * @param roleName 角色名称（模糊查询）
     * @param status   状态
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageInfo<Role> listRoles(String roleName, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 查询所有角色列表
     *
     * @return 角色列表
     */
    List<Role> listAllRoles();

    /**
     * 更新角色状态
     *
     * @param roleId 角色ID
     * @param status 状态
     */
    void updateRoleStatus(String roleId, Integer status);

    /**
     * 删除角色
     *
     * @param roleId 角色ID
     */
    void deleteRole(String roleId);

    /**
     * 分配角色菜单
     *
     * @param roleId  角色ID
     * @param menuIds 菜单ID列表
     */
    void assignMenus(String roleId, List<String> menuIds);

    /**
     * 获取角色菜单ID列表
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    List<String> getRoleMenuIds(String roleId);
}
