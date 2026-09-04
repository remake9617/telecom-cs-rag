import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import ErrorBoundary from '@/components/common/ErrorBoundary';
import '@/styles/global.css';
import { USE_MOCK } from '@/config';

// 条件启用 MSW：仅当 VITE_USE_MOCK === 'true'。
// 用动态 import 保证「联调真实后端 / 生产构建」时不把 mock 代码打进主包。
async function enableMocking(): Promise<void> {
  if (!USE_MOCK) return;
  const { worker } = await import('@/mocks/browser');
  // bypass：未命中 mock 的请求（静态资源等）直接放行，避免控制台噪声
  await worker.start({ onUnhandledRequest: 'bypass' });
}

enableMocking().then(() => {
  ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
    <React.StrictMode>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </React.StrictMode>,
  );
});
