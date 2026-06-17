/**
 * OAuth 回调处理页面
 *
 * 处理第三方 OAuth 登录后的回调，将授权码发送到后端换取用户信息
 */
import React, { useEffect, useState, useRef } from "react";
import { Card, Typography, Spin, Alert, Button } from "antd";
import {
  LoadingOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from "@ant-design/icons";
import { createStyles } from "antd-style";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import useAuthPageStyles from "@/components/AuthLayout/useAuthPageStyles";
import { useAuth } from "@/contexts/AuthContext";
import { handleOAuthCallback as handleOAuthCallbackApi } from "@/services/auth-api";
import { getFullPath } from "@/utils/env";
import type { AuthProvider } from "@/types";

const { Title, Text } = Typography;

const useStyles = createStyles(({ token }) => ({
  spinIcon: {
    fontSize: 48,
    color: token.colorPrimary,
  },
  statusIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  successIcon: {
    color: token.colorSuccess,
  },
  errorIcon: {
    color: token.colorError,
  },
  title: {
    marginTop: 16,
    marginBottom: 8,
  },
  description: {
    color: token.colorTextSecondary,
    marginBottom: 24,
  },
}));

type CallbackStatus = "loading" | "success" | "error";

const OAuthCallback: React.FC = () => {
  const { styles: authStyles } = useAuthPageStyles();
  const { styles, cx } = useStyles();
  const navigate = useNavigate();
  const { provider } = useParams<{ provider: string }>();
  const [searchParams] = useSearchParams();
  const { refreshUser } = useAuth();

  const [status, setStatus] = useState<CallbackStatus>("loading");
  const [errorMessage, setErrorMessage] = useState<string>("");
  const processedRef = useRef(false);
  const authCardStyle = {
    "--auth-card-width": "400px",
  } as React.CSSProperties;

  useEffect(() => {
    // 防止重复处理
    if (processedRef.current) return;
    processedRef.current = true;

    const processCallback = async () => {
      // 检查是否有错误参数（OAuth 服务器返回的错误）
      const error = searchParams.get("error");
      const errorDescription = searchParams.get("error_description");
      if (error) {
        setStatus("error");
        setErrorMessage(errorDescription || `OAuth 错误: ${error}`);
        return;
      }

      // 获取授权码
      const code = searchParams.get("code");
      const state = searchParams.get("state");

      if (!code) {
        setStatus("error");
        setErrorMessage("未收到授权码，请重试");
        return;
      }

      // 验证 state（防止 CSRF）
      const savedState = sessionStorage.getItem("oauth_state");
      if (state && savedState && state !== savedState) {
        setStatus("error");
        setErrorMessage("状态验证失败，请重试");
        return;
      }
      // 清除保存的 state
      sessionStorage.removeItem("oauth_state");

      // 验证 provider
      if (!provider || !["google", "github"].includes(provider.toLowerCase())) {
        setStatus("error");
        setErrorMessage(`不支持的登录方式: ${provider}`);
        return;
      }

      try {
        // 调用后端 API 处理 OAuth 回调
        const redirectUri = `${window.location.origin}${getFullPath(`/oauth/callback/${provider}`)}`;
        await handleOAuthCallbackApi(provider.toUpperCase() as AuthProvider, {
          code,
          redirectUri,
        });

        setStatus("success");

        // 刷新用户信息
        await refreshUser();

        // 延迟跳转，让用户看到成功状态
        setTimeout(() => {
          navigate("/");
        }, 1500);
      } catch (err) {
        setStatus("error");
        const errorMsg =
          err instanceof Error ? err.message : "登录处理失败，请重试";
        setErrorMessage(errorMsg);
        console.error("OAuth callback error:", err);
      }
    };

    processCallback();
  }, [provider, searchParams, refreshUser, navigate]);

  const getProviderName = (providerKey: string | undefined): string => {
    const names: Record<string, string> = {
      google: "Google",
      github: "GitHub",
    };
    return providerKey
      ? names[providerKey.toLowerCase()] || providerKey
      : "第三方";
  };

  const renderContent = () => {
    switch (status) {
      case "loading":
        return (
          <>
            <Spin
              indicator={<LoadingOutlined className={styles.spinIcon} spin />}
            />
            <Title level={4} className={styles.title}>
              正在处理 {getProviderName(provider)} 登录...
            </Title>
            <Text className={styles.description}>请稍候，正在验证您的身份</Text>
          </>
        );

      case "success":
        return (
          <>
            <CheckCircleOutlined
              className={cx(styles.statusIcon, styles.successIcon)}
            />
            <Title level={4} className={styles.title}>
              登录成功！
            </Title>
            <Text className={styles.description}>正在跳转到主页...</Text>
          </>
        );

      case "error":
        return (
          <>
            <CloseCircleOutlined
              className={cx(styles.statusIcon, styles.errorIcon)}
            />
            <Title level={4} className={styles.title}>
              登录失败
            </Title>
            <Alert
              message={errorMessage}
              type="error"
              showIcon
              style={{ marginBottom: 24, textAlign: "left" }}
            />
            <Button type="primary" onClick={() => navigate("/login")}>
              返回登录页面
            </Button>
          </>
        );

      default:
        return null;
    }
  };

  return (
    <div className={authStyles.container}>
      <Card
        className={authStyles.card}
        bordered={false}
        style={{ ...authCardStyle, textAlign: "center" }}
      >
        {renderContent()}
      </Card>
    </div>
  );
};

export default OAuthCallback;
