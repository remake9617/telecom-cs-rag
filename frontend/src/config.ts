// 运行时 API 基址与 Mock 开关（唯一真源，供 axios 与 SSE 共用）。
//
// 为什么 Mock 模式要用「同源相对路径」而不是直接连 VITE_API_BASE_URL(http://localhost:8080)：
//   页面跑在 :5173，若请求打到跨源的 :8080，application/json 的 POST 会触发 CORS 预检(OPTIONS)，
//   而 Service Worker 不拦截预检请求 -> 预检会真的发往未启动的 :8080 而失败，导致 MSW 拦不到主请求。
//   因此 Mock 模式下 baseURL 置空('')，请求变为同源 /api/...，被 MSW 的 worker 干净拦截，无跨源/预检问题。
//
// 真实联调 / 生产：仍严格使用环境变量 VITE_API_BASE_URL（默认 http://localhost:8080），不硬编码后端地址。

/** 是否启用 MSW Mock */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

/** axios / fetch 使用的 API 基址：Mock 模式同源('')，否则取环境变量 */
export const API_BASE_URL = USE_MOCK ? '' : import.meta.env.VITE_API_BASE_URL;
