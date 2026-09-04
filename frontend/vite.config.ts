import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// Vite 配置：React 插件 + `@` 别名 + 开发服务器 /api 代理。
//
// 为什么加 dev proxy：dev 下前端一律用「同源相对路径」/api（见 src/config.ts），
//   - Mock 模式：请求被 MSW 的 Service Worker 拦截（不到达 proxy）；
//   - 真实联调：MSW 关闭，/api 请求由该 proxy 转发到 VITE_API_BASE_URL(http://localhost:8080)。
//   两种情况浏览器视角都是同源 -> 无 CORS 预检问题（JSON POST 的预检不被 SW 拦截）。
// proxy 目标取自环境变量（loadEnv），不硬编码后端地址；http-proxy 默认流式转发，SSE 可正常透传。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.VITE_API_BASE_URL || 'http://localhost:8080';

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        '/api': { target: backend, changeOrigin: true },
      },
    },
  };
});
