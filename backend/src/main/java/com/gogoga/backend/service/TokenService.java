package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;

/**
 * Token 服务接口
 *
 * @author system
 * @since 2025-01-21
 */
public interface TokenService {

    /**
     * 创建登录令牌
     *
     * @param loginUser 登录用户
     * @return 令牌字符串
     */
    String createToken(LoginUser loginUser);

    /**
     * 根据令牌获取登录用户
     *
     * @param token 令牌
     * @return 登录用户，不存在或已过期返回 null
     */
    LoginUser getLoginUser(String token);

    /**
     * 刷新令牌有效期
     *
     * @param token 令牌
     */
    void refreshToken(String token);

    /**
     * 删除令牌（登出）
     *
     * @param token 令牌
     */
    void deleteToken(String token);

    /**
     * 验证令牌是否有效
     *
     * @param token 令牌
     * @return true 有效
     */
    boolean verifyToken(String token);
}
