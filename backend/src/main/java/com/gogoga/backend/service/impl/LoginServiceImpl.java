package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.mapper.MenuMapper;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.service.BackendPortalService;
import com.gogoga.backend.service.LoginService;
import com.gogoga.backend.service.TokenService;
import com.gogoga.backend.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 登录服务实现
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private BackendPortalService backendPortalService;

    /**
     * 默认角色ID（普通用户）
     */
    private static final String DEFAULT_ROLE_ID = "ROLE_USER";

    @Override
    @Transactional
    public LoginUser login(String userId, String userName, String email) {
        log.info("用户登录: userId={}, userName={}", userId, userName);

        // 查询或创建用户
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            // 首次登录，创建用户
            user = new User();
            user.setUserId(userId);
            user.setUsername(userId);
            user.setNickname(userName);
            user.setEmail(email);
            user.setAuthProvider("OPENID");
            user.setStatus(1);
            userMapper.insert(user);
            log.info("创建新用户: {}", userId);

            // 为新用户分配默认角色（普通用户）
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(DEFAULT_ROLE_ID);
            userRoleMapper.insert(userRole);
            log.info("为新用户分配默认角色: userId={}, roleId={}", userId, DEFAULT_ROLE_ID);
        } else {
            // 更新用户信息
            user.setUsername(userName);
            user.setEmail(email);
            userMapper.updateByUserId(user);
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new RuntimeException("用户已被禁用");
        }

        // 查询用户角色
        List<Role> roles = roleMapper.selectByUserId(userId);
        Set<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toSet());

        // 查询用户权限
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        Set<String> permissions = new HashSet<>(perms);

        // 构建登录用户对象
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUserName(userName);
        loginUser.setEmail(email);
        loginUser.setAvatar(user.getAvatar());
        loginUser.setRoles(roleCodes);
        loginUser.setPermissions(permissions);

        // 创建 Token
        String token = tokenService.createToken(loginUser);
        loginUser.setToken(token);

        log.info("用户登录成功: {}, 角色: {}", userId, roleCodes);
        return loginUser;
    }

    @Override
    @Transactional
    public LoginUser loginMtShop(MtShop shop) {
        if (StringUtils.isBlank(shop.getEpoiId())) {
            throw new IllegalStateException("门店缺少epoiId，无法创建H5登录态");
        }

        String userId = "mtshop_" + shop.getId();
        String username = firstNonBlank(shop.getPoiId(), shop.getEpoiId(), userId);
        String displayName = firstNonBlank(shop.getPoiName(), shop.getName(), username);

        log.info("美团门店登录: shopId={}, epoiId={}, poiId={}", shop.getId(), shop.getEpoiId(), shop.getPoiId());

        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            user = new User();
            user.setUserId(userId);
            user.setUsername(username);
            user.setNickname(displayName);
            user.setAuthProvider(AuthProviderEnum.MT_H5.name());
            user.setStatus(1);
            user = createMtShopUserOrLoadExisting(user);
        } else {
            user.setUsername(username);
            user.setNickname(displayName);
            user.setAuthProvider(AuthProviderEnum.MT_H5.name());
            if (user.getStatus() == null) {
                user.setStatus(1);
            }
            userMapper.updateByUserId(user);
        }

        ensureDefaultRole(userId);

        if (user.getStatus() != 1) {
            throw new RuntimeException("店铺登录账号已被禁用");
        }

        List<Role> roles = roleMapper.selectByUserId(userId);
        Set<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toSet());

        List<String> perms = menuMapper.selectPermsByUserId(userId);
        Set<String> permissions = new HashSet<>(perms);

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUserName(displayName);
        loginUser.setEmail(null);
        loginUser.setAvatar(shop.getLogo());
        loginUser.setEpoiId(shop.getEpoiId());
        loginUser.setRoles(roleCodes);
        loginUser.setPermissions(permissions);

        String token = tokenService.createToken(loginUser);
        loginUser.setToken(token);
        return loginUser;
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            tokenService.deleteToken(token);
            log.info("用户登出成功");
        }
    }

    @Override
    public LoginUser getCurrentUser() {
        return UserContext.getLoginUser();
    }

    @Override
    public List<Menu> getCurrentUserMenus() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            return new ArrayList<>();
        }

        List<Menu> menus;
        if (loginUser.isSuperAdmin()) {
            // 超级管理员获取所有菜单
            menus = menuMapper.selectAllEnabled();
        } else {
            // 普通用户获取有权限的菜单
            menus = menuMapper.selectByUserId(loginUser.getUserId());
        }
        menus = backendPortalService.filterMenus(menus, loginUser.getActivePortal());

        // 过滤只保留目录和菜单（M=目录，C=菜单），不包括按钮权限（F）
        List<Menu> menuList = menus.stream()
                .filter(m -> m.getMenuType() != null && !"F".equals(m.getMenuType()))
                .collect(Collectors.toList());

        // 构建菜单树
        return buildMenuTree(menuList);
    }

    @Override
    public List<String> getCurrentUserPermissions() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(loginUser.getPermissions());
    }

    @Override
    public Set<String> getUserRoles(String userId) {
        List<Role> roles = roleMapper.selectByUserId(userId);
        return roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getUserPermissions(String userId) {
        List<String> perms = menuMapper.selectPermsByUserId(userId);
        return new HashSet<>(perms);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private User createMtShopUserOrLoadExisting(User user) {
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            log.warn("美团门店登录用户已被并发创建: userId={}", user.getUserId());
            User existing = userMapper.selectByUserId(user.getUserId());
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    private void ensureDefaultRole(String userId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insertIgnoreDuplicate(userRole);
    }
}
