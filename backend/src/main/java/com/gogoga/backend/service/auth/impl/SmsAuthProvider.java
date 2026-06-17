package com.gogoga.backend.service.auth.impl;

import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.service.SmsLoginService;
import com.gogoga.backend.service.auth.AuthProvider;
import com.gogoga.backend.service.auth.AuthRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 短信验证码认证提供者
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsAuthProvider implements AuthProvider {

    private static final String DEFAULT_ROLE_ID = "ROLE_USER";
    private static final String YOUZAN_USER_ROLE_ID = "ROLE_YOUZAN_USER";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final SmsLoginService smsLoginService;

    @Override
    public AuthProviderEnum getProviderType() {
        return AuthProviderEnum.SMS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User authenticate(AuthRequest authRequest) {
        String phone = smsLoginService.verifyLoginCode(authRequest.getPhone(), authRequest.getSmsCode());

        User user = userMapper.selectByPhone(phone);
        if (user == null) {
            user = createPhoneUser(phone, resolveDefaultRoleId(authRequest));
            log.info("短信登录自动注册成功, userId={}, phone={}", user.getUserId(), phone);
        }

        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new AuthenticationException(AuthenticationException.USER_DISABLED, "用户账号已被禁用");
        }

        log.info("短信验证码登录成功, userId={}, phone={}", user.getUserId(), phone);
        return user;
    }

    @Override
    public boolean supports(AuthRequest authRequest) {
        return authRequest.getProviderType() == AuthProviderEnum.SMS
                && StringUtils.isNotBlank(authRequest.getPhone())
                && StringUtils.isNotBlank(authRequest.getSmsCode());
    }

    private User createPhoneUser(String phone, String defaultRoleId) {
        User user = new User();
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(phone);
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setPhone(phone);
        user.setAuthProvider(AuthProviderEnum.SMS.name());
        user.setStatus(1);
        userMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(defaultRoleId);
        userRoleMapper.insert(userRole);
        return user;
    }

    private String resolveDefaultRoleId(AuthRequest authRequest) {
        BackendPortalType requestedPortal = BackendPortalType.fromValue(authRequest == null ? null : authRequest.getPortal());
        if (requestedPortal == BackendPortalType.YOUZAN) {
            return YOUZAN_USER_ROLE_ID;
        }
        return DEFAULT_ROLE_ID;
    }
}
