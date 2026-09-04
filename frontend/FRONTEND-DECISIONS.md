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

## FD-7. 用户管理端点（已由统筹确认并写入 rest-api.md 第 7 节）
- **决策**：`GET /api/system/users?current=&size=&keyword=`（仅 ADMIN，返回 `R<PageVO<UserVO>>`，keyword 按用户名/昵称模糊搜）。前端 `api/modules/system.ts` + `hooks/useSystem.ts` + `pages/admin/Users.tsx` 按此正式实现，已移除页面「提案」标注。
- **理由**：交付范围含用户管理而原契约缺该端点；先按纪律提案、由统筹拍板并入契约后再落地，避免单方面改契约。
- **被放弃的方案**：静默自造契约当作已冻结（违反纪律）；砍掉用户管理页（不满足交付范围）。
- **状态**：已确认（统筹更新 rest-api.md 第 7 节），前端已对齐。
- **日期**：2026-09-04

## FD-8. 版本锁定 = React 18.3 + Vite 5 + AntD 5（不用 create-vite 默认模板）
- **决策**：手动脚手架并锁定 React 18.3.1 + Vite 5 + @vitejs/plugin-react 4 + AntD 5.21 + TS 5.6，而非 `npm create vite@latest`。
- **理由**：`create-vite@latest` 默认给 React 19 + Vite 7，而 D16 硬要求 React 18、AntD 5 与 React 18 组合最稳；手动锁定可控且可复现。Node 24 下 Vite 5.4 正常运行（engines 满足 >=20）。
- **被放弃的方案**：直接 create-vite（会引入 React 19，违背 D16）；Vite 7（默认模板绑定 React 19）。
- **日期**：2026-09-04

## FD-9. API 基址策略 = 同源相对路径(dev) + Vite dev proxy；环境变量驱动
- **决策**：`src/config.ts` 统一裁决基址——Mock 或 dev 一律用同源相对路径 `''`（请求 `/api/...`），生产构建用 `VITE_API_BASE_URL` 绝对地址；dev server 配 `/api` proxy（`loadEnv` 读 `VITE_API_BASE_URL` 作目标，不硬编码）。`VITE_USE_MOCK` 一键在「MSW Mock」与「真实后端」间切换；MSW handler 用 `*/api/...` 通配，同源请求照样命中。
- **理由**：① Mock 同源 -> SW 干净拦截；② 真实联调同源 -> 无跨源，规避「JSON POST 触发 CORS 预检、而预检不被 SW 拦截」的坑，http-proxy 默认流式转发、SSE 可透传，无需后端为联调专开 CORS；③ 基址始终由环境变量驱动，守住「不硬编码」约束。
- **被放弃的方案**：dev 直连跨源 `http://localhost:8080`（需后端配 CORS，预检/SSE 易踩坑）；生产写死后端地址（违反约束）。
- **日期**：2026-09-04

## FD-10. 错误提示集中化 + Query 关闭重试
- **决策**：业务/网络错误统一在 axios 拦截器弹一次 `message`；TanStack Query 全局 `retry:false`、`refetchOnWindowFocus:false`；组件侧只用 `StateBlock` 展示内联错误态与重试按钮，不再重复弹窗。
- **理由**：避免「拦截器 + Query 重试 + 组件 onError」造成的重复弹窗；演示时不被窗口聚焦重取打扰。
- **被放弃的方案**：默认 retry:3（失败会连弹多次）；错误提示分散在各组件（不一致、易漏）。
- **日期**：2026-09-04

## FD-11. RBAC 错误口径 = 401 跳登录 / code=1003 提示无权限不跳登录
- **决策**：按 rest-api.md 通用约定统一处理——未认证（无/失效 token）-> HTTP 401，`handleUnauthorized()` 清登录态 + 跳登录（带回跳地址）；已认证但角色不足 -> HTTP 200 + `code=1003`，拦截器提示「无权限」但不跳登录。axios 拦截器与 SSE(fetch) 共用同一 401 处理（`utils/redirect.ts`）；客户端另有 `RequireRole` 守卫前置拦截（非 ADMIN 不渲染后台页）。
- **理由**：区分「身份失效」（需重新登录）与「权限不足」（身份有效、仅提示），避免把权限问题误踢回登录页；REST/SSE 两条链路行为一致。
- **被放弃的方案**：把 1003 也当 401 跳登录（体验错误）；仅靠客户端守卫、不处理服务端 1003（直连接口时漏处理）。
- **日期**：2026-09-04

## FD-12. SSE 也走 JWT 鉴权 + 401 处理
- **决策**：`/api/qa/chat/stream` 的 fetch 带 `Authorization: Bearer <token>`（取自 authStore/localStorage）；后端无 token 返回 401，前端在 `chatStream` 识别 401 -> `handleUnauthorized()`，与 REST 一致。
- **理由**：统筹明确「所有请求含 SSE 都要 JWT」；SSE 不走 axios，需在 fetch 分支单独补齐鉴权与 401 处理，避免鉴权链路缺口。
- **被放弃的方案**：SSE 不带 token（后端 401、流式失败且无引导）；SSE 401 只在气泡报错不跳登录（身份失效却停留，体验割裂）。
- **日期**：2026-09-04

---

## FD-13. ticket_hint = 后端已自动建单通知（统筹拍板方案①，已实现）
- **决策**：`ticket_hint` 携 data `{conversationId, ticketId, autoCreated:true}`；TICKET 意图 / 无召回兜底时后端**已自动建单**，该事件是「已建单通知」。前端收到后在气泡内展示「查看工单 #{ticketId}」按钮 -> 跳 `/tickets`，**不再 POST /api/ticket**（杜绝重复建单）。`POST /api/ticket` 仅保留给用户**主动**转人工——答案旁的手动「转人工」按钮（仅在无 ticketHint 时展示）。
- **理由**：数据驱动区分「AI 自动建单」与「用户手动建单」，既保留自动建单的便捷，又消除重复建单风险；语义清晰、答辩可讲。
- **被放弃的方案**：方案②（自动建单不推 ticket_hint）——用户无从得知工单已建、体验割裂。
- **落地**：`types/index.ts`(StreamTicketHint)、`api/sse/chatStream.ts`(解析 data)、`stores/chatStore.ts`(ticketHint 存对象)、`hooks/useChatStream.ts`、`components/chat/MessageBubble.tsx`(查看工单 + 手动转人工)、`pages/chat/ChatPage.tsx`(handleViewTicket/handleManualTicket)、`mocks/handlers/qa.ts`(自动建单 + 推 data)。
- **日期**：2026-09-04

## FD-14. 联调实测发现与修复（真实后端 :8080 + Vite proxy）
- **SSE 终止事件必须主动 cancel（已修）**：实测后端/代理在推送 `done` 后不会及时关闭连接，若客户端继续 `reader.read()` 会**永久阻塞**（send() 不 resolve、连接汄漏）。修复：`chatStream` 的 dispatch 对 `done`/`error` 返回「终止」标志，读循环收到即 `await reader.cancel()` 并 return。
- **Vite proxy 流式确认可用**：Node fetch（与浏览器同款 API）实测事件逐个到达（reference→message×N→done，约 2.4s 内），proxy 不缓冲 SSE；此前一次 63s 是 LLM 生成慢的假象。
- **后端无 CORS**：预检 OPTIONS 直返 401 无 ACAO 头，故真实联调**必须走 Vite proxy 同源**（不能直连跨源）。
- **角色枚举校准**：后端实为 `VISITOR/AGENT/ADMIN`（非早期假设的 USER），前端 `Role`/`ROLE_META`/Mock 已全部对齐。
- **done.tokenCost 缺失**：后端当前 done 只发 `{messageId,conversationId}`，无 tokenCost；前端已将其设为可选、缺失时不展示 token 标签（建议后端补发或契约去掉该字段）。
- **时间格式偏差**：后端返回 ISO `yyyy-MM-ddTHH:mm:ss`（带 T），契约写的是 `yyyy-MM-dd HH:mm:ss`；dayjs 两者都能解析、显示不受影响（建议后端对齐契约或契约更新）。
- **日期**：2026-09-04

---

## 待优化 / 后续（非阻塞）
- Dashboard 因 `@ant-design/plots`(G2) 单包约 1.48MB（gzip 440KB），已懒加载隔离；后续可用 `manualChunks` 或按需引入图表类型进一步瘦身。
- 流式渲染目前逐 delta 直接 setState + `React.memo` 气泡；若超长回答出现卡顿，可加 `requestAnimationFrame` 合批。
- 联调阶段建议顺序：认证登录 -> 问答 SSE -> 知识库/工单/反馈/统计（把 `.env` 的 `VITE_USE_MOCK` 置 false 即走 dev proxy 连真实后端）。
