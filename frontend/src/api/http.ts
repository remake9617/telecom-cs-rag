import type { AxiosRequestConfig } from 'axios';
import { client } from './client';

// 类型化请求包装：响应拦截器已把 R<T> 解包为 data，运行期 resolve 的就是业务负载本身。
// axios 1.20 起 get/post 的泛型返回被包成 AxiosResponseResult，双泛型写法不再直接得到 Promise<T>，
// 故用 `as unknown as Promise<T>` 做与 axios 版本无关的显式对齐（语义与运行时一致）。

export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    client.get(url, config) as unknown as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    client.post(url, data, config) as unknown as Promise<T>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    client.put(url, data, config) as unknown as Promise<T>,
  del: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    client.delete(url, config) as unknown as Promise<T>,
};
