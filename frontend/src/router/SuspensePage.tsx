import { Suspense, type ReactNode } from 'react';
import { Spin } from 'antd';

/**
 * 懒加载页面的统一 Suspense 兜底。
 * 独立成文件：router/index.tsx 导出的是非组件（router 对象），
 * 若组件与非组件混在同一文件会破坏 React Fast Refresh（react-refresh/only-export-components）。
 */
export function SuspensePage({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<div style={{ padding: 48, textAlign: 'center' }}><Spin size="large" /></div>}>
      {children}
    </Suspense>
  );
}
