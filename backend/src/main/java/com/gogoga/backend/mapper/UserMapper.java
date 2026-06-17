package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.User;

import java.util.List;

/**
 * 用户 Mapper 接口
 *
 * @author system
 * @since 2025-01-21
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int insert(User user);

    /**
     * 根据用户ID更新用户信息
     *
     * @param user 用户实体
     * @return 影响行数
     */
    int updateByUserId(User user);

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户实体
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户实体
     */
    User selectByPhone(@Param("phone") String phone);

    /**
     * 根据主键ID查询用户
     *
     * @param id 主键ID
     * @return 用户实体
     */
    User selectById(@Param("id") Long id);

    List<User> selectByUserIds(@Param("userIds") List<String> userIds);

    List<User> selectByUserIdsAndKeyword(@Param("userIds") List<String> userIds, @Param("keyword") String keyword);

    List<User> selectByUserIdsAndRoleCodeAndKeyword(
            @Param("userIds") List<String> userIds,
            @Param("roleCode") String roleCode,
            @Param("keyword") String keyword
    );

    List<String> selectAllBackendUserIds();

    List<User> selectBackendUsersByKeyword(@Param("keyword") String keyword);

    List<User> selectBackendUsersByRoleCodeAndKeyword(
            @Param("roleCode") String roleCode,
            @Param("keyword") String keyword
    );

    /**
     * 查询用户列表
     *
     * @param keyword 搜索关键词（用户ID或姓名）
     * @param status  状态
     * @return 用户列表
     */
    List<User> selectList(@Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * 更新用户状态
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("userId") String userId, @Param("status") Integer status);

    /**
     * 删除用户
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") String userId);
}
