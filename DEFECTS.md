# DEFECTS.md — 项目缺陷清单台账

> **定位**：本文件是项目活文档，与 `DECISIONS.md`/`CONVENTIONS.md`/`contract/rest-api.md` 同级，**入 git**。
> **维护者**：统筹会话（Lead）。
> **登记分工**：
> - 明显的代码开发缺陷、安全漏洞、文档/规范漂移 → Lead 直接登记并排期，无需事先请示。
> - 功能需求、架构取舍、契约变更等决策类问题 → 只登记到第四节「待拍板决策项」标 `⏸`，**由用户拍板后**才转入缺陷区排期。任何专家不得自行处置决策类问题。

---

## 维护规则

- **ID 规则**：缺陷 `DEF-<三位序号>`，历史缺陷 `DEF-H<两位序号>`，决策项 `DEC-PEND-<两位序号>`；**只增不改不复用**；同一缺陷复发时新开 ID 并备注「DEF-xxx 复发」。
- **等级定义**：
  - **P0** = 阻塞交付 / 安全漏洞 / 数据越权或丢失 / 付费额度泄漏，必须当轮修。
  - **P1** = 功能或契约缺陷，影响正确性、演示效果或数据一致性，当轮或下一轮修。
  - **P2** = 文档规范漂移、类型声明不实、加固项、技术债、性能隐患，排期修或明确挂起。
- **状态标记**：`☐ 待修` / `◐ 修复中` / `☑ 已解决(YYYY-MM-DD)` / `⏸ 挂起待拍板` / `⊘ 未排期(技术债)` / `✗ 不修(附理由)`。
- **闭环铁律**：标 `☑` 必须同时填「解决日期」与「验证证据出处」（指向某个 `Mx-report.md` 的第几节或某次实测记录）。**无证据不得打勾**。
- **更新时机**：
  1. 调研或评审发现新缺陷即刻登记；
  2. **任何专家在执行任务时实测发现的新问题都要上报 Lead 续号登记**，不得因为「不在原计划里」就私自修掉而不留痕（DEF-063 即为执行期实测发现的例子）；
  3. 每个任务完成后把对应条目 `◐` → `☑` 并填日期；
  4. 每轮结束在 `Mx-report.md` 附本轮增量摘要（新增 N / 闭环 N / 剩余 N）。
- **等级可升级**：挂起项一旦触发前置条件立即升级并注明原因与日期。例：DEF-030 在启动公网部署（M4-B）时立即升级为 P0 前置阻塞项。

---

## 统计表

| 等级 | ◐ 修复中 | ☑ 已解决 | ⊘ 未排期 | ✗ 不修 | 合计 |
|------|----------|----------|----------|--------|------|
| P0   | 0        | 6        | 0        | 0      | 6    |
| P1   | 0        | 26       | 2        | 0      | 28   |
| P2   | 1        | 38       | 16       | 3      | 58   |
| **合计** | **1** | **70** | **18** | **3** | **92** |

---

## 一、P0 主表（6 条，全部 ☑，A 轮闭环）

| ID | 缺陷 | 类型 | 证据(文件#行) | 影响 | 归属模块 | 状态 | 解决日期 | 验证证据出处 | 处置/关联任务 |
|---|---|---|---|---|---|---|---|---|---|
| DEF-001 | 契约 5 个 `/api/kb` 端点完全未实现（GET/POST `/bases`、DELETE `/documents/{id}`、POST `/{id}/reindex`、GET `/{id}/status`） | 代码缺陷 | `KbDocumentController.java` 仅 4 个 mapping；cs-knowledge 无 @RestController | 前端知识库管理页面全部功能不可用，演示时管理员无法操作知识库 | cs-ingestion | ☑ | 2026-09-04 | M4§5.5-11 | T2 |
| DEF-002 | `/api/kb/**` 全部端点零 RBAC，VISITOR 持 JWT 即可上传文档并触发 Tika 解析 + bge-m3 向量化 + ES 写入 | **安全** | cs-ingestion 全模块 grep `requireAdmin` = 0 | 任何登录用户可篡改知识库内容，数据完整性无保障 | cs-ingestion | ☑ | 2026-09-04 | M4§5.2 | T2 |
| DEF-003 | `GET /api/qa/conversations/{id}/messages` 不返回 references，`chat_reference` 只写不读 | 代码缺陷 | `QaController.java:52` 返回 `R<List<ChatMessage>>` | 刷新页面后引用原文预览全部消失，D10 溯源亮点断裂 | cs-qa | ☑ | 2026-09-04 | M4§5.14 | T3 |
| DEF-004 | `GET /api/health/ai` 被 `/api/health/**` 放行，未认证可调用，每次真实发起 chat + embedding | **安全(额度泄漏)** | `SecurityConfig.java:59`；`AiHealthController.java:39,51` | 任何人无限调用消耗付费 API 额度，造成经济损失 | cs-system + cs-infra-ai | ☑ | 2026-09-04 | M4§5.2,3 | T1 |
| DEF-005 | `DELETE /api/qa/conversations/{id}` 无归属校验，裸 `deleteById` | **安全(越权)** | `QaController.java:58-62`，对比 `:39,:45` 都调了 SecurityUtils | 任何登录用户可删除他人会话，数据越权丢失 | cs-qa | ☑ | 2026-09-04 | M4§5.16 | T3 |
| DEF-006 | `GET /api/qa/conversations/{id}/messages` 无归属校验，任何登录用户可读他人会话全部内容 | **安全(越权)** | `QaController.java:51-55` 未调 SecurityUtils | 会话隐私泄露，违反数据归属原则 | cs-qa | ☑ | 2026-09-04 | M4§5.16 | T3 |

---

## 二、P1 主表（28 条：☑26 + ⊘2）

| ID | 缺陷 | 类型 | 证据(文件#行) | 影响 | 归属模块 | 状态 | 解决日期 | 验证证据出处 | 处置/关联任务 |
|---|---|---|---|---|---|---|---|---|---|
| DEF-007 | SSE `done` 缺 `tokenCost`（契约第 81 行有，实现未发） | 契约偏差 | `QaService.java` done 事件只发 messageId/conversationId | 前端无法展示本次对话 token 消耗，D28 成本统计口径断裂 | cs-qa | ☑ | 2026-09-04 | M4§5.13 | T3 |
| DEF-008 | `chat_message.token_cost` 列与实体字段存在，但全仓无任何 `setTokenCost` 调用 | 代码缺陷 | grep `setTokenCost` = 0 | token 成本从未落库，看板与成本分析无数据支撑 | cs-qa | ☑ | 2026-09-04 | M4§5.13 | T3 |
| DEF-009 | 全局时间格式为 ISO 带 T，契约第 12 行要求空格格式；根因是 JSR-310 对 `spring.jackson.date-format` 无效 | 契约偏差 | `application.yml` 无 JSR-310 序列化配置 | 前端时间显示与契约不一致，虽 dayjs 兼容但口径不统一 | cs-bootstrap | ☑ | 2026-09-04 | M4§5.12+§6.6 | T1 |
| DEF-010 | `GET /api/kb/documents` 返回 `R<List<KbDocument>>` 而非 `R<PageVO<DocumentVO>>`，不接 current/size | 契约偏差 | `KbDocumentController.java:69-73` | 前端分页参数无效，文档表格恒空或一次性全量加载 | cs-ingestion | ☑ | 2026-09-04 | M4§5.8 | T2 |
| DEF-011 | `POST /api/kb/documents/upload` 与 `/url` 返回 `Map<String,Object>` 而非 DocumentVO | 契约偏差 | `KbDocumentController.java:38,47` | 前端类型断言失败，需额外适配非标准返回结构 | cs-ingestion | ☑ | 2026-09-04 | M4§5.7 | T2 |
| DEF-012 | `KbKnowledgeBase.status` 是 Integer(1/0)，契约与前端要 string | 类型冲突 | `KbKnowledgeBase.java:28` `private Integer status` | 前端渲染出绿色的「1」标签而非「启用」，TS 编译期无法捕获 | cs-knowledge | ☑ | 2026-09-04 | M4§5.5+§6.4 | T2 |
| DEF-013 | ConversationVO/MessageVO/DocumentVO/KnowledgeBaseVO 四个契约 VO 后端不存在，用 Entity 或 Map 顶替 | 代码缺陷 + 信息泄露面 | cs-qa/cs-ingestion 无 vo 包 | rewrittenQuery/intent 等 RAG 内部调试数据下发给普通用户 | cs-qa + cs-ingestion | ☑ | 2026-09-04 | M4§5.5,7,8,14 | T2/T3 |
| DEF-014 | DELETE 会话不级联清理 chat_message/chat_reference/Redis conv:memory:{id} | 代码缺陷 | `QaController.java:60` 仅 deleteById；schema 无外键级联 | 孤儿数据永久残留，Redis 内存泄漏 | cs-qa | ☑ | 2026-09-04 | M4§5.17 | T3 |
| DEF-015 | `chat_reference` 无 doc_title/chunk_text 列，saveReferences 丢弃 docTitle 与 content | 数据模型缺陷 | `ChatReference.java` 无该字段；`schema.sql:98-106`；`QaService.java:197-206` | DEF-003 的根因——刷新后引用溯源信息不可恢复 | cs-qa + schema | ☑ | 2026-09-04 | M4§5.14,15 | T1(schema)+T3 |
| DEF-016 | `POST /api/ticket` 返回 TicketVO 只 set 4 个字段，缺 createdAt/reply/handlerId/repliedAt | 契约偏差 | cs-ticket TicketController | 前端工单详情页新建后字段空白，需手动刷新 | cs-ticket | ☑ | 2026-09-04 | M4§5.18 | T4 |
| DEF-017 | Ticket.createdAt 依赖 MySQL DEFAULT CURRENT_TIMESTAMP，insert 后 Java 对象仍为 null | 代码缺陷 | MP 不回读 DB 生成值 | 即使补 set 也拿不到值，前端显示 null | cs-ticket | ☑ | 2026-09-04 | M4§5.18 | T4 |
| DEF-018 | OverviewVO 缺 kbCount/docCount | 功能缺陷 | cs-stats OverviewVO | 看板「知识库/文档」卡恒显示 0/0，答辩现场一眼可见 | cs-stats | ☑ | 2026-09-04 | M4§5.19+§6.5 | T4 |
| DEF-019 | SecurityUtils 保留开发期回退 DEV_FALLBACK_USER{id=1, ADMIN} | 安全隐患 | `SecurityUtils.java:28` | 将来某端点漏配 Security 会静默以 admin 执行，无告警 | cs-framework | ☑ | 2026-09-04 | M4§5.2,3+§4 | T1 |
| DEF-020 | 前端 Login.tsx Alert 写「admin / 任意密码」，真实模式按提示操作必然登录失败 | 代码缺陷 | frontend Login.tsx | 用户按引导操作无法登录，演示体验断裂 | frontend | ☑ | 2026-09-04 | M4§6.1,10 | T6 |
| DEF-021 | 前端 dist 过期（构建时间早于 chatStream.ts 修改时间） | 交付缺陷 | dist/ vs src/ 时间戳 | 直接部署会跑旧代码，缺 SSE 终止阻塞修复 | frontend | ☑ | 2026-09-04 | M4§5门禁build | T5 |
| DEF-022 | 前端 26 个直接依赖全用 ^，21 个已漂移（最大 @tanstack/react-query +43 minor、axios +13 minor） | 工程缺陷 | `package.json` vs `package-lock.json` | axios 漂移已引发泛型签名事故；换机安装可能得到不同版本 | frontend | ☑ | 2026-09-04 | M4§5门禁npm-ls | T5 |
| DEF-023 | FD-8 记录「AntD 5.21 + TS 5.6」与实装 5.29.3/5.9.3 失真 | 文档漂移 | `FRONTEND-DECISIONS.md:53` vs `package.json` | 决策日志与实际运行版本不是一回事，误导后续维护 | frontend | ☑ | 2026-09-04 | M4§5门禁+FD | T5 |
| DEF-024 | Mock handlers/auth.ts:24 密码错误用 5001(USER_NOT_FOUND)，应为 5003 | 代码缺陷 | `mocks/handlers/auth.ts:24` fail(5001,...) | 违反 D22 防枚举设计意图，Mock 与真实行为不一致 | frontend | ☑ | 2026-09-04 | M4§5门禁lint | T6 |
| DEF-025 | Dashboard.tsx:98-100 页脚「当前为 Mock 数据，联调后自动切换真实统计」过时 | 代码缺陷 | Dashboard.tsx | 不存在自动切换机制，文案误导用户认为数据非真实 | frontend | ☑ | 2026-09-04 | M4§6.5 | T6 |
| DEF-063 | 前端质量门禁 `npm run lint` 当前为红：8 problems(7 errors + 1 warning)，含 irregular whitespace、unused var、react-refresh 警告；`--max-warnings 0` 下门禁长期失效 | 工程缺陷 | `npm run lint` 8 problems：MarkdownRenderer.tsx#22:17 no-unused-vars(node，行内disable+理由)；Login.tsx#67 列31/33/49/51 ×4 no-irregular-whitespace(全角空格)；MyTickets.tsx#72 列40/42 ×2 no-irregular-whitespace；router/index.tsx#22:10 react-refresh/only-export-components(warning，拆出 SuspensePage.tsx)。处置：6改普通空格+1带理由disable+1拆文件，未改.eslintrc.cjs；修复后 lint exit0 | 门禁常红等于没有门禁，后续新引入的 lint 问题被预存噪音掩盖，无法通过 lint 判断改动是否干净 | frontend | ☑ | 2026-09-04 | M4§5门禁lint0 | 前端配合改动与文案清理任务(F1-F13)负责修到 exit 0 |
| DEF-026 | 7 处大模型调用全是裸调用 + try-catch，无超时/重试/熔断/降级；ChatClient 在 4 个类各自 build | 架构缺陷 | cs-qa/cs-infra-ai 多处 ChatClient.builder().build() | 单供应商抖动即全链失败，D10 创新点4 未兑现 | cs-qa + cs-infra-ai | ☑ | 2026-09-09 | M5§三(路7)：CircuitBreaker 19/19 断言 + Run A–D 四轮实测；容器内 failover 链就绪日志 | 路7 ChatModelFacade + 手写三态熔断 + failover（D30/D31）；cs.ai.resilience.* 键已落 yml |
| DEF-027 | 全仓自动化测试覆盖为 0：后端无 src/test、前端无 vitest | 工程缺陷 | 项目目录结构 | 任何改动缺回归保护，重构风险不可控 | 全仓 | ⊘ | | | DEC-PEND-04 |
| DEF-028 | JWT 无法主动作废，仅 24h 过期兜底，无 Redis 黑名单、无刷新令牌 | 安全(中) | cs-system JwtService | 令牌泄露后无法立即失效，封号/登出无即时效果 | cs-system | ☑ | 2026-09-09 | M5§三(路8+收口)：登出后同 token me=401；黑名单 TTL=剩余有效期；禁用链路真实数据 401/200/200 实测 | 路8 jti + 双 key（D29）；logout 幂等由 DEF-092 补齐 |
| DEF-029 | 入库 ES/MySQL 非事务一致性：@Transactional 只保 MySQL，回滚后 ES 残留 chunk | 代码缺陷 | IngestionService | ES 残留 chunk 被检索命中导致答案引用不存在的文档 | cs-ingestion | ⊘ | | | 阶段二 RocketMQ 事务消息补偿 |
| DEF-030 | cs.jwt.secret 与 cs.admin.default-password 有弱默认值兜底，忘配 .env 会静默用开发密钥启动 | 安全隐患 | `application.yml:93,97` 含默认值 | 公网部署时若忘配环境变量，任何人可伪造 JWT 或猜中管理员密码 | cs-bootstrap | ☑ | 2026-09-09 | M5§三(路6 B5)：严格×短密钥/弱默认值/容器缺键等 5 情形均拒绝启动，清单只列键名不回显值 | 路6 ConfigValidation（EnvironmentPostProcessor）+ cs.strict-config 双模式 |
| DEF-067 | DESIGN §5.3 把 rate:limit/idem/hot:qa 三 Redis key 标 MVP 且 §6.1 承诺「幂等+限流」，实测零读写代码 | 设计承诺未兑现 | `docs/DESIGN.md` §5.3/§6.1 vs 全仓 grep 三 key=0 | 设计承诺未兑现，MVP 仅落地 conv:memory | 文档 | ☑ | 2026-09-04 | M4§7(用户拍板降级阶段二) | 阶段1修正1 |
| DEF-068 | DESIGN §5.2 ES 索引表把 chunk_id/doc_id 等误列顶层、metadata 描述失真，字段名应为 metadata.doc_title | 文档错误(可致真实bug) | `docs/DESIGN.md` §5.2 vs `es-chunk-mapping.json` | 照原表写 term(doc_id) 会静默删不掉 chunk | 文档 | ☑ | 2026-09-04 | M4§7(阶段1修正5) | 阶段1修正5 |
| DEF-086 | 统筹给出的 `env \| grep` 排查命令导致 AI_DASHSCOPE_API_KEY 与 SILICONFLOW_API_KEY 明文回显，进入对话记录与 VM shell 历史 | 流程缺陷（统筹自身） | VM 排查命令设计 | 密钥泄露，用户已轮换两个密钥 | 流程 | ☑ | 2026-09-09 | M5§四：替代写法 `env \| cut -d= -f1` 只取键名，已固化进 phase2-common 与收口工作单硬约束 | 收口固化纪律 |

---

## 三、P2 主表（57 条：☑38 + ⊘15 + ✗3 + ◐1）

| ID | 缺陷 | 类型 | 证据(文件#行) | 影响 | 归属模块 | 状态 | 解决日期 | 验证证据出处 | 处置/关联任务 |
|---|---|---|---|---|---|---|---|---|---|
| DEF-031 | 前端 StatsOverview 缺 openTicketCount，后端已发但前端丢弃 | 类型缺口 | frontend types | 看板「待处理工单」数据无法展示 | frontend | ☑ | 2026-09-04 | M4§6.5 | T6 |
| DEF-032 | 前端 resolveRate/ticketRate 声明必填 number，后端按 D24 可返回 null | 类型缺陷 | frontend types | 类型在撒谎——运行期 null 导致 NaN 或 UI 异常 | frontend | ☑ | 2026-09-04 | M4§6.5+tsc | T6 |
| DEF-033 | 前端 api/types.ts:8 的 R.timestamp 声明 string，后端 R.java:28 是 long | 类型缺陷 | `api/types.ts:8` vs `R.java:28` | 类型不一致虽前端零读取暂无运行期影响，但声明不实 | frontend | ☑ | 2026-09-04 | M4§5门禁tsc | T6 |
| DEF-034 | 前端 types/index.ts:50 注释 sourceType 为「FILE / URL」，后端实际是 UPLOAD / URL | 注释错误 | `types/index.ts:50` | 误导开发者按 FILE 做条件判断 | frontend | ☑ | 2026-09-04 | M4§5门禁tsc | T6 |
| DEF-035 | 前端 types/index.ts:110 注释「后端当前不含 tokenCost」在 DEF-007 修复后过时 | 注释过时 | `types/index.ts:110` | DEF-007 修复后注释与事实矛盾 | frontend | ☑ | 2026-09-04 | M4§5门禁tsc | T6 |
| DEF-036 | Mock handlers/kb.ts:24 用 2001(KB_NOT_FOUND) 表达「名称不能为空」，应为 1001(PARAM_ERROR) | 代码缺陷 | `mocks/handlers/kb.ts:24` fail(2001,...) | Mock 与后端错误码语义不一致，前端错误处理逻辑被误导 | frontend | ☑ | 2026-09-04 | M4§5门禁lint | T6 |
| DEF-037 | mocks/handlers/system.ts:5 注释称端点「未冻结」，实际 FD-7 已确认写入契约 | 注释陈旧 | `mocks/handlers/system.ts:5` | 自相矛盾，误导后续开发者认为该端点仍是提案 | frontend | ☑ | 2026-09-04 | M4§5门禁lint | T6 |
| DEF-038 | CONVENTIONS.md §2 依赖表未同步 cs-qa→cs-ticket | 规范漂移 | CONVENTIONS.md §2 | 属既存 §11.2 违规，新专家按表开发会遗漏合法依赖 | 文档 | ☑ | 2026-09-04 | M4§7 | T7 |
| DEF-039 | 「Mx-report 6 段结构」在 CONVENTIONS 里不存在，只散落在集成报告与 path3-m3.md | 规范缺失 | CONVENTIONS.md 无该章节 | 实际报告段数不一(6/6/7/8)，新阶段无统一模板可循 | 文档 | ☑ | 2026-09-04 | M4§7 | T7 |
| DEF-040 | CONVENTIONS.md §4「Controller 只返回 R<T>」未写明 SSE 返回 SseEmitter 是合理例外 | 规范缺失 | CONVENTIONS.md §4 | 严格执行者会误判 QaController.chatStream 违规 | 文档 | ☑ | 2026-09-04 | M4§7 | T7 |
| DEF-041 | 契约与 path3-m3.md 放行清单只列 3 项，实际 SecurityConfig.java:60 还放行 /error | 契约漂移 | `SecurityConfig.java:60`；contract/rest-api.md | 契约文本与实际配置不一致，审计时遗漏 | 文档 + cs-system | ☑ | 2026-09-04 | M4§5.4+contract§8 | T7 |
| DEF-042 | 契约第 60 行 OverviewVO 用省略号 `...`，该 VO 实际从未被冻结 | 契约缺陷 | contract/rest-api.md:60 | 前后端各自猜测字段，看板对接缺乏权威依据 | 文档 | ☑ | 2026-09-04 | M4§5.19 | T7 |
| DEF-043 | 契约 VO 速查缺 DocStatusVO 命名条目，前端已自行命名 | 契约缺陷 | contract/rest-api.md VO 速查节 | 前后端命名可能分裂，增加对接沟通成本 | 文档 | ☑ | 2026-09-04 | M4§5.9 | T7 |
| DEF-044 | POST /api/kb/search 是 M1 验收调试端点，混入生产 API 面、契约未收录、无 RBAC、直接返回内部 RetrievedChunk | 契约 + 信息泄露面 | `KbDocumentController.java:55-66` | 暴露内部 metadata Map，未授权用户可探测知识库内容 | cs-ingestion | ☑ | 2026-09-04 | M4§5.2,10 | T2/T7 |
| DEF-045 | 契约字面「无权限 403」与实现「200 + code=1003」口径差，D23 标「待确认」长期悬置 | 口径差 | contract/rest-api.md；M3-report 决策3 | 前后端对 RBAC 拒绝响应处理不一致 | 文档 + cs-system | ☑ | 2026-09-04 | M4§5.2,16 | T7 在 D23 追加确认结论 |
| DEF-046 | audit_log 表建而零代码引用(grep=0)，但 DESIGN §5.1 列为 MVP 项 | 未完成功能 | schema.sql audit_log；grep 全仓 = 0 | 设计承诺未兑现，审计能力空缺 | cs-system | ⊘ | | | DEC-PEND-06 |
| DEF-047 | sys_role/sys_permission 建表未启用关联映射，SysUser 用单 role 字段过渡 | 未完成功能 | schema.sql:24-38；SysUser entity | RBAC 停留在最简模型，阶段二升级需补关联 | cs-system | ⊘ | | | DEC-PEND-06；阶段二 |
| DEF-048 | stat_snapshot 只写不读：SnapshotTask 每日 00:05 幂等写入但无查询端点 | 待落地 | cs-stats SnapshotTask | 每日写入零消费，数据堆积无业务价值 | cs-stats | ⊘ | | | DEC-PEND-07 |
| DEF-049 | RetrievalRequest.kbId 与 minScore 声明但从未使用 | 代码冗余 | `RetrievalRequest.java` | 字段存在给调用方造成「可过滤」的错觉 | cs-knowledge | ⊘ | | | 阶段二多库路由时启用 |
| DEF-050 | QaService.chatStream 用默认 ForkJoinPool.commonPool，无专用线程池 | 性能隐患 | QaService.java | 高并发下 SSE 流与其他异步任务竞争线程，响应劣化 | cs-qa | ⊘ | | | 阶段二 |
| DEF-051 | ES client 版本隐式锁定：根 pom 无 elasticsearch.version，升 Spring AI 会静默错配 | 配置隐患 | 根 pom.xml；`deploy/elasticsearch/Dockerfile:7` | Spring AI 升级后 client/server 版本分裂，运行期才暴露 | 根 pom + deploy | ⊘ | | | DEC-PEND-08 |
| DEF-052 | docker-compose.yml 只有中间件三件套，缺 backend/frontend 编排 | 部署缺口 | docker-compose.yml | 换机部署必须人工记得跑 init-index.sh，不可复现 | deploy | ☑ | 2026-09-09 | M5§三(路6 B1)：全栈五服务 healthy + es-init 幂等建索引 + 27 端点经 8088 全回归 | 路6 全栈编排（name 钉死防卷孤立，D35） |
| DEF-053 | 无任何 CI 配置 | 工程缺口 | .github/workflows 等均不存在 | 代码合并无自动化门禁，质量全靠人工 | 全仓 | ☑ | 2026-09-09 | M5§三(路6)：.github/workflows/ci.yml 交付（后端 package + 前端 tsc/build/lint，零密钥） | 路6 |
| DEF-054 | 后端全仓无 CORS 配置，生产缺 nginx 同源反代方案 | 部署约束 | grep CORS = 0 | 生产部署时前端跨域请求全部失败 | cs-bootstrap | ☑ | 2026-09-09 | M5§三(路6)：nginx 同源反代——27 端点经 8088 全绿、SSE 40+ 事件跨 5.5s 逐包到达、产物 grep localhost:8080 命中 0 | 路6 deploy/nginx/nginx.conf |
| DEF-055 | build.ps1 的 .env 解析器不支持引号包裹值、行尾注释、多行值、export 前缀 | 工具局限 | build.ps1 -split '=',2 | 特定格式的 .env 值会被截断或解析错误 | 工具链 | ⊘ | | | 低优先；写 .env 时避开 |
| DEF-056 | 前端仓库内无 .npmrc，镜像源只在用户级配置 | 工程隐患 | frontend/ 无 .npmrc | 换机/CI 上 npm install 回落 npmjs.org 超时，lock resolved 不一致 | frontend | ☑ | 2026-09-04 | M4§5门禁npm-ls+.npmrc | T5 |
| DEF-057 | frontend/.env 被 gitignore，换机回落 .env.example 的 VITE_USE_MOCK=true | 环境陷阱 | frontend/.gitignore + .env.example | 静默回到 Mock 模式，开发者误以为在联调真实后端 | frontend | ☑ | 2026-09-04 | M4§6.1,10+.env.example | T6 (F13) |
| DEF-058 | 流式渲染逐 delta setState，超长回答可能卡顿 | 性能 | frontend chatStore/useChatStream | 超长回答(>2000字)时 UI 掉帧，体验劣化 | frontend | ⊘ | | | 非阻塞；建议 rAF 合批 |
| DEF-059 | @ant-design/plots 单包约 1.48MB(gzip 440KB) | 性能 | frontend bundle | 首屏加载慢（已懒加载隔离，影响有限） | frontend | ⊘ | | | 建议 manualChunks 瘦身 |
| DEF-060 | CHITCHAT 意图不检索但仍走完整生成链路 | 功能未落地 | QaService CHITCHAT 分支 | 闲聊回复慢且浪费 token（应走轻量回复） | cs-qa | ⊘ | | | 阶段二 M2 可增强#2 |
| DEF-061 | 启动日志 "Using generated security password" WARN | 日志噪音 | M3-report §5 #4 | 无害但干扰开发体验，新人可能误判为安全问题 | cs-bootstrap | ⊘ | | | 阶段二排除 UserDetailsServiceAutoConfiguration |
| DEF-062 | 前端 hooks/useKb.ts:85-95 的 useDocStatus 无任何消费方 | 死代码 | `hooks/useKb.ts:85-95` | noUnusedLocals 不检查导出符号，不会导致 tsc 失败 | frontend | ✗ | | | **不修**：保留作为阶段二入库 Pipeline 细粒度进度预留能力 |
| DEF-064 | 入库为同步事务：ingest 置 PROCESSING 同事务末尾置 DONE，PENDING/PROCESSING 对外不可观测，FAILED 仅 reindex 可达 | 设计缺口 | IngestionService ingest/reindex | 契约「入库进度轮询」与前端3秒轮询实际无进度可轮 | cs-ingestion | ⊘ | | | 阶段二入库Pipeline异步化(D10)；本轮契约已标注可达性 |
| DEF-065 | cs-qa 残留 DEFAULT_USER_ID=1L 静默回退与「MVP 暂用默认用户」注释 | 代码缺陷+过时注释 | QaService DEFAULT_USER_ID | 将来传 null 会静默归属 id=1(管理员)，违反 D17 | cs-qa | ☑ | 2026-09-04 | M4§5(cs-qa compile+grep无回退) | 已删常量+Javadoc固化非null契约 |
| DEF-066 | 全仓 VO 映射风格不统一：UserVO/新6VO 用 static from()，TicketVO 走 toVO() 实例方法 | 代码风格 | UserVO vs TicketService.toVO | 仅风格差异，两者都正确 | 全仓 | ⊘ | | | 可另开纯 refactor 归一 |
| DEF-069 | DESIGN §5.2 称用 ES retriever rrf 融合，与 M1 手动 RRF(k=60) 决策矛盾 | 文档矛盾 | `docs/DESIGN.md` §5.2 vs M1-report 决策2 | DESIGN 停留早期设计 | 文档 | ☑ | 2026-09-04 | M4§7(阶段1修正6) | 阶段1修正6 |
| DEF-070 | ticketRate 与 resolveRate 零样本口径不对称(askCount=0 返 0.0 vs null) | 代码缺陷 | StatsServiceImpl.overview | 空库看板同卡两种口径 | cs-stats | ☑ | 2026-09-04 | M4§5.19(ticketRate null) | 阶段1修正8 |
| DEF-071 | 契约 VO 速查缺 HotQuestionVO/TrendVO/LoginVO 三命名条目 | 契约缺陷 | contract/rest-api.md VO 速查 | 类名从未在契约出现 | 文档 | ☑ | 2026-09-04 | M4§7(阶段1修正3) | 阶段1修正3 |
| DEF-072 | contract 第3节模块归属写「cs-knowledge/cs-ingestion」失真(9端点全在cs-ingestion) | 文档漂移 | contract/rest-api.md §3 | 模块归属误导 | 文档 | ☑ | 2026-09-04 | M4§7(阶段1修正4) | 阶段1修正4 |
| DEF-073 | 契约 UserVO.role 未列枚举取值(ADMIN/AGENT/VISITOR) | 契约缺陷 | contract/rest-api.md UserVO | 角色取值无权威依据 | 文档 | ☑ | 2026-09-04 | M4§7(阶段1修正3) | 阶段1修正3 |
| DEF-074 | seed.ts 仍用 sourceType:'FILE' 与小写 fileType，违反冻结枚举 | 代码缺陷 | `mocks/data/seed.ts` | MSW 模式数据与真后端不一致 | frontend | ☑ | 2026-09-04 | M4§5门禁tsc/lint(seed.ts) | 阶段1修正7 |
| DEF-075 | JacksonConfig Javadoc「15个时间字段」不精确(全仓22处，业务实体侧15处) | 注释口径不精确 | JacksonConfig Javadoc | 字段数口径误导 | cs-bootstrap | ☑ | 2026-09-04 | M4§7(D27字段数校正) | D27 留痕 |
| DEF-076 | M1-report 缺「踩坑」段，四份报告段数段序不统一 | 规范执行不一致 | M0~M3-report 段序 | 历史报告不统一 | 文档 | ✗ | | | **不修**：历史报告不回改；CONVENTIONS§13 历史现状已记录 |
| DEF-077 | F1：SSE KB路径事件序 reference→message→done 与契约 L95-102(message→reference→done) 不符 | 文档漂移 | QaService L121 检索后推reference / L151 生成推message | 功能无害(前端按事件名消费)；契约文本与实现不一致 | 文档+cs-qa | ⊘ | | | 建议下轮改契约对齐实现；M4§5.13/§6.2 实测留证 |
| DEF-078 | F3：SSE 完成后 async dispatch 抛 AuthorizationDeniedException「Access Denied」+response already committed 3条ERROR | 日志噪音 | Spring Security6 未放行 DispatcherType.ASYNC | 功能无害(客户端已收全流)；ERROR 日志噪音 | cs-system | ☑ | 2026-09-09 | M5§三(路8)：放行 ASYNC 后 SSE 完成日志零 AccessDenied（改动前 M4 为 3 条 ERROR） | 路8 SecurityConfig 放行（含安全性论证注释） |
| DEF-079 | 项目无「禁用用户」管理端点，sys_user.status 仅在登录处被消费，路8 实现的 AuthService.invalidateUser(userId) 当前无任何调用方 | 功能缺口 | AuthService.invalidateUser 全仓 grep 无调用方；sys_user.status 仅登录路径消费 | 管理员无法封号；路8 的「用户禁用即时失效」能力有实现无入口，只能靠 redis-cli 手工写时间戳验证 | cs-system | ⊘ | | | 归管理端增强批次（与 DEF-047 RBAC 升级同期）；路8 于 2026-09-05 上报，统筹 2026-09-09 补录（原收口工作单漏列此 ID 致悬空引用，同类问题 A 轮 DEF-065 已发生过一次） |
| DEF-080 | QueryRewriteService 未校验模型输出有效性，「-」等垃圾被当作有效重写污染检索 | 代码缺陷 | QueryRewriteService.rewrite | 垃圾重写劣化检索质量 | cs-qa | ☑ | 2026-09-09 | M5§三(路7 补刀1)：isValidRewrite 三条判定，13 项断言 PASS | 路7 补刀1 |
| DEF-081 | 客户端断开后 Flux 仍继续生成至完毕（token 白烧） | 资源浪费 | QaService chatStream | SSE 取消后模型调用未中止 | cs-qa | ⊘ | | | 挂路10（与 C5 限流/记忆摘要一并处理） |
| DEF-082 | saveAssistantMessage/saveUserMessage 无 content 守卫，null 撞 NOT NULL 约束且被宽 catch 统一报成 3002 掩盖真因 | 代码缺陷 | QaService 落库路径 | 排障方向被误导（容器实测实际发生） | cs-qa | ☑ | 2026-09-09 | M5§三(路7 补刀2)：入口守卫 + resolveErrorCode 按异常类型分派，16/16 断言 PASS | 路7 补刀2（D32） |
| DEF-083 | SSE 经 nginx 的逐包穿透未实测（proxy_buffering off 是否生效无证据） | 部署验证缺口 | deploy/nginx/nginx.conf | 若攒包则流式打字机效果完全失效 | deploy | ☑ | 2026-09-09 | M5§三(路6)：SSE 40+ 事件跨 5.5s 逐包到达（逐行时间戳留证） | 路6 实测闭环 |
| DEF-084 | compose 项目名随目录变化，阶段一数据卷被静默孤立（aibishe_* 与 telecom-cs-rag_* 两套卷并存） | 部署缺陷 | docker-compose.yml 缺顶层 name | 数据「消失」假象，实为旧卷不再挂载 | deploy | ☑ | 2026-09-09 | M5§三(路6)：yml 钉死 name: telecom-cs-rag 后旧卷回归（24 会话、doc1 三 chunks、两个旧知识库） | 路6 修复（D35） |
| DEF-085 | 未匹配的 /api/** 路径穿透静态资源处理器抛 NoResourceFoundException，被兜底成 1999「系统繁忙」 | 代码缺陷 | GlobalExceptionHandler 无该异常分支 | 客户端 URL 写错被报成服务端内部错误，误导排障（路6 容器实测实际发生） | cs-framework | ☑ | 2026-09-09 | M5§三(收口)：新增分支返回 1004 + warn 日志，实测 HTTP 200 {"code":1004} | 收口修复 |
| DEF-087 | 容器内出网到模型供应商间歇性 Connection reset（attempt=1/3 失败后重试成功） | 环境 | 收口实测：容器 MTU 1400=预置生效值、宿主 1500，Docker 默认 bridge MTU 本会继承宿主（原本一致）；reset 后重试即成 | 偶发一次重试开销，已被路7 重试机制消化 | 环境 | ✗ | | | **不修**：非本项目缺陷（供应商侧间歇抖动，D30 重试已消化）；MTU 预置属无依据推测性改动已回退，回退后 SSE 逐包复测无退化 |
| DEF-088 | ChatRequest.question 缺 DTO 层校验；question=null 时 NPE→3002「系统繁忙」，排障成本被显著放大（路6 与路7 从不同方向独立发现） | 代码缺陷 | QaService/QaController | 参数错误与系统错误不可区分 | cs-qa | ☑ | 2026-09-09 | M5§三(收口)：服务层守卫 SSE error 1001 + 会话数 19→19 零落库实测；DTO @Valid 停手上报（SSE 端点返回 HTTP 200+JSON 会被前端静默吞掉，待统筹拍板，见 D32） | 路7 服务层守卫运行时闭环；**统筹 2026-09-09 裁决选项①：仅保留服务层守卫、不加 DTO 层 @Valid**——SSE 的错误通道是事件流而非 HTTP 响应体，@Valid 失败返回的 JSON 会被前端 chatStream.ts 静默吞掉；定制 SSE 感知的校验失败处理器复杂度高收益低；改前端是为后端的错误设计买单。**该裁决上升为架构口径**：SSE 端点的参数校验一律在服务层做并以 SSE error 事件出口，待写入 CONVENTIONS §4 的 SSE 例外段 |
| DEF-089 | 路7 入口守卫使 done.messageId 可能为 null，A 轮契约与前端类型声明必填——类型在撒谎 | 契约与类型不一致 | contract SSE 段；types/index.ts StreamDoneInfo | 运行期安全（?? tempId 防御+点赞灰化）但类型不实 | contract + frontend | ☑ | 2026-09-09 | M5§三(收口)：契约补口径 + StreamDoneInfo.messageId: number\|null，前端 tsc/build/lint 三门禁 exit0 | 收口修复 |
| DEF-090 | 历史工单 ticket3~8 为 9 月 4–6 日 GBK 乱码数据（PowerShell 内联中文传参遗留），答辩演示观感差 | 数据卫生 | ticket 表 | 管理后台工单列表出现乱码 | 数据 | ☑ | 2026-09-09 | M5§三(收口)：清理乱码与三路测试数据，p9- 前缀重建演示数据（kb4/doc6/conv34/ticket10 REPLIED/resolveRate=0.5） | 收口清理+重建 |
| DEF-091 | 启动包/部署手册 SSE 示例把请求字段写成 message，契约实际是 question，后续路会抄示例 | 文档勘误 | docs/reports/deploy-guide.md §8 示例；统筹派单消息 | 按示例构造请求全部 3002，浪费整轮排障 | 文档 | ☑ | 2026-09-09 | M5§三(收口)：deploy-guide 示例已改 question；verify-integration.ps1 本就正确 | 收口修正；启动包示例位置已列回执供统筹知会 |
| DEF-092 | logout 二次调用返回 1002 而非契约承诺的幂等 code=0（已拉黑 token 被过滤器拦截，到不了 Controller） | 代码缺陷 | SecurityConfig 放行清单无 logout | 登出后前端再调 logout 会得到错误提示 | cs-system | ◐ | | | 收口已修复（SecurityConfig 精确放行 + 安全性论证注释），cs-server.jar 已重新构建；**部署复验待下次部署**（用户裁定跳过本轮部署验证） |

---

## 四、待拍板决策项（8 条，状态 ⏸）

| ID | 决策事项 | 背景与来源 | 候选选项 | 状态 |
|---|---|---|---|---|
| DEC-PEND-01 | 毕设题目是否采用《基于 RAG 的电信运营商智能客服系统的设计与实现》 | DECISIONS.md 末尾待拍板第 1 项 | 采用 / 修改措辞 | ⏸ |
| DEC-PEND-02 | 毕设约束（验收侧重、云端 API 限制、开题-中期-答辩时间线、论文格式）待导师学院确定后回填 | DECISIONS.md 末尾待拍板第 2 项 | 直接影响优先做「论文数据」还是「简历工程」，有消息需尽快同步 | ⏸ |
| DEC-PEND-03 | 阶段二优先级排序 | DEF-026 模型熔断降级、RAG 评估体系、ReAct Agent+MCP、树形意图、检索增强 | 调研建议：熔断降级 → RAG 评估 → Agent | ⏸ |
| DEC-PEND-04 | 是否补关键路径自动化测试（DEF-027），技术选型 | 0 测试覆盖下任何改动都缺回归保护 | JUnit5+Mockito / @SpringBootTest+Testcontainers / 只补 PS 断言脚本 | ⏸ |
| DEC-PEND-05 | 容器化部署（M4-B）何时启动 | 用户本轮明确跳过，但 DEF-030/052/053/054 全挂在它上面，且 D14 承诺「本地 Compose + 云学生机公网可访问」是简历刚需 | 本轮后立刻做 / 秋招投递前(2026-11)做 / 答辩前(2027-06)做 | ⏸ |
| DEC-PEND-06 | audit_log(DEF-046) 与 sys_role/sys_permission 关联映射(DEF-047)：阶段二实现还是降级移除 | 两张表建而无任何代码引用，属「设计承诺未兑现」 | 阶段二实现 / 降级移除 / 保留建表但在 DESIGN 标注为预留 | ⏸ |
| DEC-PEND-07 | stat_snapshot(DEF-048) 的落地路径 | SnapshotTask 每日 00:05 幂等写入但零消费，看板全走实时聚合 SQL | 阶段二 RAG 评估体系复用其时序数据 / 新增端点做同环比图表 / 暂停写入 | ⏸ |
| DEC-PEND-08 | 是否在根 pom 显式声明 elasticsearch.version 属性以消除 DEF-051 的隐式锁定 | 显式声明属依赖管理变更，触碰 D20 版本锁定口径 | 显式声明并写入 D20 附注 / 保持隐式但在 DECISIONS 记录风险 | ⏸ |

---

## 五、已闭环决策存档（2026-09-04 拍板）

| 决策事项 | 结论 | 影响的缺陷 |
|---|---|---|
| SSE done 缺 tokenCost / 时间格式 ISO vs 空格 / messages 不返回 references | **后端补齐对齐契约**，契约正文这三处定义不动 | DEF-007、DEF-009、DEF-003 |
| 两个契约外端点（/api/kb/search、/api/health/ai） | **收进契约 + 加防护** | DEF-004、DEF-044 |
| 前端依赖版本 | **新增 D25**，26 个包锁硬版本 | DEF-022、DEF-023、DEF-056 |
| RBAC 拒绝的 HTTP 状态码口径 | 以 **HTTP 200 + code=1003** 为准，403 仅作过滤器链兜底；D23「待确认」正式关闭 | DEF-045 |
| 容器化部署（M4-B） | 本轮**跳过** | DEF-052、DEF-053、DEF-054 挂起 |
| DEF-067 三个 Redis key（rate:limit/idem/hot:qa）的 MVP 承诺 | **降级为阶段二**，同步修正 DESIGN §5.3/§6.1 标注 | DEF-067 |
| 阶段1 八项文档修正（DESIGN §5.2/5.3/6.1、contract 四项、seed.ts、ticketRate 口径） | **全量授权**直接修 | DEF-068~075、DEF-070、DEF-074 |
| 缺陷清单载体 | 根目录 DEFECTS.md，**入 git**；全量收录；决策类独立一节 | — |

---

## 六、附录：阶段一已闭环缺陷（历史）

> 以下为阶段一 M0–M3 + 集成接线 + 前端联调期间遇到并已解决的踩坑，全部 ☑。
> 来源：`docs/reports/phase1-integration-report.md` §6、`M0-report.md` §4、`M2-report.md` §5、`M3-report.md` §5、`frontend/FRONTEND-DECISIONS.md` FD-5/FD-14。

| ID | 坑 | 根因 | 解决 | 闭环里程碑 | 日期 |
|---|---|---|---|---|---|
| DEF-H01 | Spring AI 版本错配 | 1.0.x 对应 Boot 3.4，3.5 要 1.1.x | 校准 Boot 3.5.16 + Spring AI 1.1.2（D20） | M0 | 2026-09-03 |
| DEF-H02 | dashscope starter 版本缺失 | Alibaba 双 BOM：核心管编排、扩展管模型 starter | 同时 import 两个 BOM | M0 | 2026-09-03 |
| DEF-H03 | ES client/server 不匹配 | Spring AI 传递 client 8.18.8 | server 对齐 8.18.8 | M0 | 2026-09-03 |
| DEF-H04 | CentOS7 跑不了 ES8 | glibc 2.17 / 内核 3.10（ES8 要 glibc>=2.28） | 换 Ubuntu 24.04 VM | M0 | 2026-09-03 |
| DEF-H05 | Docker 拉不动镜像 | 国内连 Docker Hub 被拒 | 配镜像加速器 + ES 基础镜像改走 Docker Hub | M0 | 2026-09-03 |
| DEF-H06 | IK 切碎「畅享」 | IK 默认词典无电信术语 | 阶段二加自定义词典（已识别，非阻塞） | M0 | 2026-09-03 |
| DEF-H07 | 单模块 spring-boot:run 找不到依赖 | 单模块 run 从 .m2 找 jar，但没 install 过 | 先 `build.ps1 clean install -DskipTests` | M0 | 2026-09-03 |
| DEF-H08 | .env 没生效 | config.import 相对路径受工作目录影响 | build.ps1 启动前加载 .env 为进程环境变量 | M0 | 2026-09-03 |
| DEF-H09 | Spring AI 自动建索引用 standard 分词器 | 中文被逐字切分，BM25 质量极差 | 禁用 initialize-schema，改用 deploy/elasticsearch/ 手动建带 IK 的索引 | M1 | 2026-09-04 |
| DEF-H10 | ticket_hint 事件在 SseEmitter.complete() 后发不出 | emitter 关闭后再 send 静默丢弃 | 把 ticket_hint 移到 done 之前发送 | M2 | 2026-09-04 |
| DEF-H11 | Redis 端口漂移 | compose 文件写 6379 vs 运行容器映射 16379 | 统一 16379（文件=运行=后端配置） | M2 | 2026-09-04 |
| DEF-H12 | POST+SSE 不能用 EventSource | EventSource 只支持 GET 且无法携带请求体 | 改用 fetch + ReadableStream 手写 SSE 解析 | 前端 M2 | 2026-09-04 |
| DEF-H13 | axios 1.20 泛型签名变更 | client.get<any,T> 双泛型返回被包成 AxiosResponseResult | 改用 `as unknown as Promise<T>` 显式对齐 | 前端 M2 | 2026-09-04 |
| DEF-H14 | MyBatis-Plus 3.5.9 分页插件缺失构件 | PaginationInnerInterceptor 拆到 mybatis-plus-jsqlparser 且需注册拦截器 | 根 pom 加构件 + MybatisPlusConfig 注册 Bean | M3 | 2026-09-04 |
| DEF-H15 | PS 沙箱 Get-NetTCPConnection 静默失败 | 沙箱环境不可用，返回空误判端口空闲 | 改用 HTTP 探测 + netstat 找 PID | M3 | 2026-09-04 |
| DEF-H16 | 业务错误 HTTP 200 vs 测试脚本预期 4xx | 契约统一 HTTP 200 + code，Invoke-RestMethod 不抛异常 | 改为断言响应体 code 字段 | M3 | 2026-09-04 |
| DEF-H17 | PowerShell 5.1 按 GBK 解析 UTF-8 无 BOM 脚本 | 中文字面量乱码导致工单 question 乱码 | 脚本存 UTF-8 BOM 或用 pwsh | M3 | 2026-09-04 |
| DEF-H18 | 启动日志 "Using generated security password" | Boot 自动配置内存 UserDetailsService | 确认无害（自定义 SecurityFilterChain 不启用 formLogin）；阶段二可排除 | M3 | 2026-09-04 |
| DEF-H19 | ticket_hint 语义歧义导致重复建单 | 契约「提示建单」vs 实现「自动建单」语义漂移 | 方案①：hint 带 ticketId + autoCreated:true，前端查看跳转不重建 | 集成接线 | 2026-09-04 |
| DEF-H20 | SSE 收到 done 后 reader.read() 永久阻塞 | 后端/代理推送完终止事件后不及时关闭连接 | dispatch 对 done/error 返回终止标志，读循环命中即 reader.cancel() | 前端联调 | 2026-09-04 |
| DEF-H21 | 前端角色枚举误写 USER | 后端实为 VISITOR/AGENT/ADMIN，早期假设错误 | 前端 Role/ROLE_META/Mock 全部校准对齐 | 前端联调 | 2026-09-04 |
| DEF-H22 | 后端无 CORS，前端直连跨源失败 | 预检 OPTIONS 返回 401 无 ACAO 头 | 真实联调必须走 Vite proxy 同源；生产走 nginx 同源反代 | 前端联调 | 2026-09-04 |
| DEF-H23 | Vite 配置文件不在 tsconfig include 内时，复杂回调触发 IDE 解析报错 | vite.config.ts 不在 tsconfig include | 简化 proxy configure 钩子为仅 changeOrigin | 前端 M2 | 2026-09-04（补录；来源=项目记忆系统 common_pitfalls_experience，phase1-integration-report 与 M2-report 均无文字记录） |
