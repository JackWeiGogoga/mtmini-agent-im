package com.gogoga.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.gogoga.backend.entity.UserAuth;

import java.util.List;

/**
 * 用户认证绑定 Mapper 接口
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Mapper
public interface UserAuthMapper {

    /**
     * 插入用户认证绑定记录
     *
     * @param userAuth 用户认证绑定实体
     * @return 影响行数
     */
    int insert(UserAuth userAuth);

    /**
     * 根据提供者和提供者用户ID查询绑定记录
     *
     * @param provider       认证提供者
     * @param providerUserId 提供者侧用户唯一标识
     * @return 用户认证绑定实体
     */
    UserAuth selectByProviderAndProviderUserId(@Param("provider") String provider,
                                                @Param("providerUserId") String providerUserId);

    /**
     * 根据用户ID查询所有绑定记录
     *
     * @param userId 用户ID
     * @return 用户认证绑定列表
     */
    List<UserAuth> selectByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和提供者查询绑定记录
     *
     * @param userId   用户ID
     * @param provider 认证提供者
     * @return 用户认证绑定实体
     */
    UserAuth selectByUserIdAndProvider(@Param("userId") String userId, @Param("provider") String provider);

    /**
     * 删除用户认证绑定记录
     *
     * @param userId   用户ID
     * @param provider 认证提供者
     * @return 影响行数
     */
    int deleteByUserIdAndProvider(@Param("userId") String userId, @Param("provider") String provider);

    /**
     * 根据用户ID删除所有绑定记录
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") String userId);
}
