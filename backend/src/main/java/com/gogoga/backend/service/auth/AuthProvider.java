package com.gogoga.backend.service.auth;

import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.AuthProviderEnum;

/**
 * 认证提供者策略接口
 * 
 * 所有认证方式（OpenID、邮箱密码、Google、GitHub）都实现此接口，
 * 以支持统一的认证流程和可扩展性。
 *
 * @author JackWei
 * @since 2026-01-01
 */
public interface AuthProvider {

    /**
     * 获取认证提供者类型
     *
     * @return 认证提供者枚举
     */
    AuthProviderEnum getProviderType();

    /**
     * 执行认证流程
     *
     * @param authRequest 认证请求参数
     * @return 认证成功后的用户实体
     * @throws AuthenticationException 认证失败时抛出
     */
    User authenticate(AuthRequest authRequest);

    /**
     * 检查当前提供者是否支持给定的请求
     *
     * @param authRequest 认证请求参数
     * @return 是否支持
     */
    boolean supports(AuthRequest authRequest);
}
