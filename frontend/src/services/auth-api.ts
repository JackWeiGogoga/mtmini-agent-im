/**
 * 认证相关 API 服务
 */
import request from "@/utils/request";
import type {
  CurrentUser,
  PasswordLoginParam,
  RegisterParam,
  SmsLoginParam,
  SmsSendCodeParam,
  AuthResult,
  OAuthConfig,
  OAuthCallbackParam,
  AuthProvider,
  BackendPortal,
} from "@/types";

/**
 * 邮箱密码注册
 * @param params 注册参数
 * @returns 认证结果
 */
export async function register(params: RegisterParam): Promise<AuthResult> {
  const response = await request.post<unknown, { data: AuthResult }>(
    "/auth/register",
    params,
  );
  return response.data;
}

/**
 * 邮箱密码登录
 * @param params 登录参数
 * @returns 认证结果
 */
export async function loginWithPassword(
  params: PasswordLoginParam,
): Promise<AuthResult> {
  const response = await request.post<unknown, { data: AuthResult }>(
    "/auth/login/password",
    params,
  );
  return response.data;
}

/**
 * 发送短信验证码
 * @param params 手机号参数
 */
export async function sendSmsCode(params: SmsSendCodeParam): Promise<void> {
  await request.post("/auth/sms/send-code", params);
}

/**
 * 手机验证码登录
 * @param params 登录参数
 * @returns 认证结果
 */
export async function loginWithSms(
  params: SmsLoginParam,
): Promise<AuthResult> {
  const response = await request.post<unknown, { data: AuthResult }>(
    "/auth/login/sms",
    params,
  );
  return response.data;
}

/**
 * 获取 OAuth 配置
 * @returns OAuth 配置信息
 */
export async function getOAuthConfig(): Promise<OAuthConfig> {
  const response = await request.get<unknown, { data: OAuthConfig }>(
    "/auth/oauth/config",
  );
  return response.data;
}

/**
 * Google OAuth 回调
 * @param params 回调参数
 * @returns 认证结果
 */
export async function googleOAuthCallback(
  params: OAuthCallbackParam,
): Promise<AuthResult> {
  const response = await request.post<unknown, { data: AuthResult }>(
    "/auth/oauth/google/callback",
    null,
    { params },
  );
  return response.data;
}

/**
 * GitHub OAuth 回调
 * @param params 回调参数
 * @returns 认证结果
 */
export async function githubOAuthCallback(
  params: OAuthCallbackParam,
): Promise<AuthResult> {
  const response = await request.post<unknown, { data: AuthResult }>(
    "/auth/oauth/github/callback",
    null,
    { params },
  );
  return response.data;
}

/**
 * 处理 OAuth 回调（统一入口）
 * @param provider 认证提供者
 * @param params 回调参数
 * @returns 认证结果
 */
export async function handleOAuthCallback(
  provider: AuthProvider,
  params: OAuthCallbackParam,
): Promise<AuthResult> {
  if (provider === "GOOGLE") {
    return googleOAuthCallback(params);
  } else if (provider === "GITHUB") {
    return githubOAuthCallback(params);
  }
  throw new Error(`Unsupported OAuth provider: ${provider}`);
}

/**
 * 构建 OAuth 授权 URL
 * @param config OAuth 配置
 * @param provider 认证提供者
 * @param state 状态参数（用于 CSRF 防护）
 * @returns 授权 URL
 */
export function buildOAuthAuthorizationUrl(
  config: OAuthConfig,
  provider: "google" | "github",
  state?: string,
): string {
  const providerConfig = config[provider];
  const params = new URLSearchParams({
    client_id: providerConfig.clientId,
    redirect_uri: providerConfig.redirectUri,
    scope: providerConfig.scope,
    response_type: "code",
  });

  if (state) {
    params.append("state", state);
  }

  // Google 需要额外参数
  if (provider === "google") {
    params.append("access_type", "offline");
    params.append("prompt", "consent");
  }

  return `${providerConfig.authorizationUri}?${params.toString()}`;
}

/**
 * 登出
 */
export async function logout(): Promise<void> {
  await request.post("/auth/logout");
}

/**
 * 获取当前用户信息
 * @returns 当前用户信息（包含权限和菜单）
 */
export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await request.get<unknown, { data: CurrentUser }>(
    "/auth/current-user",
  );
  return response.data;
}

/**
 * 切换后台工作台
 */
export async function switchPortal(portal: BackendPortal): Promise<CurrentUser> {
  const response = await request.post<unknown, { data: CurrentUser }>(
    "/auth/switch-portal",
    { portal },
  );
  return response.data;
}

export async function startImpersonation(
  targetUserId: string,
  portal: BackendPortal = "SALES",
): Promise<void> {
  await request.post("/auth/impersonation", { targetUserId, portal });
}

export async function stopImpersonation(): Promise<void> {
  await request.post("/auth/impersonation/stop");
}

/**
 * 刷新 Token（可选实现）
 */
export async function refreshToken(): Promise<void> {
  await request.post("/auth/refresh");
}
