package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.entity.RoleMenu;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.RoleMenuMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.param.RoleCreateParam;
import com.gogoga.backend.param.RoleUpdateParam;
import com.gogoga.backend.service.RoleService;
import com.gogoga.backend.vo.RoleVO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 角色服务实现
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Override
    @Transactional
    public String createRole(RoleCreateParam param) {
        // 检查角色编码是否已存在
        Role existRole = roleMapper.selectByRoleCode(param.getRoleCode());
        if (existRole != null) {
            throw new BusinessException("角色编码已存在: " + param.getRoleCode());
        }

        // 构建角色实体
        Role role = new Role();
        String roleId = UUID.randomUUID().toString().replace("-", "");
        role.setRoleId(roleId);
        role.setRoleName(param.getRoleName());
        role.setRoleCode(param.getRoleCode());
        role.setDescription(param.getDescription());
        role.setStatus(param.getStatus() != null ? param.getStatus() : 1);
        role.setOrderNum(param.getOrderNum() != null ? param.getOrderNum() : 0);

        // 创建角色
        roleMapper.insert(role);
        log.info("创建角色: {}", role.getRoleCode());

        // 分配菜单
        if (param.getMenuIds() != null && !param.getMenuIds().isEmpty()) {
            assignMenusInternal(roleId, param.getMenuIds());
        }

        return roleId;
    }

    @Override
    @Transactional
    public void updateRole(RoleUpdateParam param) {
        // 检查角色是否存在
        Role existRole = roleMapper.selectByRoleId(param.getRoleId());
        if (existRole == null) {
            throw new BusinessException("角色不存在: " + param.getRoleId());
        }

        // 构建更新实体
        Role role = new Role();
        role.setRoleId(param.getRoleId());
        role.setRoleName(param.getRoleName());
        role.setDescription(param.getDescription());
        role.setStatus(param.getStatus());
        role.setOrderNum(param.getOrderNum());

        // 更新角色信息
        roleMapper.updateByRoleId(role);
        log.info("更新角色: {}", role.getRoleId());

        // 更新菜单
        if (param.getMenuIds() != null) {
            roleMenuMapper.deleteByRoleId(role.getRoleId());
            if (!param.getMenuIds().isEmpty()) {
                assignMenusInternal(role.getRoleId(), param.getMenuIds());
            }
        }
    }

    @Override
    public Role getRoleByRoleId(String roleId) {
        return roleMapper.selectByRoleId(roleId);
    }

    @Override
    public RoleVO getRoleDetail(String roleId) {
        Role role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleId);
        }

        RoleVO vo = new RoleVO();
        vo.setRoleId(role.getRoleId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setOrderNum(role.getOrderNum());
        vo.setCreateTime(role.getCreateTime());
        vo.setUpdateTime(role.getUpdateTime());

        // 获取菜单ID列表
        List<String> menuIds = roleMenuMapper.selectMenuIdsByRoleId(roleId);
        vo.setMenuIds(menuIds);

        return vo;
    }

    @Override
    public PageInfo<Role> listRoles(String roleName, Integer status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Role> roles = roleMapper.selectList(roleName, status);
        return new PageInfo<>(roles);
    }

    @Override
    public List<Role> listAllRoles() {
        return roleMapper.selectList(null, null);
    }

    @Override
    @Transactional
    public void updateRoleStatus(String roleId, Integer status) {
        Role role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleId);
        }

        // 不能禁用超级管理员角色
        if ("super_admin".equals(role.getRoleCode()) && status == 0) {
            throw new BusinessException("不能禁用超级管理员角色");
        }

        roleMapper.updateStatus(roleId, status);
        log.info("更新角色状态: roleId={}, status={}", roleId, status);
    }

    @Override
    @Transactional
    public void deleteRole(String roleId) {
        Role role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleId);
        }

        // 不能删除超级管理员角色
        if ("super_admin".equals(role.getRoleCode())) {
            throw new BusinessException("不能删除超级管理员角色");
        }

        // 检查是否有用户使用此角色
        List<String> userIds = userRoleMapper.selectUserIdsByRoleId(roleId);
        if (userIds != null && !userIds.isEmpty()) {
            throw new BusinessException("该角色下存在用户，无法删除");
        }

        // 删除角色菜单关联
        roleMenuMapper.deleteByRoleId(roleId);

        // 删除角色
        roleMapper.deleteByRoleId(roleId);
        log.info("删除角色: {}", roleId);
    }

    @Override
    @Transactional
    public void assignMenus(String roleId, List<String> menuIds) {
        Role role = roleMapper.selectByRoleId(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleId);
        }

        // 清除原有菜单
        roleMenuMapper.deleteByRoleId(roleId);

        // 分配新菜单
        if (menuIds != null && !menuIds.isEmpty()) {
            assignMenusInternal(roleId, menuIds);
        }

        log.info("分配角色菜单: roleId={}, menuIds={}", roleId, menuIds);
    }

    @Override
    public List<String> getRoleMenuIds(String roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 内部方法：分配菜单
     */
    private void assignMenusInternal(String roleId, List<String> menuIds) {
        List<RoleMenu> roleMenus = new ArrayList<>();
        for (String menuId : menuIds) {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenus.add(roleMenu);
        }
        roleMenuMapper.batchInsert(roleMenus);
    }
}
