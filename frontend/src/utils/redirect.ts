import { useAuthStore } from '@/stores/authStore';

// 未认证（HTTP 401）统一处理：清登录态 + 跳登录并带回跳地址。
// 供 axios 响应拦截器与 SSE(fetch) 复用，保证两条链路的 401 行为一致。
// 说明：整页跳转可规避与路由的循环依赖；会话失效属低频场景，可接受。
export function handleUnauthorized(): void {
  useAuthStore.getState().logout();
  const { pathname, search } = window.location;
  if (pathname.startsWith('/login')) return;
  const redirect = encodeURIComponent(pathname + search);
  window.location.assign(`/login?redirect=${redirect}`);
}
