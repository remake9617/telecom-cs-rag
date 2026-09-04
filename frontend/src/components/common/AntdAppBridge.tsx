import { App } from 'antd';
import { useEffect } from 'react';
import { setMessageApi } from '@/utils/notify';

// 把 antd <App> 提供的 theme-aware message 实例注入全局 notify，
// 让 axios 拦截器 / SSE 解析器等「非组件环境」的提示也享受主题与上下文（避免静态方法的 context 警告）。
// 必须渲染在 <ConfigProvider><App> 内部。
export default function AntdAppBridge() {
  const { message } = App.useApp();
  useEffect(() => {
    setMessageApi(message);
  }, [message]);
  return null;
}
