import { App as AntApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { router } from '@/router';
import { useUiStore } from '@/stores/uiStore';
import { buildTheme } from '@/styles/theme';
import AntdAppBridge from '@/components/common/AntdAppBridge';
import { useMe } from '@/hooks/useAuth';

// 全局 QueryClient：关闭重试（错误提示已在 axios 拦截器统一弹出，重试会造成重复弹窗），
// 关闭窗口聚焦重取（避免演示时频繁刷新），默认 30s 新鲜度。
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, staleTime: 30_000 },
    mutations: { retry: false },
  },
});

/** 应用引导副作用：刷新后恢复当前登录用户（供路由守卫与角色入口使用） */
function Bootstrap() {
  useMe();
  return null;
}

export default function App() {
  const themeMode = useUiStore((s) => s.themeMode);
  return (
    <ConfigProvider locale={zhCN} theme={buildTheme(themeMode)}>
      <AntApp>
        {/* 注入 theme-aware message 实例，供拦截器/SSE 等非组件环境使用 */}
        <AntdAppBridge />
        <QueryClientProvider client={queryClient}>
          <Bootstrap />
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
