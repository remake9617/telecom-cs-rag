import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '@/api';
import { useAuthStore } from '@/stores/authStore';

const { Title, Text } = Typography;

interface LoginForm {
  username: string;
  password: string;
}

// 登录页：账号密码登录，成功后写入 authStore 并回跳来源页。
export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);

  // 回跳优先级：路由 state.from > ?redirect= > /chat
  const from =
    (location.state as { from?: { pathname: string } } | null)?.from?.pathname ??
    new URLSearchParams(location.search).get('redirect') ??
    '/chat';

  const onFinish = async (values: LoginForm): Promise<void> => {
    setLoading(true);
    try {
      const res = await authApi.login(values);
      setAuth(res.token, res.user);
      navigate(from, { replace: true });
    } catch {
      // 错误信息已由 axios 拦截器统一弹出，这里只需结束 loading
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
        background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
      }}
    >
      <Card style={{ width: 400, maxWidth: '100%', boxShadow: '0 8px 24px rgba(0,0,0,0.15)' }}>
        <Space direction="vertical" size={4} style={{ width: '100%', textAlign: 'center', marginBottom: 20 }}>
          <Title level={3} style={{ margin: 0 }}>
            电信智能客服
          </Title>
          <Text type="secondary">基于 RAG 的运营商智能问答系统</Text>
        </Space>

        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="演示账号（Mock）"
          description={
            <Text style={{ fontSize: 12 }}>
              管理员：admin / 任意密码　·　访客：任意用户名 / 任意密码　·　错误演示：密码填 wrong
            </Text>
          }
        />

        <Form<LoginForm> layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <Text type="secondary">还没有账号？</Text>
          <Link to="/register">立即注册</Link>
        </div>
      </Card>
    </div>
  );
}
