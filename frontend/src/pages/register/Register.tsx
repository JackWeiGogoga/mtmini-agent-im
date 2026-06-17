/**
 * 用户注册页面
 */
import React, { useState } from "react";
import { Button, Card, Typography, Alert, Form, Input, Progress } from "antd";
import {
  UserAddOutlined,
  MailOutlined,
  LockOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useAuth } from "@/contexts/AuthContext";
import logo from "@/assets/logo.jpg";
import { register } from "@/services/auth-api";
import { message } from "@/components/AppMessageContext";
import useAuthPageStyles from "@/components/AuthLayout/useAuthPageStyles";
import { Link, useNavigate } from "react-router-dom";

const { Title, Text } = Typography;
import { createStyles } from "antd-style";

const useStyles = createStyles(({ token }) => ({
  passwordStrength: {
    marginTop: -16,
    marginBottom: 8,
  },
  passwordHint: {
    fontSize: 12,
    color: token.colorTextSecondary,
    marginTop: -12,
    marginBottom: 16,
  },
}));

interface RegisterFormValues {
  email: string;
  password: string;
  confirmPassword: string;
  nickname?: string;
}

/**
 * 计算密码强度
 */
function getPasswordStrength(password: string): {
  percent: number;
  status: "exception" | "normal" | "success";
} {
  if (!password) return { percent: 0, status: "exception" };

  let strength = 0;

  // 长度检查
  if (password.length >= 8) strength += 25;
  if (password.length >= 12) strength += 10;

  // 包含小写字母
  if (/[a-z]/.test(password)) strength += 20;

  // 包含大写字母
  if (/[A-Z]/.test(password)) strength += 20;

  // 包含数字
  if (/\d/.test(password)) strength += 20;

  // 包含特殊字符
  if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) strength += 15;

  strength = Math.min(100, strength);

  if (strength < 50) return { percent: strength, status: "exception" };
  if (strength < 80) return { percent: strength, status: "normal" };
  return { percent: strength, status: "success" };
}

const Register: React.FC = () => {
  const { styles } = useAuthPageStyles();
  const { styles: registerStyles } = useStyles();
  const { refreshUser } = useAuth();
  const navigate = useNavigate();

  const [form] = Form.useForm<RegisterFormValues>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const authCardStyle = {
    "--auth-card-width": "460px",
  } as React.CSSProperties;

  const passwordStrength = getPasswordStrength(password);

  /**
   * 提交注册
   */
  const handleRegister = async (values: RegisterFormValues) => {
    setLoading(true);
    setError(null);
    try {
      await register({
        email: values.email,
        password: values.password,
        nickname: values.nickname,
      });
      message.success("注册成功！");
      await refreshUser();
      navigate("/");
    } catch (err: unknown) {
      const errorMsg = err instanceof Error ? err.message : "注册失败，请重试";
      setError(errorMsg);
      console.error("Register error:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.container}>
      <Card className={styles.card} bordered={false} style={authCardStyle}>
        <div className={styles.logo}>
          <img src={logo} alt="Logo" className={styles.logoImage} />
          <Title level={3} className={styles.title}>
            创建账号
          </Title>
          <Text className={styles.subtitle}>注册 外卖大师管理系统账号</Text>
        </div>

        {error && (
          <Alert
            message={error}
            type="error"
            showIcon
            closable
            style={{ marginBottom: 16 }}
            onClose={() => setError(null)}
          />
        )}

        <Form
          form={form}
          className={styles.form}
          onFinish={handleRegister}
          size="large"
          layout="vertical"
        >
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: "请输入邮箱" },
              { type: "email", message: "请输入有效的邮箱地址" },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="your@email.com"
              autoComplete="email"
            />
          </Form.Item>

          <Form.Item name="nickname" label="昵称（可选）">
            <Input
              prefix={<UserOutlined />}
              placeholder="您的昵称"
              autoComplete="nickname"
            />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 8, message: "密码至少8位" },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/,
                message: "密码需包含大小写字母和数字",
              },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="至少8位，包含大小写字母和数字"
              autoComplete="new-password"
              onChange={(e) => setPassword(e.target.value)}
            />
          </Form.Item>

          {password && (
            <div className={registerStyles.passwordStrength}>
              <Progress
                percent={passwordStrength.percent}
                status={passwordStrength.status}
                showInfo={false}
                size="small"
              />
              <Text type="secondary" style={{ fontSize: 12 }}>
                密码强度:{" "}
                {passwordStrength.percent < 50
                  ? "弱"
                  : passwordStrength.percent < 80
                    ? "中"
                    : "强"}
              </Text>
            </div>
          )}

          <Form.Item
            name="confirmPassword"
            label="确认密码"
            dependencies={["password"]}
            rules={[
              { required: true, message: "请确认密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("password") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="再次输入密码"
              autoComplete="new-password"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              className={styles.primaryButton}
              loading={loading}
              icon={<UserAddOutlined />}
            >
              注册
            </Button>
          </Form.Item>
        </Form>

        <div className={styles.linkRow}>
          <Text type="secondary">已有账号？</Text>
          <Link to="/login"> 立即登录</Link>
        </div>

        <div className={styles.footer}>
          <Text type="secondary">
            注册即表示您同意我们的
            <a href="/terms" style={{ margin: "0 4px" }}>
              服务条款
            </a>
            和
            <a href="/privacy" style={{ margin: "0 4px" }}>
              隐私政策
            </a>
          </Text>
        </div>
      </Card>
    </div>
  );
};

export default Register;
