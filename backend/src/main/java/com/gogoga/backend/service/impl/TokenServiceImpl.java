package com.gogoga.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.service.TokenService;
import com.gogoga.backend.util.JsonUtil;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token 服务实现
 * 使用 Redis 存储登录用户信息
 *
 * @author system
 * @since 2025-01-21
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    /**
     * Token 在 Redis 中的 Key 前缀
     */
    private static final String TOKEN_KEY_PREFIX = "backend:token:";

    /**
     * Token 过期时间（7天，单位：秒）
     */
    private static final long TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60;

    /**
     * 刷新阈值（距离过期时间小于1天时刷新）
     */
    private static final long REFRESH_THRESHOLD = 24 * 60 * 60 * 1000;

    private final StringRedisTemplate redisTemplate;

    public TokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String createToken(LoginUser loginUser) {
        // 生成唯一令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        loginUser.setToken(token);
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(System.currentTimeMillis() + TOKEN_EXPIRE_TIME * 1000);

        // 存入 Redis
        String key = TOKEN_KEY_PREFIX + token;
        String value = JsonUtil.toJson(loginUser);
        if (value.isEmpty()) {
            log.error("序列化登录用户失败");
            throw new RuntimeException("创建 Token 失败");
        }
        redisTemplate.opsForValue().set(key, value, TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
        log.info("创建 Token 成功，用户: {}, Token: {}", loginUser.getUserId(), token);

        return token;
    }

    @Override
    public LoginUser getLoginUser(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        String key = TOKEN_KEY_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isEmpty()) {
            return null;
        }

        LoginUser loginUser = JsonUtil.toBean(value, LoginUser.class);
        if (loginUser == null) {
            log.error("反序列化登录用户失败");
            return null;
        }
        // 检查是否需要刷新
        if (loginUser.getExpireTime() - System.currentTimeMillis() < REFRESH_THRESHOLD) {
            refreshToken(token);
        }
        return loginUser;
    }

    @Override
    public void refreshToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        String key = TOKEN_KEY_PREFIX + token;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isEmpty()) {
            return;
        }

        LoginUser loginUser = JsonUtil.toBean(value, LoginUser.class);
        if (loginUser == null) {
            log.error("刷新 Token 失败：反序列化用户信息失败");
            return;
        }
        loginUser.setExpireTime(System.currentTimeMillis() + TOKEN_EXPIRE_TIME * 1000);
        String newValue = JsonUtil.toJson(loginUser);
        if (!newValue.isEmpty()) {
            redisTemplate.opsForValue().set(key, newValue, TOKEN_EXPIRE_TIME, TimeUnit.SECONDS);
            log.debug("刷新 Token 成功，用户: {}", loginUser.getUserId());
        }
    }

    @Override
    public void deleteToken(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }

        String key = TOKEN_KEY_PREFIX + token;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("删除 Token 成功: {}", token);
        }
    }

    @Override
    public boolean verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        String key = TOKEN_KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
