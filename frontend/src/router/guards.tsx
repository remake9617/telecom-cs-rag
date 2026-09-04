import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Button, Result, Spin } from 'antd';
import { useAuthStore } from '@/stores/authStore';
import type { Role } from '@/types';

// 路由守卫：RequireAuth 校验登录态；RequireRole 在其基础上校验角色。

/** 居中全屏加载（等待 /me 恢复用户信息时用） */
function CenterSpin() {
  return (
    <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Spin size="large" />
    </div>
  );
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const location = useLocation();
  if (!token) {
    // 记录来源，登录成功后回跳
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}

export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  if (!token) return <Navigate to="/login" replace />;
  // 刷新后 user 可能仍在经 /me 恢复：先展示 loading，避免把管理员误判为 403
  if (!user) return <CenterSpin />;
  if (user.role !== role) {
    return (
      <Result
        status="403"
        title="403"
        subTitle="抱歉，你没有访问该页面的权限"
        extra={
          <Button type="primary" onClick={() => (window.location.href = '/chat')}>
            返回对话端
          </Button>
        }
      />
    );
  }
  return <>{children}</>;
}
