package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.param.UserCreateParam;
import com.gogoga.backend.param.UserUpdateParam;
import com.gogoga.backend.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author system
 * @since 2025-01-21
 */
public interface UserService {

    /**
     * 创建用户
     *
     * @param param 创建参数
     * @return 用户ID
     */
    String createUser(UserCreateParam param);

    /**
     * 更新用户
     *
     * @param param 更新参数
     */
    void updateUser(UserUpdateParam param);

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getUserByUserId(String userId);

    /**
     * 分页查询用户列表
     *
     * @param username 用户名（模糊查询）
     * @param status   状态
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageInfo<User> listUsers(String username, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 分页查询用户列表（包含角色等详细信息）
     *
     * @param username 用户名（模糊查询）
     * @param status   状态
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果（UserVO）
     */
    PageInfo<UserVO> listUsersWithDetail(String username, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 获取用户详情（包含角色信息）
     *
     * @param userId 用户ID
     * @return 用户VO
     */
    UserVO getUserDetail(String userId);

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 状态
     */
    void updateUserStatus(String userId, Integer status);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     */
    void deleteUser(String userId);

    /**
     * 分配用户角色
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(String userId, List<String> roleIds);

    /**
     * 获取用户角色ID列表
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<String> getUserRoleIds(String userId);
}
