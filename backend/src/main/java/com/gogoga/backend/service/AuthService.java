package com.gogoga.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gogoga.backend.entity.AuthSessionInfo;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.exception.AuthenticationException;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.param.LoginParam;
import com.gogoga.backend.param.RegisterParam;
import com.gogoga.backend.param.SmsLoginParam;
import com.gogoga.backend.service.auth.AuthProvider;
import com.gogoga.backend.service.auth.AuthRequest;
import com.gogoga.backend.service.auth.AuthResult;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.PhoneUtil;
import com.gogoga.backend.util.PasswordUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证服务
 * 
 * 统一处理用户注册、登录、会话管理等认证相关逻辑
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Slf4j
@Service
public class AuthService {

    // 会话相关常量
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final long DEFAULT_SESSION_EXPIRE_SECONDS = 24 * 60 * 60; // 24小时
    private static final long REMEMBER_ME_SESSION_EXPIRE_SECONDS = 30 * 24 * 60 * 60; // 30天

    /**
     * 默认角色ID（普通用户）
     */
    private static final String DEFAULT_ROLE_ID = "ROLE_USER";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final PasswordUtil passwordUtil;
    private final StringRedisTemplate redisTemplate;
    private final Map<AuthProviderEnum, AuthProvider> authProviderMap;
    private final SmsLoginService smsLoginService;
    private final BackendPortalService backendPortalService;

    public AuthService(UserMapper userMapper,
                       UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper,
                       PasswordUtil passwordUtil,
                       StringRedisTemplate redisTemplate,
                       SmsLoginService smsLoginService,
                       BackendPortalService backendPortalService,
                       List<AuthProvider> authProviders) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordUtil = passwordUtil;
        this.redisTemplate = redisTemplate;
        this.smsLoginService = smsLoginService;
        this.backendPortalService = backendPortalService;
        // 将认证提供者列表转换为 Map，便于快速查找
        this.authProviderMap = authProviders.stream()
                .collect(Collectors.toMap(AuthProvider::getProviderType, Function.identity()));
    }

    /**
     * 邮箱密码注册
     *
     * @param registerParam 注册参数
     * @return 认证结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthResult register(RegisterParam registerParam) {
        String email = registerParam.getEmail();
        String password = registerParam.getPassword();
        String nickname = registerParam.getNickname();

        // 参数校验
        if (StringUtils.isBlank(email) || StringUtils.isBlank(password)) {
            return AuthResult.failure(AuthenticationException.INVALID_CREDENTIALS, "邮箱和密码不能为空");
        }

        // 验证密码强度
        if (!passwordUtil.isPasswordStrong(password)) {
            return AuthResult.failure(AuthenticationException.PASSWORD_TOO_WEAK, 
                    "密码强度不足，需要至少8位，包含大小写字母和数字");
        }

        // 检查邮箱是否已存在
        User existingUser = userMapper.selectByEmail(email);
        if (existingUser != null) {
            return AuthResult.failure(AuthenticationException.EMAIL_ALREADY_EXISTS, "该邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUserId(UUID.randomUUID().toString().replace("-", ""));
        user.setEmail(email);
        user.setPassword(passwordUtil.encode(password));
        user.setNickname(StringUtils.isNotBlank(nickname) ? nickname : extractNicknameFromEmail(email));
        user.setUsername(email); // 邮箱作为用户名
        user.setAuthProvider(AuthProviderEnum.PASSWORD.name());
        user.setStatus(1);

        userMapper.insert(user);

        // 为新用户分配默认角色（普通用户）
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRoleMapper.insert(userRole);

        log.info("用户注册成功, userId={}, email={}, 已分配默认角色: {}", user.getUserId(), email, DEFAULT_ROLE_ID);

        // 创建会话
        String sessionToken = createSession(user.getUserId(), BackendPortalType.ADMIN, false);
        return AuthResult.success(user, sessionToken, DEFAULT_SESSION_EXPIRE_SECONDS, 
                AuthProviderEnum.PASSWORD, true);
    }

    /**
     * 邮箱密码登录
     *
     * @param loginParam 登录参数
     * @return 认证结果
     */
    public AuthResult loginWithPassword(LoginParam loginParam) {
        AuthRequest authRequest = AuthRequest.builder()
                .providerType(AuthProviderEnum.PASSWORD)
                .email(loginParam.getEmail())
                .password(loginParam.getPassword())
                .rememberMe(loginParam.getRememberMe())
                .portal(StringUtils.defaultIfBlank(loginParam.getPortal(), BackendPortalType.ADMIN.name()))
                .build();

        return authenticate(authRequest);
    }

    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     */
    public void sendSmsCode(String phone, String clientIp) {
        smsLoginService.sendLoginCode(phone, clientIp);
    }

    /**
     * 短信验证码登录
     *
     * @param smsLoginParam 登录参数
     * @return 认证结果
     */
    public AuthResult loginWithSms(SmsLoginParam smsLoginParam) {
        String normalizedPhone = PhoneUtil.normalizeCnPhone(smsLoginParam.getPhone());
        boolean isNewUser = normalizedPhone == null || userMapper.selectByPhone(normalizedPhone) == null;

        AuthRequest authRequest = AuthRequest.builder()
                .providerType(AuthProviderEnum.SMS)
                .phone(smsLoginParam.getPhone())
                .smsCode(smsLoginParam.getCode())
                .rememberMe(smsLoginParam.getRememberMe())
                .portal(smsLoginParam.getPortal())
                .build();

        AuthResult result = authenticate(authRequest);
        if (result.isSuccess()) {
            result.setNewUser(isNewUser);
        }
        return result;
    }

    /**
     * OAuth 登录（Google/GitHub）
     *
     * @param provider    认证提供者
     * @param code        授权码
     * @param redirectUri 重定向 URI
     * @param rememberMe  是否记住登录
     * @return 认证结果
     */
    public AuthResult loginWithOAuth(AuthProviderEnum provider, String code, 
                                      String redirectUri, boolean rememberMe) {
        AuthRequest authRequest = AuthRequest.builder()
                .providerType(provider)
                .code(code)
                .redirectUri(redirectUri)
                .rememberMe(rememberMe)
                .portal(BackendPortalType.ADMIN.name())
                .build();

        return authenticate(authRequest);
    }

    /**
     * 统一认证入口
     *
     * @param authRequest 认证请求
     * @return 认证结果
     */
    public AuthResult authenticate(AuthRequest authRequest) {
        AuthProviderEnum providerType = authRequest.getProviderType();
        AuthProvider authProvider = authProviderMap.get(providerType);

        if (authProvider == null) {
            log.error("不支持的认证提供者: {}", providerType);
            return AuthResult.failure("UNSUPPORTED_PROVIDER", "不支持的登录方式");
        }

        try {
            User user = authProvider.authenticate(authRequest);
            
            // 判断是否使用记住我功能
            boolean rememberMe = authRequest.getRememberMe() != null && authRequest.getRememberMe();
            long expireSeconds = rememberMe ? REMEMBER_ME_SESSION_EXPIRE_SECONDS : DEFAULT_SESSION_EXPIRE_SECONDS;
            
            // 创建会话
            String sessionToken = createSession(
                    user.getUserId(),
                    resolveInitialPortal(user, authRequest),
                    rememberMe
            );
            
            return AuthResult.success(user, sessionToken, expireSeconds, providerType, false);
        } catch (AuthenticationException e) {
            log.warn("认证失败: {}, provider={}", e.getMessage(), providerType);
            return AuthResult.failure(e.getErrorCode(), e.getMessage());
        } catch (BusinessException e) {
            log.warn("认证业务校验失败: {}, provider={}", e.getMessage(), providerType);
            return AuthResult.failure("AUTH_BUSINESS_ERROR", resolveAuthBusinessMessage(e, authRequest));
        } catch (Exception e) {
            log.error("认证过程发生异常, provider={}", providerType, e);
            return AuthResult.failure("AUTH_ERROR", "认证过程发生错误，请稍后重试");
        }
    }

    /**
     * 验证会话
     *
     * @param sessionToken 会话 Token
     * @return 用户 ID，如果会话无效则返回 null
     */
    public String validateSession(String sessionToken) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        return sessionInfo == null ? null : sessionInfo.getUserId();
    }

    /**
     * 获取会话信息
     *
     * @param sessionToken 会话 Token
     * @return 会话信息，如果会话无效则返回 null
     */
    public AuthSessionInfo getSessionInfo(String sessionToken) {
        if (StringUtils.isBlank(sessionToken)) {
            return null;
        }
        String sessionKey = SESSION_KEY_PREFIX + sessionToken;
        String sessionPayload = redisTemplate.opsForValue().get(sessionKey);
        if (StringUtils.isBlank(sessionPayload)) {
            return null;
        }
        if (sessionPayload.startsWith("{")) {
            AuthSessionInfo sessionInfo = JsonUtil.toBean(sessionPayload, AuthSessionInfo.class);
            if (sessionInfo != null && StringUtils.isNotBlank(sessionInfo.getUserId())) {
                return sessionInfo;
            }
        }
        AuthSessionInfo legacySessionInfo = new AuthSessionInfo();
        legacySessionInfo.setUserId(sessionPayload);
        return legacySessionInfo;
    }

    /**
     * 获取会话对应的用户
     *
     * @param sessionToken 会话 Token
     * @return 用户实体，如果会话无效则返回 null
     */
    public User getUserBySession(String sessionToken) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        if (sessionInfo == null || StringUtils.isBlank(sessionInfo.getUserId())) {
            return null;
        }
        return userMapper.selectByUserId(sessionInfo.getUserId());
    }

    public User getEffectiveUserBySession(String sessionToken) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        if (sessionInfo == null) {
            return null;
        }
        String effectiveUserId = StringUtils.defaultIfBlank(
                sessionInfo.getImpersonatedUserId(),
                sessionInfo.getUserId()
        );
        if (StringUtils.isBlank(effectiveUserId)) {
            return null;
        }
        return userMapper.selectByUserId(effectiveUserId);
    }

    /**
     * 切换后台工作台
     *
     * @param sessionToken 会话 Token
     * @param targetPortal 目标工作台
     */
    public void switchPortal(String sessionToken, BackendPortalType targetPortal) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        if (sessionInfo == null || StringUtils.isBlank(sessionInfo.getUserId())) {
            throw new BusinessException(401, "登录态已失效，请重新登录");
        }
        if (StringUtils.isNotBlank(sessionInfo.getImpersonatedUserId())) {
            throw new BusinessException(400, "请先退出模拟登录后再切换工作台");
        }
        User user = userMapper.selectByUserId(sessionInfo.getUserId());
        if (user == null) {
            throw new BusinessException(401, "当前用户不存在，请重新登录");
        }
        Set<String> roleCodes = getUserRoleCodes(user.getUserId());
        backendPortalService.validatePortalOwnership(user, roleCodes, targetPortal);
        updateSessionPortal(sessionToken, sessionInfo.getUserId(), targetPortal);
    }

    public void startImpersonation(String sessionToken, String targetUserId, BackendPortalType targetPortal) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        if (sessionInfo == null || StringUtils.isBlank(sessionInfo.getUserId())) {
            throw new BusinessException(401, "登录态已失效，请重新登录");
        }
        String operatorUserId = sessionInfo.getUserId();
        if (!getUserRoleCodes(operatorUserId).contains(BackendPortalService.ROLE_CODE_SUPER_ADMIN)) {
            throw new BusinessException(403, "只有超级管理员可以模拟登录");
        }
        String normalizedTargetUserId = StringUtils.trimToNull(targetUserId);
        if (normalizedTargetUserId == null) {
            throw new BusinessException(400, "目标用户不能为空");
        }
        BackendPortalType resolvedTargetPortal = targetPortal == null ? BackendPortalType.SALES : targetPortal;
        User targetUser = userMapper.selectByUserId(normalizedTargetUserId);
        if (targetUser == null) {
            throw new BusinessException(404, "目标用户不存在");
        }
        Set<String> targetRoleCodes = getUserRoleCodes(normalizedTargetUserId);
        backendPortalService.validatePortalOwnership(targetUser, targetRoleCodes, resolvedTargetPortal);

        sessionInfo.setImpersonatedUserId(normalizedTargetUserId);
        sessionInfo.setImpersonatedPortal(resolvedTargetPortal);
        sessionInfo.setActivePortal(resolvedTargetPortal);
        persistSession(sessionToken, sessionInfo);
        log.info("超级管理员模拟登录, operatorUserId={}, targetUserId={}, targetPortal={}",
                operatorUserId, normalizedTargetUserId, resolvedTargetPortal);
    }

    public void stopImpersonation(String sessionToken) {
        AuthSessionInfo sessionInfo = getSessionInfo(sessionToken);
        if (sessionInfo == null || StringUtils.isBlank(sessionInfo.getUserId())) {
            throw new BusinessException(401, "登录态已失效，请重新登录");
        }
        if (StringUtils.isBlank(sessionInfo.getImpersonatedUserId())) {
            return;
        }
        String targetUserId = sessionInfo.getImpersonatedUserId();
        sessionInfo.setImpersonatedUserId(null);
        sessionInfo.setImpersonatedPortal(null);
        sessionInfo.setActivePortal(BackendPortalType.ADMIN);
        persistSession(sessionToken, sessionInfo);
        log.info("超级管理员退出模拟登录, operatorUserId={}, targetUserId={}",
                sessionInfo.getUserId(), targetUserId);
    }

    /**
     * 登出（销毁会话）
     *
     * @param sessionToken 会话 Token
     */
    public void logout(String sessionToken) {
        if (StringUtils.isNotBlank(sessionToken)) {
            String sessionKey = SESSION_KEY_PREFIX + sessionToken;
            redisTemplate.delete(sessionKey);
            log.info("用户登出, sessionToken={}", sessionToken);
        }
    }

    /**
     * 创建会话
     *
     * @param userId     用户 ID
     * @param rememberMe 是否记住登录（30天）
     * @return 会话 Token
     */
    private String createSession(String userId, BackendPortalType activePortal, boolean rememberMe) {
        String sessionToken = UUID.randomUUID().toString().replace("-", "");
        String sessionKey = SESSION_KEY_PREFIX + sessionToken;
        long expireSeconds = rememberMe ? REMEMBER_ME_SESSION_EXPIRE_SECONDS : DEFAULT_SESSION_EXPIRE_SECONDS;

        AuthSessionInfo sessionInfo = new AuthSessionInfo();
        sessionInfo.setUserId(userId);
        sessionInfo.setActivePortal(activePortal);
        redisTemplate.opsForValue().set(sessionKey, JsonUtil.toJson(sessionInfo), expireSeconds, TimeUnit.SECONDS);
        log.debug("创建会话, userId={}, rememberMe={}, expireSeconds={}", userId, rememberMe, expireSeconds);
        
        return sessionToken;
    }

    /**
     * 从邮箱提取昵称（邮箱@前面的部分）
     */
    private String extractNicknameFromEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return "用户";
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private BackendPortalType resolveInitialPortal(User user, AuthRequest authRequest) {
        if (authRequest.getProviderType() == AuthProviderEnum.SMS) {
            return backendPortalService.resolvePhonePortal(
                    getUserRoleCodes(user.getUserId()),
                    BackendPortalType.fromValue(authRequest.getPortal())
            );
        }
        return BackendPortalType.ADMIN;
    }

    private Set<String> getUserRoleCodes(String userId) {
        return roleMapper.selectByUserId(userId).stream()
                .map(role -> role.getRoleCode())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveAuthBusinessMessage(BusinessException e, AuthRequest authRequest) {
        if (e == null) {
            return "认证失败，请稍后重试";
        }
        if (authRequest != null && authRequest.getProviderType() == AuthProviderEnum.SMS) {
            String originalMessage = StringUtils.trimToEmpty(e.getMessage());
            if (StringUtils.equalsAny(
                    originalMessage,
                    "当前账号无权进入所选工作台",
                    "当前账号未开通普通商户或网销工作台",
                    "当前账号未开通普通商户、网销或有赞工作台"
            )) {
                BackendPortalType requestedPortal = BackendPortalType.fromValue(authRequest.getPortal());
                if (requestedPortal == BackendPortalType.SALES) {
                    return "当前账号未开通网销工作台，请联系超管分配“网销用户”角色";
                }
                if (requestedPortal == BackendPortalType.YOUZAN) {
                    return "当前账号未开通有赞工作台，请联系超管分配“有赞用户”角色";
                }
                return "当前账号未开通普通商户工作台，请联系超管分配“普通用户”角色";
            }
        }
        return StringUtils.defaultIfBlank(e.getMessage(), "认证失败，请稍后重试");
    }

    private void updateSessionPortal(String sessionToken, String userId, BackendPortalType activePortal) {
        AuthSessionInfo sessionInfo = new AuthSessionInfo();
        sessionInfo.setUserId(userId);
        sessionInfo.setActivePortal(activePortal);
        persistSession(sessionToken, sessionInfo);
    }

    private void persistSession(String sessionToken, AuthSessionInfo sessionInfo) {
        String sessionKey = SESSION_KEY_PREFIX + sessionToken;
        Long expireSeconds = redisTemplate.getExpire(sessionKey, TimeUnit.SECONDS);
        if (expireSeconds == null || expireSeconds <= 0) {
            expireSeconds = DEFAULT_SESSION_EXPIRE_SECONDS;
        }
        redisTemplate.opsForValue().set(sessionKey, JsonUtil.toJson(sessionInfo), expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取 OAuth 授权 URL
     *
     * @param provider 认证提供者
     * @param state    状态参数（用于 CSRF 防护）
     * @return 授权 URL
     */
    public String getOAuthAuthorizationUrl(AuthProviderEnum provider, String state) {
        // 这个方法可以根据需要实现，返回 OAuth 提供者的授权 URL
        // 目前前端可以直接构建 URL
        return null;
    }
}
