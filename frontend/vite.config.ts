import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Vite 配置：React 插件 + `@` 路径别名 + 开发端口。
// 为什么不用 dev proxy：Mock 由 MSW 在浏览器网络层拦截（含 SSE 流式），
// 真实后端联调时把 .env 的 VITE_USE_MOCK 置 false，由后端开启 CORS 即可，
// API 基址统一走 VITE_API_BASE_URL 环境变量，禁止硬编码。
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    host: true,
  },
});
