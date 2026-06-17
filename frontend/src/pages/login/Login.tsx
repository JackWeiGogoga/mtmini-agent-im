/**
 * 登录页面
 *
 * 支持多种登录方式：
 * - 手机验证码登录
 * - 邮箱密码登录
 * - Google OAuth 登录
 * - GitHub OAuth 登录
 */
import React, { useState, useEffect, useMemo } from "react";
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Form,
  Input,
  Spin,
  Tabs,
  Typography,
} from "antd";
import {
  LockOutlined,
  LoginOutlined,
  MailOutlined,
  MobileOutlined,
} from "@ant-design/icons";
import { Link, useNavigate, useSearchParams } from "react-router-dom";

import logo from "@/assets/logo.jpg";
import { message } from "@/components/AppMessageContext";
import useAuthPageStyles from "@/components/AuthLayout/useAuthPageStyles";
import { useAuth } from "@/contexts/AuthContext";
import {
  loginWithPassword,
  loginWithSms,
  sendSmsCode,
} from "@/services/auth-api";
import type { BackendPortal } from "@/types";
import { getFullPath } from "@/utils/env";

const { Title, Text } = Typography;

interface PasswordLoginFormValues {
  email: string;
  password: string;
  rememberMe: boolean;
}

interface SmsLoginFormValues {
  phone: string;
  code: string;
  rememberMe: boolean;
}

const Login: React.FC = () => {
  const { styles } = useAuthPageStyles();
  const { refreshUser, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [passwordForm] = Form.useForm<PasswordLoginFormValues>();
  const [smsForm] = Form.useForm<SmsLoginFormValues>();
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [smsLoginLoading, setSmsLoginLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [activeTab, setActiveTab] = useState("sms");
  const isAdminLoginMode = searchParams.get("admin") === "1";
  const requestedPortal = useMemo<BackendPortal | undefined>(() => {
    const portal = searchParams.get("portal");
    if (
      portal === "ADMIN" ||
      portal === "ORDINARY" ||
      portal === "SALES" ||
      portal === "YOUZAN"
    ) {
      return portal;
    }
    return undefined;
  }, [searchParams]);

  const initialError = useMemo(() => {
    const errorCode = searchParams.get("error");
    const errorMessage = searchParams.get("message");
    if (errorCode) {
      return errorMessage || `登录失败: ${errorCode}`;
    }
    return null;
  }, [searchParams]);

  const [error, setError] = useState<string | null>(initialError);
  const authCardStyle = {
    "--auth-card-width": "460px",
  } as React.CSSProperties;

  useEffect(() => {
    const errorCode = searchParams.get("error");
    if (errorCode) {
      navigate("/login", { replace: true });
    }
  }, [searchParams, navigate]);

  useEffect(() => {
    if (countdown <= 0) {
      return undefined;
    }

    const timer = window.setTimeout(() => {
      setCountdown((prev) => prev - 1);
    }, 1000);

    return () => window.clearTimeout(timer);
  }, [countdown]);

  const handlePasswordLogin = async (values: PasswordLoginFormValues) => {
    setPasswordLoading(true);
    setError(null);
    try {
      await loginWithPassword({
        email: values.email,
        password: values.password,
        rememberMe: values.rememberMe,
        portal: "ADMIN",
      });
      message.success("登录成功");
      await refreshUser();
      window.location.href = getFullPath("/");
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "登录失败，请重试";
      setError(errorMsg);
      console.error("Password login error:", err);
      setPasswordLoading(false);
    }
  };

  const handleSendSmsCode = async () => {
    try {
      const values = await smsForm.validateFields(["phone"]);
      setSendingCode(true);
      setError(null);
      await sendSmsCode({ phone: values.phone });
      message.success("验证码已发送，请注意查收");
      setCountdown(60);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
        console.error("Send sms code error:", err);
      }
    } finally {
      setSendingCode(false);
    }
  };

  const handleSmsLogin = async (values: SmsLoginFormValues) => {
    setSmsLoginLoading(true);
    setError(null);
    try {
      await loginWithSms({
        phone: values.phone,
        code: values.code,
        rememberMe: values.rememberMe,
        portal: requestedPortal,
      });
      message.success("登录成功");
      await refreshUser();
      window.location.href = getFullPath("/");
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "登录失败，请重试";
      setError(errorMsg);
      console.error("Sms login error:", err);
      setSmsLoginLoading(false);
    }
  };

  const loginTabItems = [
    {
      key: "sms",
      label: "手机号登录",
      children: (
        <Spin spinning={smsLoginLoading} tip="登录中...">
          <Form
            form={smsForm}
            className={styles.form}
            onFinish={handleSmsLogin}
            initialValues={{ rememberMe: false }}
            size="large"
          >
            <Form.Item
              name="phone"
              rules={[
                { required: true, message: "请输入手机号" },
                {
                  pattern: /^(?:\+?86)?1[3-9]\d{9}$/,
                  message: "请输入有效的手机号",
                },
              ]}
            >
              <Input
                prefix={<MobileOutlined />}
                placeholder="手机号"
                autoComplete="tel"
              />
            </Form.Item>
            <div className={styles.codeRow}>
              <Form.Item
                name="code"
                rules={[{ required: true, message: "请输入验证码" }]}
                className={styles.codeInput}
              >
                <Input
                  prefix={<LockOutlined />}
                  placeholder="短信验证码"
                  autoComplete="one-time-code"
                />
              </Form.Item>
              <Button
                className={styles.codeButton}
                onClick={handleSendSmsCode}
                disabled={sendingCode || countdown > 0}
                loading={sendingCode}
              >
                {countdown > 0 ? `${countdown}s 后重试` : "发送验证码"}
              </Button>
            </div>
            <div className={styles.formFooter}>
              <Form.Item name="rememberMe" valuePropName="checked" noStyle>
                <Checkbox>记住我（30天）</Checkbox>
              </Form.Item>
            </div>
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                className={styles.primaryButton}
                loading={smsLoginLoading}
                icon={<LoginOutlined />}
              >
                登录
              </Button>
            </Form.Item>
          </Form>
        </Spin>
      ),
    },
    ...(isAdminLoginMode
      ? [
          {
            key: "password",
            label: "邮箱登录",
            children: (
              <Spin spinning={passwordLoading} tip="登录中...">
                <Form
                  form={passwordForm}
                  className={styles.form}
                  onFinish={handlePasswordLogin}
                  initialValues={{ rememberMe: false }}
                  size="large"
                >
                  <Form.Item
                    name="email"
                    rules={[
                      { required: true, message: "请输入邮箱" },
                      { type: "email", message: "请输入有效的邮箱地址" },
                    ]}
                  >
                    <Input
                      prefix={<MailOutlined />}
                      placeholder="邮箱地址"
                      autoComplete="email"
                    />
                  </Form.Item>
                  <Form.Item
                    name="password"
                    rules={[{ required: true, message: "请输入密码" }]}
                  >
                    <Input.Password
                      prefix={<LockOutlined />}
                      placeholder="密码"
                      autoComplete="current-password"
                    />
                  </Form.Item>
                  <div className={styles.formFooter}>
                    <Form.Item
                      name="rememberMe"
                      valuePropName="checked"
                      noStyle
                    >
                      <Checkbox>记住我（30天）</Checkbox>
                    </Form.Item>
                    <Link to="/forgot-password">忘记密码？</Link>
                  </div>
                  <Form.Item>
                    <Button
                      type="primary"
                      htmlType="submit"
                      className={styles.primaryButton}
                      loading={passwordLoading}
                      icon={<LoginOutlined />}
                    >
                      登录
                    </Button>
                  </Form.Item>
                </Form>
              </Spin>
            ),
          },
        ]
      : []),
  ];

  if (isAuthenticated) {
    return (
      <div className={styles.container}>
        <Card className={styles.card} style={authCardStyle}>
          <div className={styles.logo}>
            <img src={logo} alt="Logo" className={styles.logoImage} />
            <Title level={3} className={styles.title}>
              外卖大师管理系统
            </Title>
          </div>
          <Alert title="您已登录，正在跳转..." type="success" showIcon />
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Card className={styles.card} bordered={false} style={authCardStyle}>
        <div className={styles.logo}>
          <img src={logo} alt="Logo" className={styles.logoImage} />
          <Title level={3} className={styles.title}>
            外卖大师管理系统
          </Title>
          <Text className={styles.subtitle}>登录以继续</Text>
        </div>

        {error && (
          <Alert
            title={error}
            type="error"
            showIcon
            closable
            style={{ marginBottom: 16 }}
            onClose={() => setError(null)}
          />
        )}

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={loginTabItems}
        />

        <div className={styles.footer}>
          <Text type="secondary">
            © {new Date().getFullYear()} 外卖大师管理系统. All rights reserved.
          </Text>
        </div>
      </Card>
    </div>
  );
};

export default Login;
