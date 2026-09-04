import { useState } from 'react';
import { Button, Card, Form, Input, Space, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '@/api';
import { useAuthStore } from '@/stores/authStore';

const { Title, Text } = Typography;

interface RegisterForm {
  username: string;
  password: string;
  confirm: string;
}

// 注册页：访客自助注册，成功后直接登录并进入对话端。
export default function Register() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: RegisterForm): Promise<void> => {
    setLoading(true);
    try {
      const res = await authApi.register({ username: values.username, password: values.password });
      setAuth(res.token, res.user);
      navigate('/chat', { replace: true });
    } catch {
      // 错误信息由拦截器统一弹出
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
            注册账号
          </Title>
          <Text type="secondary">注册后即可体验智能问答</Text>
        </Space>

        <Form<RegisterForm> layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="username"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, message: '用户名至少 3 个字符' },
            ]}
          >
            <Input prefix={<UserOutlined />} placeholder="用户名" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少 6 位' },
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Form.Item
            name="confirm"
            dependencies={['password']}
            rules={[
              { required: true, message: '请确认密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="确认密码" size="large" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 8 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading}>
              注册并登录
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <Text type="secondary">已有账号？</Text>
          <Link to="/login">返回登录</Link>
        </div>
      </Card>
    </div>
  );
}
