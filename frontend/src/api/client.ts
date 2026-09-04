import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { BizError, type R } from './types';
import { useAuthStore } from '@/stores/authStore';
import { notify } from '@/utils/notify';
import { handleUnauthorized } from '@/utils/redirect';
import { API_BASE_URL } from '@/config';

// axios 实例 + 拦截器：统一注入 JWT、解包 R<T>、集中错误提示。
// 错误提示只在此处弹一次；TanStack Query 关闭重试（见 QueryClient 配置），避免重复弹窗。

/** 免鉴权白名单：注册 / 登录不带 Authorization（见 rest-api.md 认证约定） */
const AUTH_FREE_PATHS = ['/api/auth/register', '/api/auth/login'];

export const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

// 请求拦截器：注入 Bearer JWT
client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const url = config.url ?? '';
  const authFree = AUTH_FREE_PATHS.some((p) => url.includes(p));
  const token = useAuthStore.getState().token;
  if (token && !authFree) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// 响应拦截器：code=0 解包返回 data；非 0 抛 BizError 并提示；HTTP 401/403 特殊处理
client.interceptors.response.use(
  // 返回类型标为 any：运行时把 R<T> 解包成 data，配合 http.ts 的 client.get<any,T> 得到 Promise<T>
  (response): any => {
    const r = response.data as R<unknown>;
    if (r && typeof r.code === 'number') {
      if (r.code === 0) return r.data;
      // RBAC（契约通用约定）：已认证但角色不足 -> HTTP 200 + code=1003，提示“无权限”但不跳登录
      notify.error(r.code === 1003 ? r.message || '无权限访问该资源' : r.message || '请求失败');
      return Promise.reject(new BizError(r.code, r.message, r.traceId));
    }
    // 非标准响应体（理论上不出现）：原样返回 data
    return response.data;
  },
  (error: AxiosError) => {
    const status = error.response?.status;
    if (status === 401) {
      // 未认证（无/失效 token）：清登录态 + 跳登录，与 SSE 的 401 处理一致
      notify.error('登录已过期，请重新登录');
      handleUnauthorized();
    } else if (status === 403) {
      notify.error('无权限访问该资源');
    } else if (error.code === 'ECONNABORTED') {
      notify.error('请求超时，请稍后重试');
    } else {
      notify.error(error.message || '网络异常，请检查连接');
    }
    return Promise.reject(error);
  },
);
