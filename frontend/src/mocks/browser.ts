import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

// 浏览器端 MSW worker：仅当 VITE_USE_MOCK === 'true' 时在 main.tsx 中启动。
// 未命中 mock 的请求（静态资源等）放行，避免控制台噪声告警。
export const worker = setupWorker(...handlers);
