# FRONTEND-DECISIONS.md — 前端技术决策日志（路4）

> 独立于根目录 `DECISIONS.md`，避免前后端并行写入冲突；集成时由负责人合并。
> 条目格式：**决策 / 理由 / 被放弃的方案 / 日期**。技术栈基线见后端 D16（React 18 + TS + AntD 5 + Vite）。

---

## FD-1. 状态管理 = Zustand（客户端态）+ TanStack Query v5（服务端态）
- **决策**：客户端态（会话流式缓冲、JWT、主题、侧栏折叠）用 Zustand；服务端态（会话列表、历史、知识库/文档、工单、统计等 REST 数据）用 TanStack Query 管理缓存/分页/失效/加载错误态。SSE 高频 delta 只进 Zustand，不进 Query。
- **理由**：职责分离清晰——Query 擅长「请求缓存 + 失效重取」，Zustand 轻量无样板、适合高频写入的流式缓冲；二者组合样板代码少、面试易讲清「服务端态 vs 客户端态」的边界。
- **被放弃的方案**：Redux Toolkit + RTK Query（全家桶但样板多、流式高频更新需额外优化）；仅 Zustand 手写请求（无缓存/失效/重试，列表分页要手搓）。
- **日期**：2026-09-04

## FD-2. SSE 流式方案 = fetch + ReadableStream + TextDecoder 手写解析
- **决策**：`POST /api/qa/chat/stream` 用 `fetch` 拿到 `res.body.getReader()`，`TextDecoder` 增量解码，按 `\n\n` 切分完整事件，解析 `event:`/`data:`（多行 data 拼接）后分发到 message/reference/done/error/ticket_hint 回调；用 `AbortController` 支持「停止生成」。
- **理由**：原生 `EventSource` 只支持 GET 且无法携带请求体，本接口是 POST + JSON body，必须手写；按「完整事件」切分可正确处理跨 chunk 的半行，避免丢字/串字。
- **被放弃的方案**：原生 EventSource（不支持 POST，直接排除）；`@microsoft/fetch-event-source`（可用但引额外依赖，手写更可控且便于答辩讲原理）。
- **日期**：2026-09-04

## FD-3. Mock 方案 = MSW v2（网络层拦截，含 SSE 流式）
- **决策**：用 MSW 在 Service Worker 层拦截请求，handlers 按域拆分、统一产出契约规定的 `R<T>`/`PageVO<T>`；SSE 用 `HttpResponse` + `ReadableStream` 真实模拟逐帧推送；内存库 `db` 让「发消息生成会话、上传入库、创建/回复工单」即时反映；`VITE_USE_MOCK` 开关，动态 import 保证生产/联调不打入 mock 代码。
- **理由**：网络层拦截对 axios 与 fetch/SSE 全覆盖、零业务代码侵入，且能在 `vite preview` 与测试中复用；可模拟流式打字机，脱离后端也能完整演示。
- **被放弃的方案**：vite-plugin-mock（dev 中间件，SSE 需另造、build 预览不生效）；axios-mock-adapter（只拦 axios，SSE 要单独造桩，两套机制并存）。
- **日期**：2026-09-04

## FD-4. 路由 = React Router 6（createBrowserRouter）+ 懒加载 + 角色守卫
- **决策**：`createBrowserRouter` 声明式路由；页面全部 `React.lazy` + `Suspense` 代码分割；`RequireAuth`（登录态）与 `RequireRole('ADMIN')`（角色）守卫；用户端 `/chat`、`/chat/:conversationId`、`/tickets`，后台 `/admin/{dashboard,kb,documents,kb/:kbId/documents,tickets,users}`。
- **理由**：数据路由是 RR6 推荐范式；懒加载让 plots/markdown 等重依赖按需加载；守卫集中处理鉴权，页面组件保持纯粹。刷新后 user 未就绪时守卫先展示 loading，避免把管理员误判 403。
- **被放弃的方案**：`<BrowserRouter>` + `<Routes>` 老写法（无数据路由能力）；不分角色守卫（后台裸露）。
- **日期**：2026-09-04

## FD-5. API 层 = axios 拦截器解包 R<T> + 类型化 http 包装
- **决策**：单 axios 实例，`baseURL` 取 `VITE_API_BASE_URL`；请求拦截器注入 `Authorization: Bearer <JWT>`（注册/登录白名单除外）；响应拦截器 `code===0` 解包返回 `data`，非 0 抛 `BizError` 并弹 `message`，HTTP 401 清登录态跳登录、403 提示无权限；`http.get/post/put/del<T>()` 让调用处直接拿 `Promise<T>`。
- **理由**：把「统一响应解析 + 鉴权 + 错误提示」收敛到一处，业务代码只关心数据；与契约第 5 节完全对齐。
- **踩坑记录（可讲）**：axios 升到 1.20 后 `client.get<any,T>` 的双泛型返回被包成 `AxiosResponseResult`，不再直接得到 `Promise<T>`；改用 `as unknown as Promise<T>` 做与 axios 版本无关的显式对齐（运行期拦截器已解包，语义一致）。
- **被放弃的方案**：每个接口手动判断 code（重复且易漏）；用 fetch 封装 REST（要自己处理拦截/超时/错误分支）。
- **日期**：2026-09-04

## FD-6. 主题 = AntD ConfigProvider token + 亮/暗 algorithm
- **决策**：`buildTheme(mode)` 产出 `ThemeConfig`，主色电信蓝 `#1677ff`、圆角 8；亮/暗用 `defaultAlgorithm`/`darkAlgorithm`，由 `uiStore.themeMode` 驱动；`locale=zh_CN`；全局消息用 antd `<App>` + `AntdAppBridge` 注入 theme-aware `message` 实例给拦截器/SSE 使用。
- **理由**：token 集中配置避免主色散落硬编码；暗色一键切换；`<App>` 桥接解决「静态 message 无法消费动态主题」的 antd v5 痛点。
- **被放弃的方案**：各组件内联颜色（难维护）；直接用静态 `message`（暗色下样式不一致、有 context 警告）。
- **日期**：2026-09-04

## FD-7.（待负责人确认）用户管理端点为提案，未在 rest-api.md 冻结
- **决策**：交付范围含「用户管理」，但 `contract/rest-api.md` 未定义用户 CRUD 端点。前端按 CONVENTIONS 与既有 `UserVO`/`R<PageVO<T>>` 约定，提案 `GET /api/system/users?current=&size=&keyword=`，仅用于 Mock 独立演示，页面顶部标注「提案」提示。**未修改 rest-api.md**。
- **理由**：遵守协作纪律「契约变更先提出、不单方面改」；同时不阻塞前端交付与演示。
- **被放弃的方案**：静默自造契约并当作已冻结（违反纪律）；砍掉用户管理页（不满足交付范围）。
- **待办**：请负责人确认端点路径/出入参与角色权限，确认后由负责人更新 `rest-api.md`，前端据此校准 `api/modules/system.ts`。
- **日期**：2026-09-04

## FD-8. 版本锁定 = React 18.3 + Vite 5 + AntD 5（不用 create-vite 默认模板）
- **决策**：手动脚手架并锁定 React 18.3.1 + Vite 5 + @vitejs/plugin-react 4 + AntD 5.21 + TS 5.6，而非 `npm create vite@latest`。
- **理由**：`create-vite@latest` 默认给 React 19 + Vite 7，而 D16 硬要求 React 18、AntD 5 与 React 18 组合最稳；手动锁定可控且可复现。Node 24 下 Vite 5.4 正常运行（engines 满足 >=20）。
- **被放弃的方案**：直接 create-vite（会引入 React 19，违背 D16）；Vite 7（默认模板绑定 React 19）。
- **日期**：2026-09-04

## FD-9. 环境变量与 Mock 开关
- **决策**：`VITE_API_BASE_URL`（默认 `http://localhost:8080`）+ `VITE_USE_MOCK`（默认 `true`）；`.env` gitignore、`.env.example` 入库；MSW handler 用 `*/api/...` 通配匹配任意 origin，故 mock 开启时对 `localhost:8080` 的跨源请求也能拦截。
- **理由**：满足「base URL 走环境变量、禁止硬编码、不下发密钥」的硬约束；联调时只需把 `VITE_USE_MOCK` 置 false（并由后端开启 CORS）。
- **被放弃的方案**：Vite dev proxy（与 MSW 网络层拦截重复，且 base URL 硬约束要求直连）。
- **日期**：2026-09-04

## FD-10. 错误提示集中化 + Query 关闭重试
- **决策**：业务/网络错误统一在 axios 拦截器弹一次 `message`；TanStack Query 全局 `retry:false`、`refetchOnWindowFocus:false`；组件侧只用 `StateBlock` 展示内联错误态与重试按钮，不再重复弹窗。
- **理由**：避免「拦截器 + Query 重试 + 组件 onError」造成的重复弹窗；演示时不被窗口聚焦重取打扰。
- **被放弃的方案**：默认 retry:3（失败会连弹多次）；错误提示分散在各组件（不一致、易漏）。
- **日期**：2026-09-04

---

## 待优化 / 后续（非阻塞）
- Dashboard 因 `@ant-design/plots`(G2) 单包约 1.48MB（gzip 440KB），已懒加载隔离；后续可用 `manualChunks` 或按需引入图表类型进一步瘦身。
- 流式渲染目前逐 delta 直接 setState + `React.memo` 气泡；若超长回答出现卡顿，可加 `requestAnimationFrame` 合批。
- 用户管理端点待负责人确认后并入契约（见 FD-7）。
