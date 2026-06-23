package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.param.UserCreateParam;
import com.gogoga.backend.param.UserUpdateParam;
import com.gogoga.backend.service.UserService;
import com.gogoga.backend.util.PhoneUtil;
import com.gogoga.backend.vo.UserVO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final UserRoleMapper userRoleMapper;

    private final RoleMapper roleMapper;

    public UserServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional
    public String createUser(UserCreateParam param) {
        String normalizedPhone = normalizePhone(param.getPhone());

        // 检查用户名是否已存在
        User existUser = userMapper.selectByUsername(param.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户名已存在: " + param.getUsername());
        }

        if (normalizedPhone != null && userMapper.selectByPhone(normalizedPhone) != null) {
            throw new BusinessException("手机号已存在: " + normalizedPhone);
        }

        // 构建用户实体
        User user = new User();
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(param.getUsername());
        user.setNickname(param.getNickname());
        user.setEmail(param.getEmail());
        user.setPhone(normalizedPhone);
        user.setAvatar(param.getAvatar());
        user.setStatus(param.getStatus() != null ? param.getStatus() : 1);

        // 创建用户
        userMapper.insert(user);
        log.info("创建用户: {}", user.getUserId());

        // 分配角色
        if (param.getRoleIds() != null && !param.getRoleIds().isEmpty()) {
            assignRolesInternal(user.getUserId(), param.getRoleIds());
        }

        return user.getUserId();
    }

    @Override
    @Transactional
    public void updateUser(UserUpdateParam param) {
        String normalizedPhone = normalizePhone(param.getPhone());

        // 检查用户是否存在
        User existUser = userMapper.selectByUserId(param.getUserId());
        if (existUser == null) {
            throw new BusinessException("用户不存在: " + param.getUserId());
        }

        if (normalizedPhone != null) {
            User phoneUser = userMapper.selectByPhone(normalizedPhone);
            if (phoneUser != null && !phoneUser.getUserId().equals(param.getUserId())) {
                throw new BusinessException("手机号已存在: " + normalizedPhone);
            }
        }

        // 构建更新实体
        User user = new User();
        user.setUserId(param.getUserId());
        user.setNickname(param.getNickname());
        user.setEmail(param.getEmail());
        user.setPhone(normalizedPhone);
        user.setAvatar(param.getAvatar());
        user.setStatus(param.getStatus());

        // 更新用户信息
        userMapper.updateByUserId(user);
        log.info("更新用户: {}", user.getUserId());

        // 更新角色
        if (param.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(user.getUserId());
            if (!param.getRoleIds().isEmpty()) {
                assignRolesInternal(user.getUserId(), param.getRoleIds());
            }
        }
    }

    @Override
    public User getUserByUserId(String userId) {
        return userMapper.selectByUserId(userId);
    }

    @Override
    public PageInfo<User> listUsers(String username, Integer status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<User> users = userMapper.selectList(username, status);
        return new PageInfo<>(users);
    }

    @Override
    public PageInfo<UserVO> listUsersWithDetail(String username, Integer status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<User> users = userMapper.selectList(username, status);
        PageInfo<User> userPageInfo = new PageInfo<>(users);
        
        // 转换为 UserVO 并填充角色信息
        List<UserVO> userVOList = users.stream()
                .map(this::convertToUserVO)
                .collect(Collectors.toList());
        
        // 构建 PageInfo<UserVO>
        PageInfo<UserVO> pageInfo = new PageInfo<>();
        pageInfo.setList(userVOList);
        pageInfo.setTotal(userPageInfo.getTotal());
        pageInfo.setPageNum(userPageInfo.getPageNum());
        pageInfo.setPageSize(userPageInfo.getPageSize());
        pageInfo.setPages(userPageInfo.getPages());
        pageInfo.setIsFirstPage(userPageInfo.isIsFirstPage());
        pageInfo.setIsLastPage(userPageInfo.isIsLastPage());
        pageInfo.setHasPreviousPage(userPageInfo.isHasPreviousPage());
        pageInfo.setHasNextPage(userPageInfo.isHasNextPage());
        
        return pageInfo;
    }

    /**
     * 将 User 转换为 UserVO（包含角色信息）
     */
    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setAuthProvider(user.getAuthProvider());

        // 获取角色信息
        List<String> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getUserId());
        vo.setRoleIds(roleIds);

        if (roleIds != null && !roleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectByRoleIds(roleIds);
            List<String> roleNames = roles.stream()
                    .map(Role::getRoleName)
                    .collect(Collectors.toList());
            vo.setRoleNames(roleNames);
        } else {
            vo.setRoleNames(new ArrayList<>());
        }

        return vo;
    }

    @Override
    public UserVO getUserDetail(String userId) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());

        // 获取角色信息
        List<String> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        vo.setRoleIds(roleIds);

        if (!roleIds.isEmpty()) {
            List<Role> roles = roleMapper.selectByRoleIds(roleIds);
            List<String> roleNames = roles.stream()
                    .map(Role::getRoleName)
                    .collect(Collectors.toList());
            vo.setRoleNames(roleNames);
        }

        return vo;
    }

    @Override
    @Transactional
    public void updateUserStatus(String userId, Integer status) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        userMapper.updateStatus(userId, status);
        log.info("更新用户状态: userId={}, status={}", userId, status);
    }

    @Override
    @Transactional
    public void deleteUser(String userId) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        // 删除用户角色关联
        userRoleMapper.deleteByUserId(userId);

        // 删除用户
        userMapper.deleteByUserId(userId);
        log.info("删除用户: {}", userId);
    }

    @Override
    @Transactional
    public void assignRoles(String userId, List<String> roleIds) {
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        // 清除原有角色
        userRoleMapper.deleteByUserId(userId);

        // 分配新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            assignRolesInternal(userId, roleIds);
        }

        log.info("分配用户角色: userId={}, roleIds={}", userId, roleIds);
    }

    @Override
    public List<String> getUserRoleIds(String userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    /**
     * 内部方法：分配角色
     */
    private void assignRolesInternal(String userId, List<String> roleIds) {
        List<UserRole> userRoles = new ArrayList<>();
        for (String roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoles.add(userRole);
        }
        userRoleMapper.batchInsert(userRoles);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        if (!PhoneUtil.isValidCnPhone(phone)) {
            throw new BusinessException("手机号格式不正确: " + phone);
        }
        return PhoneUtil.normalizeCnPhone(phone);
    }
}
