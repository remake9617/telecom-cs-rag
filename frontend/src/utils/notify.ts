import { message as staticMessage } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';

// 全局消息提示桥：让「非 React 环境」（如 axios 拦截器、SSE 解析器）也能弹出
// 与 ConfigProvider 主题一致的消息。AntdAppBridge 挂载后注入 theme-aware 实例；
// 未注入前（极早期错误）回退到 antd 静态 message，保证提示不丢。

let api: MessageInstance | null = null;

export function setMessageApi(instance: MessageInstance): void {
  api = instance;
}

export const notify = {
  success: (content: string) => (api ?? staticMessage).success(content),
  error: (content: string) => (api ?? staticMessage).error(content),
  info: (content: string) => (api ?? staticMessage).info(content),
  warning: (content: string) => (api ?? staticMessage).warning(content),
  loading: (content: string) => (api ?? staticMessage).loading(content),
};
