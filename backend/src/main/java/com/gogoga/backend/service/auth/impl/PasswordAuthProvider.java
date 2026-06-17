package com.gogoga.backend.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.service.auth.AuthProvider;
import com.gogoga.backend.service.auth.AuthRequest;
import com.gogoga.backend.util.PasswordUtil;

/**
 * 邮箱密码认证提供者
 * 
 * 处理邮箱+密码的登录逻辑
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordAuthProvider implements AuthProvider {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;

    @Override
    public AuthProviderEnum getProviderType() {
        return AuthProviderEnum.PASSWORD;
    }

    @Override
    public User authenticate(AuthRequest authRequest) {
        String email = authRequest.getEmail();
        String password = authRequest.getPassword();

        // 参数校验
        if (StringUtils.isBlank(email) || StringUtils.isBlank(password)) {
            throw new AuthenticationException(
                    AuthenticationException.INVALID_CREDENTIALS, 
                    "邮箱和密码不能为空");
        }

        // 查询用户
        User user = userMapper.selectByEmail(email);
        if (user == null) {
            log.warn("邮箱密码登录失败：用户不存在, email={}", email);
            throw new AuthenticationException(
                    AuthenticationException.INVALID_CREDENTIALS, 
                    "邮箱或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            log.warn("邮箱密码登录失败：用户已禁用, userId={}", user.getUserId());
            throw new AuthenticationException(
                    AuthenticationException.USER_DISABLED, 
                    "用户账号已被禁用");
        }

        // 检查用户是否有密码（可能是第三方登录用户）
        if (StringUtils.isBlank(user.getPassword())) {
            log.warn("邮箱密码登录失败：用户未设置密码, userId={}", user.getUserId());
            throw new AuthenticationException(
                    AuthenticationException.INVALID_CREDENTIALS, 
                    "该账号未设置密码，请使用其他方式登录");
        }

        // 验证密码
        if (!passwordUtil.matches(password, user.getPassword())) {
            log.warn("邮箱密码登录失败：密码错误, userId={}", user.getUserId());
            throw new AuthenticationException(
                    AuthenticationException.INVALID_CREDENTIALS, 
                    "邮箱或密码错误");
        }

        log.info("邮箱密码登录成功, userId={}, email={}", user.getUserId(), email);
        return user;
    }

    @Override
    public boolean supports(AuthRequest authRequest) {
        return authRequest.getProviderType() == AuthProviderEnum.PASSWORD
                && StringUtils.isNotBlank(authRequest.getEmail())
                && StringUtils.isNotBlank(authRequest.getPassword());
    }
}
