# DECISIONS.md — 技术决策日志

> 本文件记录《基于 RAG 的电信运营商智能客服系统》（毕业设计 + 秋招简历核心项目）的全部关键技术决策。
> 条目格式：**决策 / 理由 / 被放弃的方案 / 日期**。
> 每次拍板后追加。本文件是答辩与面试中"讲清每一个技术决策"的第一手依据。

---

## D1. 业务场景 = 电信运营商客服
- **决策**：系统面向电信运营商客服场景，覆盖套餐资费咨询、宽带报障指引、账单规则解释等业务。
- **理由**：贴合本人两段实习背景（亿迅 = 中国电信、浩鲸 = 运营商 BSS 智能体平台），求职叙事连贯；"转人工工单"是运营商客服的真实形态；语料可用公开资费/服务文档构造，不涉密。
- **被放弃的方案**：企业 IT 服务台（真实语料涉密需虚构）、高校校园客服（撞题率高、与实习背景关联弱）、电商/SaaS 客服（语料需完全自造）。
- **日期**：2026-09-03

## D2. 对 Ragent 采取"架构参考、代码独立实现"
- **决策**：借鉴本地开源项目 Ragent（nageoffer，Spring Boot + React 的生产级 Agentic RAG 平台）的分层思想、检索漏斗、SSE 事件设计等，代码全部独立编写。
- **理由**：规避毕业设计查重与知识产权风险；保证每一处实现都能讲清楚；Ragent 已验证的架构能确保系统达到企业级标准。
- **被放弃的方案**：直接 fork 二开（查重风险高、6 万行代码讲不清、面试被识破严重减分）、混合抄用（边界模糊仍有风险）。
- **日期**：2026-09-03

## D3. 语料来源 = 公开资料为主 + LLM 生成长尾 FAQ 补充
- **决策**：核心政策/资费文档采用公开真实资料，长尾 FAQ 用 LLM 批量生成扩充。
- **理由**：兼顾真实性与工作量；答辩被问"语料真实性"时，核心文档有据可查。
- **被放弃的方案**：纯公开收集（长尾覆盖不足、整理工作量大）、纯 LLM 生成（真实性存疑）。
- **日期**：2026-09-03

## D4. 文档解析支持"全家桶"
- **决策**：入库解析支持 Markdown/TXT、PDF（含表格）、Word、Excel（FAQ）、网页 URL 抓取。
- **理由**：企业级文档处理是 RAG 工程化的高频考点，面试与论文均有内容可写。
- **被放弃的方案**：仅 MD/TXT（丢失"文档处理"亮点）、仅 MD+PDF（覆盖不足）。
- **日期**：2026-09-03

## D5. 语料规模 = 中型，且确认无涉密
- **决策**：目标规模为数百篇文档、数千 chunk；全部使用公开/虚构数据，无涉密内容。
- **理由**：中型规模足以谈检索性能与调优；无涉密 → 可直接使用云端 LLM API，不受数据出域限制。
- **被放弃的方案**：小型规模（几十篇，撑不起性能话题）。
- **日期**：2026-09-03

## D6. 两阶段推进（秋招 MVP → 毕设终态）
- **决策**：阶段一在秋招前（约 8 周，2026-11 前）交付可演示核心版本；阶段二在毕设答辩前补齐 Ragent 级全功能与创新点实验。
- **理由**：唯一确定的死线是"秋招 11 月前拿 offer，边投边做"；一步到位做全功能会导致秋招时项目跑不起来，是求职大忌。
- **被放弃的方案**：一步到位做全功能再演示（秋招大概率赶不上，简历项目处于"在建"状态吃亏）。
- **日期**：2026-09-03

## D7. MVP 检索 = 混合检索（向量 + BM25 + RRF + Rerank + 并行召回）
- **决策**：阶段一实现向量检索 + 关键词检索（ES/BM25）+ RRF 融合去重 + Rerank 重排 + 多通道并行召回。
- **理由**：混合检索是 RAG 面试最高频考点，性价比最高，足以体现深度。
- **被放弃的方案**：仅向量 TopK（玩具级）、四路全上含知识图谱 + 联网搜索（知识图谱/联网是巨坑，联网搜索已排除）。
- **日期**：2026-09-03

## D8. MVP 问题理解 = 问题重写 + 简单意图识别
- **决策**：阶段一实现问题重写（多轮上下文补全）+ 简单意图识别（知识库 / 工单 / 闲聊三类路由）+ 单知识库。
- **理由**：多轮改写是客服刚需且好讲；简单意图路由贴合"客服"场景（判断走知识库还是转人工）。
- **被放弃的方案**：全套树形意图识别 + 多知识库路由 + 问题拆分（企业级满配，放阶段二）。
- **日期**：2026-09-03

## D9. 转人工 = 轻量工单闭环
- **决策**：AI 判定答不了 → 生成工单 → 管理员后台回复 → 用户查看回复。
- **理由**：有"兜底"闭环，是"客服系统"区别于"问答 Demo"的关键，工作量可控。
- **被放弃的方案**：纯机器人无兜底（答辩会被问"解决不了怎么办"）、实时坐席（SSE/WebSocket 人工接入 + 排队 + 分配，工程量最大，放阶段二可选）。
- **日期**：2026-09-03

## D10. 功能取舍（MVP 含 / 阶段二承诺 / 明确排除）
- **决策**：
  - MVP 纳入：回答溯源 + 引用原文预览、基础数据统计看板、最简点赞点踩（仅落库）。
  - 阶段二承诺：模型路由 + 首包探测 + 三态熔断降级、全链路 Trace + 审计、完整版限流（Redis 公平排队）、RAG 效果评估体系、ReAct Agent + MCP、树形意图 / 多知识库路由。
  - 阶段二可选延伸：知识图谱检索、入库 Pipeline 节点编排、RocketMQ 事务消息、问题拆分、会话记忆持久化摘要、SSE 分事件 + 跨节点取消、坐席工作台、推荐追问、反馈回流、敏感词风控、多租户。
  - 明确排除：联网搜索通道、VLM 图片理解。
- **理由**：以"客服主线 + 求职可讲性 + 8 周工期"三重约束做优先级切分；排除项对客服场景价值低或过重。
- **被放弃的方案**：MVP 全都要（工期不允许）、把重功能全砍（丢失企业级亮点与阶段二论文素材）。
- **日期**：2026-09-03

## D11. AI 框架 = Spring Boot + Spring AI + Spring AI Alibaba + JDK 17
- **决策**：后端采用 Spring Boot + Spring AI + Spring AI Alibaba（DashScope）+ JDK 17，模型底层管道交给官方框架，业务编排层自研。（**注**：初始设想 Spring AI 1.0.x，后经联网核实发现版本错配，具体版本组合由 **D20** 校准为 Boot 3.5.16 + Spring AI 1.1.2 + Alibaba 1.1.2.2。）
- **理由**：本人负责决策但不深钻底层代码，框架可显著降低需答辩防守的底层细节；Spring AI Alibaba 原生对接百炼通义千问、支持多模型共存、内置 Graph 工作流；1.0.x GA + Spring Boot 3.5 是资料最多、最稳的组合。
- **被放弃的方案**：手写模型客户端层（Ragent 式，最可控但底层细节多、答辩难守）、LangChain4j（版本 churn + 抽象黑盒）、Python/FastAPI/LangChain（与 Java 后端求职方向不符）。
- **日期**：2026-09-03

## D12. 存储 = MySQL + Elasticsearch 8.x 一体化 + Redis
- **决策**：业务数据用 MySQL；知识向量 + 关键词检索用 Elasticsearch 8.x（dense_vector kNN + BM25 + 原生 RRF 融合）一体化；缓存/限流/会话短期记忆用 Redis。
- **理由**：ES 一个组件同时搞定混合检索三路能力，契合 D7；MySQL/ES/Redis 全是本人简历已有技能，面试连贯，无陌生组件。
- **被放弃的方案**：Milvus + ES 双系统（数千 chunk 属杀鸡用牛刀、运维重）、PostgreSQL + pgvector（BM25 弱于 ES、PG 是陌生组件、与简历 MySQL 不连贯）。
- **日期**：2026-09-03

## D13. 模型选型 = 阿里 qwen-plus 主力 + 硅基流动免费 E/R + 多供应商兜底
- **决策**：Chat 主力用阿里 qwen-plus（百炼，90 天大额免费额度、国内稳定、有发票）；Embedding 用硅基流动 bge-m3（1024 维，免费）；Rerank 用硅基流动 bge-reranker-v2-m3（免费）；兜底 Chat 用智谱 GLM-4-Flash（永久免费）/ DeepSeek（极低价）。
- **理由**：学生预算优先免费额度与低价；多供应商组合天然支撑阶段二"模型路由 + 熔断降级"；E/R 全免费大幅降低成本。
- **被放弃的方案**：阿里百炼全家桶（跨供应商降级故事弱、免费额度到期需付费）、全硅基流动（Chat 能力一般、生产稳定性弱、免费 QPS 限制）、DeepSeek 主力（无 E/R 需混用、峰谷计费高峰翻倍）。
- **日期**：2026-09-03

## D14. 部署 = 本地 Docker Compose + 云学生机公网
- **决策**：本地用 Docker Compose 一键起（MySQL/ES/Redis/后端/前端）做开发与答辩演示；同时部署到云学生机（几十元/月）提供公网访问。
- **理由**：答辩现场演示稳定；公网可访问让简历能写"线上部署"、面试官可实际体验；成本可控。
- **被放弃的方案**：仅本地 Compose（无公网、简历不能写线上部署）、仅云服务器（本地开发调试不便）、K8s 部署（学生单机过重，列为阶段二可选加分，呼应亿迅 K8s 经历）。
- **日期**：2026-09-03

## D15. 坐席范围与反馈按钮
- **决策**：保留访客 / 客服坐席 / 管理员三角色，但 MVP 阶段工单由管理员后台回复，完整坐席工作台（工单池认领 / 状态流转）放阶段二可选；MVP 保留最简点赞/点踩按钮（仅落库，不做回流）。
- **理由**：坐席台放阶段二可省 MVP 工作量；点赞点踩为阶段二 RAG 评估体系预留真实反馈数据来源。
- **被放弃的方案**：MVP 就做独立坐席角色 + 坐席台（MVP 变重）、MVP 完全不做反馈（阶段二评估缺数据、需改用人工测试集）。
- **日期**：2026-09-03

## D16. 前端框架 = React 18 + TypeScript + Ant Design
- **决策**：前端采用 React 18 + TypeScript + Ant Design，含用户对话端与管理后台。
- **理由**：对齐 Ragent 参考栈；Ant Design 企业级组件成熟、管理后台开箱即用；TypeScript 提升可维护性；契合"企业级前端"目标。
- **被放弃的方案**：Vue3 + Element Plus（同样可行，但与参考栈不一致，本项目语境下生态契合度略低）。
- **日期**：2026-09-03

## D17. 认证方案 = Spring Security + JWT
- **决策**：认证鉴权采用 Spring Security + JWT，配合 RBAC 与数据归属校验。
- **理由**：匹配本人简历 JWT/Shiro 经验、面试可讲；Spring Security 是企业级主流、生态完善，与 Spring Boot 3.5 无缝集成。
- **被放弃的方案**：Sa-Token（更轻、上手快，但与简历经验不连贯、企业级叙事略弱）。
- **日期**：2026-09-03

## D18. 实现阶段并行开发分工策略
- **决策**：采用"阶段A 单路串行打地基并冻结契约 → 阶段B 开 3 路后端(入库检索/问答/工单统计系统)+1 路前端并行、每路独立 git worktree → 阶段C 集成联调"；混合检索与问答链路两个核心亮点模块由本人主导/深度参与，体力活(前端页面/CRUD/工单/统计)分给并行会话。
- **理由**：模块依赖是串行的，地基与契约不先冻结则并行必崩；2-4 路是并行甜蜜点，过多则集成成本吃掉收益；本人主导亮点模块以保障"每处决策可讲清"（最高目标）。
- **被放弃的方案**：一次开 5-6 路最大化提速（合并冲突与集成成本高）、全部并行本人只集成（对代码理解变浅、答辩面试风险高）、仅严格目录边界不隔离（公共文件仍冲突）。
- **日期**：2026-09-03

## D19. 版本管理 / 仓库策略 = 本地 git + GitHub 私有仓库
- **决策**：本地 git init 作为 worktree 并行前提；远程采用 GitHub 私有仓库，先私有、毕设答辩通过后再决定是否公开做简历展示；API Key 走环境变量 + .gitignore 绝不入库；所有 git commit/push/merge 由本人执行，AI 不自动提交。
- **理由**：本地 git 是 worktree 隔离的硬前提；GitHub 私有兼顾备份/多机协作且规避查重风险；答辩后转公开可作求职展示；密钥入库难彻底清除故走环境变量。
- **被放弃的方案**：公开仓库（毕设查重/原创性质疑风险，与 D2 冲突）、Gitee 私有（求职认可度略低）、仅本地不建远程（无备份、无法多机协作、简历无法贴链接）。
- **日期**：2026-09-03

## D20. 框架版本校准 = Spring Boot 3.5.16 + Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0
- **决策**：将 D11 的版本组合校准为 Spring Boot 3.5.16 + Spring AI 1.1.2 + Spring AI Alibaba（核心 BOM 1.1.2.0 + 扩展 BOM 1.1.2.1）+ JDK 17；用 spring-ai-bom + spring-ai-alibaba-bom + spring-ai-alibaba-extensions-bom 三个 BOM 统一版本。**已 mvn clean compile 实测 BUILD SUCCESS（10 模块全过）**。
- **理由**：联网核实发现原 D11 的"Spring AI 1.0.x"官方对应 Spring Boot 3.4.x（与 3.5 错配，会依赖打架）；Spring Boot 3.5.x 已于 2026-06 EOL，3.5.16 为最后 OSS 稳定版；Spring AI Alibaba 1.1.2.x 官方基于 Boot 3.5.x + Spring AI 1.1.2，匹配且含 Agent 能力，毕设周期（至 2027-06）足够。**踩坑记录（面试可讲）**：Alibaba 的 BOM 拆为核心（spring-ai-alibaba-bom，管 graph/agent/studio）与扩展（spring-ai-alibaba-extensions-bom，管 dashscope 等模型 starter）两层；只 import 核心 BOM 会导致 starter-dashscope 版本缺失、编译报 'version is missing'，必须两个 BOM 都 import。
- **被放弃的方案**：Boot 4.x + Spring AI 2.0 + Alibaba 2.0.0-M1（里程碑版不稳、追新风险高）、Boot 3.4.x + Spring AI 1.0.0 + Alibaba 1.0.0.2（更老、功能少、非新项目推荐）、沿用原 D11 错配组合（编译期依赖冲突）。
- **日期**：2026-09-03

## D21. 多会话协作模式 = 统筹会话 + 各开发路独立会话 + 提示词约束
- **决策**：主会话（本会话）做统筹——维护全局设计/契约、规划里程碑、为各路拟定角色提示词、协调模块边界、负责集成联调、审校汇总阶段汇报；前端(路4)、M3(路3)及阶段二各模块分别开独立会话开发，每会话用专门提示词（docs/prompts/pathX-*.md）固化角色/边界/契约/记录文档要求。
- **理由**：各会话上下文专注不超载；统筹保全局一致性与集成质量；提示词防止越界改代码与契约漂移；落实 D18 的阶段B并行+集成者策略。
- **被放弃的方案**：单会话串行做全部（上下文超载、进度慢）、多会话但无统筹无提示词（易越界冲突、契约各自漂移）。
- **日期**：2026-09-04

## D22. M3 认证实现 = jjwt 0.12.6 + 手动查库 + BCrypt 校验
- **决策**：JWT 库选 jjwt 0.12.6（api/impl/jackson 三构件，版本根 pom 管理）；登录不走 DaoAuthenticationProvider/UserDetailsService 体系，改为 AuthService 手动查库 + `BCryptPasswordEncoder.matches`，错误码一一映射（5002 重名 / 5003 密码错 / 5004 禁用）；登录失败统一 5003「用户名或密码错误」防用户名枚举。HS256 对称签名，秘钥走环境变量 JWT_SECRET，有效期 24h。
- **理由**：jjwt 是 JVM 生态最广用的 JWT 库、API 类型安全、无依赖膨胀；本系统用户体量小且单角色字段，UserDetailsService 体系会把「不存在/禁用/密码错」压进 AuthenticationException 继承树，业务错误码反而难对应；手动流程直白可控、答辩可讲性强。
- **被放弃的方案**：Sa-Token（D17 已否）、auth0 java-jwt（资料少于 jjwt）、自签自验（密码学细节易错）、DaoAuthenticationProvider 全套（MVP 场景过度抽象）。
- **日期**：2026-09-04

## D23. M3 RBAC = 编程式 SecurityUtils.requireAdmin() + 开发期默认用户过渡
- **决策**：接口角色校验用 cs-framework 的 `SecurityUtils.requireAdmin()`（编程式），RBAC 拒绝返回 HTTP 200 + code=1003（R 体系）；HTTP 401 留给过滤器链（未认证），RestAccessDeniedHandler 保留 HTTP 403（当前不触发）。认证收口前 SecurityUtils 回退开发默认用户 {id=1, ADMIN}，认证上线后由 JwtAuthenticationFilter 写入真实上下文自动覆盖；默认管理员由 AdminSeeder 首次启动播种（BCrypt，幂等）。
- **理由**：错误码统一走 R 体系，前端只需一套处理逻辑；避免 cs-ticket/cs-stats 为用 @PreAuthorize 而引 spring-security 依赖扩散；回退默认用户让工单/反馈/统计先于认证可端到端自测且角色校验逻辑一次写对（受保护接口匿名请求进不了 Controller，回退不会绕过鉴权）。
- **被放弃的方案**：@PreAuthorize + @EnableMethodSecurity（校验失败抛 AccessDeniedException，要么被兜底成 1999 要么需额外 MVC handler，且依赖扩散）；URL 级 hasRole()（粒度粗）；先上鉴权再写业务（每步被阻塞）。
- **待确认**：契约字面「无权限 403」与实现「HTTP 200 + code=1003」的口径差异，交统筹会话与前端对齐确认（未单方面改契约）。
- **确认结论（2026-09-04 由用户拍板，此项关闭、不再悬置）**：RBAC 拒绝以 **HTTP 200 + `code=1003`** 为准。`contract/rest-api.md`「通用约定 · 错误」条其实**早已正确表述**该口径（未认证 → HTTP 401，前端据此跳登录；已认证但角色不足 → HTTP 200 + code=1003，前端提示「无权限」不跳登录），本轮核对确认**契约原文无需改动**；`RestAccessDeniedHandler` 的 HTTP 403 仅作过滤器链兜底、**当前不主动触发**。
- **日期**：2026-09-04

## D24. M3 统计口径与实现 = 实时聚合 SQL + 每日快照归档双轨
- **决策**：指标口径：咨询量 = chat_message(role=user) 消息数；解决率 = LIKE/(LIKE+DISLIKE)（无样本返回 null，不硬造 0%）；转人工率 = 工单数/咨询量；热点问题 = user 消息按内容分组 TopN；趋势按天聚合且缺数日补零。实现：cs-stats 用 @Select 聚合 SQL 只读直查 chat_message/ticket/feedback（不依赖对方模块 Service，守依赖方向）；另加 stat_snapshot 每日 00:05 幂等快照（uk_date 覆盖更新）供历史回看与阶段二评估。
- **理由**：统计是读侧场景，表级只读是数仓/统计模块常规边界；MVP 单机 MySQL 聚合毫秒级无需 OLAP；实时接口保证演示数据新鲜，快照为阶段二 RAG 评估体系留时序数据。
- **被放弃的方案**：依赖 cs-qa/cs-ticket Service（破坏依赖方向或需接口下沉）；只读快照（当天数据缺失，演示体验差）；ES 聚合热点（阶段二增强：ik 分词后按词根统计比整句 group by 更准）。
- **日期**：2026-09-04

## D25. 前端依赖版本精确锁定（patch 级）= 26 包去 caret 锁实装版本 + engines + .npmrc
- **决策**：将 `frontend/package.json` 的 26 条 dependencies/devDependencies 全部去掉 `^` 前缀，锁定为当前 `package-lock.json` 的实装版本（精确到 patch）；新增 `"engines": { "node": ">=20" }`；新建 `frontend/.npmrc`（`registry=https://registry.npmmirror.com`）使镜像源配置随仓库版本化。操作口径：改 package.json 后必须 `npm install` 同步 lock（不能 `npm ci`，因其前置校验 spec 文本逐字一致会报 EUSAGE），验收红线为「lock diff 只应出现在根 `packages[""]` 块的 spec 行，不得有 version/resolved/integrity 变更」。
- **理由**：实测漂移数据——26 个包中 21 个已漂移（声明下限 vs lock 实装），最大漂移：`@tanstack/react-query` minor +43（5.59.0→5.102.8）、`axios` minor +13（1.7.7→1.20.0）、`msw` +11（2.4.9→2.15.0）、`antd` +8（5.21.2→5.29.3）、`prettier` +6（3.3.3→3.9.6）。`FRONTEND-DECISIONS.md` FD-8 写的「AntD 5.21 + TS 5.6」与实装 5.29.3/5.9.3 已失真。收益：彻底消除「同一份 package.json 在不同时间 `npm install` 装出不同依赖树」的漂移风险，与 D20 后端三 BOM 锁版本形成前后呼应的叙事对称。代价：阻断 patch 级安全更新自动流入——`axios`/`eslint`/`vite` 属需要跟进安全公告的包，锁定后必须建立人工例行 `npm outdated` + `npm audit` 机制，否则从「版本漂移风险」换成「漏洞滞留风险」。`.npmrc` 理由：镜像源当前仅存在于用户级 `C:\Users\17962\.npmrc`，仓库内无 `.npmrc`；换机/CI 上 `npm ci` 走 lock 的 resolved 安全，但 `npm install` 新增依赖会回落 `registry.npmjs.org` 导致国内超时，且新条目 resolved 会污染 lock 一致性——与项目已有的 Docker Registry Mirror 治理思路一致。
- **被放弃的方案**：① 只依赖 lock 不改 package.json（`npm ci` 能复现但 `npm install` 仍漂移，且 `^5.21.2` 与实装 `5.29.3` 差距过大不利阅读）；② 用 `overrides` 强制锁间接依赖（过重，MVP 无此需求）；③ 引入 renovate/dependabot 自动升级（毕设周期内维护成本不划算，可列阶段二可选）。
- **日期**：2026-09-04

## D26. 引用溯源 = 快照冗余落库（`chat_reference` 增 doc_title / chunk_text）
- **决策**：`chat_reference` 表增加 `doc_title VARCHAR(256)` 与 `chunk_text TEXT` 两列，问答落库时冗余写入「当时向用户展示了什么」（`QaService.saveReferences` 从 `RetrievedChunk` 取 `docTitle` / `content`）。**旧数据降级规则**：本轮之前产生的引用行两列为 `null`，读取时 `docTitle` 按 `doc_id` 批量 join `kb_document.title` 兜底、`chunkText` 回填空串 `""`，**不做 ES 回查**。
- **理由**：① **引用快照语义**——`chat_reference` 记录的是「当时向用户展示了什么」，本质是**审计快照**，与 `kb_chunk_meta` 的「当前索引状态」是两种不同职责，因此不违反「chunk 正文只存 ES、MySQL 仅存元数据」的原则；② **读性能**——历史消息回放零 ES 往返，且 ES 不可用时历史接口不会 500；③ **写放大量化**——一次问答最多 5 条引用 × 约 500 字符 ≈ 2.5KB，在 D5 的语料规模（数百文档 / 数千 chunk）下完全可接受。
- **被放弃的方案**：① 纯 ES `mget` 按 `es_chunk_id` 回查（N+1 严重：20 条消息 × 5 引用 = 100 次 ES GET；且 ES 挂了整个历史接口 500）；② 只 join MySQL 取 `doc_title`、不冗余 `chunk_text`（引用来源面板展开后是空白，D10 纳入 MVP 的「回答溯源 + 引用原文预览」在历史回放路径上仍然断裂，等于没修）。
- **日期**：2026-09-04

## D27. 全局时间序列化 = JSR-310 `Jackson2ObjectMapperBuilderCustomizer`（cs-bootstrap）
- **决策**：在 `cs-bootstrap` 新建 `JacksonConfig`，注册 `Jackson2ObjectMapperBuilderCustomizer` bean，用 `serializerByType` + `deserializerByType` **双向**把 `LocalDateTime` 定为 `yyyy-MM-dd HH:mm:ss`、`LocalDate` 定为 `yyyy-MM-dd`。**硬约束（必记）：不得把 datetime 的 pattern 套到 `LocalDate` 上**——否则将来 `StatsMapper` 若改返回 `LocalDate`，`TrendVO.date` 会变成 `2026-09-04 00:00:00`，污染前端折线图 X 轴。
- **理由**：`spring.jackson.date-format` 只对 `java.util.Date`/`Timestamp` 生效，本项目时间字段全是 JSR-310 类型（`LocalDateTime`/`LocalDate`），配了等于没配（实测 `application.yml` 亦无该配置项）；且改造前实测输出为 ISO 带 `T`，与契约「通用约定 · 时间格式」条、前端 Mock 数据、`frontend/src/utils/format.ts` 的注释三处都不一致，改后端是向三者对齐。选 Customizer 而非直接暴露 `ObjectMapper` bean，是为了不覆盖 Boot 自动配置的其它默认行为、仅叠加格式化器；Deserializer 属**防御性注册**（当前全仓 `@RequestBody` DTO 均无日期字段）。**字段数口径校正**：`JacksonConfig` 的 Javadoc 写「15 个时间字段」，实测全仓 `LocalDateTime`/`LocalDate` 字段声明共 22 处，其中业务实体侧（扣除未启用的 `sys_role`/`sys_permission`）为 15 处。
- **被放弃的方案**：① 逐字段加 `@JsonFormat`（易遗漏，且每次新增 VO 都要记得加，无强制机制）；② 改契约承认 ISO 带 `T`（用户已否决，选「后端补齐对齐契约」）；③ 配 `spring.jackson.date-format`（对 JSR-310 无效，正是本条要纠正的错误认知）。
- **日期**：2026-09-04

## D28. token 成本统计口径 = 末 chunk usage 覆盖累积 + 允许 null + HashMap 负载
- **决策**：token 用量从流式**最后一个 chunk** 的 `ChatResponse.getMetadata().getUsage().getTotalTokens()` 取值，写入 `chat_message.token_cost` 并经 SSE `done` 事件回传；`MessageVO` 同步新增可空的 `tokenCost?`，修复「流式结束显示 N tokens 标签、刷新页面后标签消失」。**取不到 usage 时允许为 `null`**：前端以 `typeof === 'number'` 守卫自动隐藏标签、不报错；转人工 / 兜底分支不调模型，固定为 `null`。**实现坑（必记）**：SSE `done` 负载不能用 `Map.of` 构造（`Map.of` 不容忍 `null` value，`tokenCost` 为 `null` 时会运行期抛 NPE），须用 `HashMap`。
- **理由**：DashScope 在流式模式下 usage 只在最后一个 chunk 返回，前面的 chunk 为 `null` 或 `0`，故需在消费侧用可变持有者「持续用非 null 值覆盖」（实现为单元素数组 `Integer[] tokenCostRef = {null}`，绕过 lambda 对捕获变量的 effectively-final 限制；消费是顺序阻塞的 `.toStream().forEach`，全程在同一异步线程内、事实单线程无并发写）；`AnswerGenerateService` 也因此把 `.stream().content()` 改为 `.stream().chatResponse()`——返回 `Flux<ChatResponse>` 而非 `Flux<String>`，纯文本流会丢失 usage。`chat_message.token_cost` 列与实体字段阶段一就已存在但从未被写入，本轮首次真正落值。`avgTokenCost` 本轮不做（D10 把成本分析归阶段二）。
- **被放弃的方案**：① 删掉契约里的 `tokenCost` 字段（用户已否决）；② 用 `AtomicInteger` 累积（无法表达 `null`，与「允许为 null」的降级设计冲突）。
- **日期**：2026-09-04

## D29. JWT 主动作废 = jti + Redis 双 key（登出黑名单 + 用户级失效）
- **决策**：签发时加 `jti`(UUID) + Redis 双 key（`jwt:blacklist:{jti}` 登出黑名单 + `jwt:user-invalid:{userId}` 用户级失效时间戳）+ JWT 过滤器两道作废检查 + `POST /api/auth/logout` 幂等登出；旧 token 无 jti 时回退 `SHA-256(token)` 摘要作 key。批次 0 集成收口追加：`/api/auth/logout` 精确放行（DEF-092），否则已拉黑 token 被过滤器拦下、幂等无从谈起。
- **理由**：令牌泄露后需立即失效，仅靠 24h 过期不够；黑名单 TTL 取剩余有效期避免积累无用键；加 `jti` 同时为将来的 token 追踪与 `audit_log`（DEF-046）打基础。
- **被放弃的方案**：① 双 token（access+refresh）：必然牵动前端存双 token 与 401 刷新重试，越出「仅 cs-system」边界，且黑名单方案已覆盖登出与封号两个真实场景，无实质缺陷；② 引入 `UserDetailsService` 体系：D22 明确禁止，会与手动查库流程冲突；③ 本地缓存（Caffeine）减少 Redis 往返：新增依赖触碰 D20，且实测增量仅约 5ms 不值得。
- **日期**：2026-09-09

## D30. 模型可靠层路线 = ChatModelFacade + 手写三态熔断 + 备用模型手动构造不注册为 bean
- **决策**：`cs-infra-ai` 建 `ChatModelFacade` 作全仓唯一模型调用入口，手写三态 `CircuitBreaker`（滑动窗口 + 并发安全），备用模型**手动构造 `OpenAiChatModel` 且不注册为 Spring bean**；failover 链 DashScope qwen-plus → 硅基流动免费 Chat（备1）→ GLM-4-Flash/DeepSeek（备2/3，无 key 自动不注册）。
- **理由**：① 复用 `cs-infra-ai` 已有的 `spring-ai-starter-model-openai`（embedding 在用），**零新增依赖**不触碰 D20；② 流式天然统一为 `Flux<ChatResponse>`，消除自研响应式 SSE 解析的风险，且 usage 提取与主链同构保住 tokenCost；③ 不注册为 bean 则容器内 `DashScopeChatModel` 保持唯一，`ChatClientAutoConfiguration` 的 `@ConditionalOnSingleCandidate` 语义不受影响。
- **被放弃的方案**：① 手写 `RestClient` 调 OpenAI 兼容接口：流式需自研响应式 SSE 解析，成本高易错；② 引入 Resilience4j：新增依赖触碰 D20，且手写状态机是答辩可讲的亮点；③ 熔断状态存 Redis：MVP 单实例部署无必要。
- **必须记录的取舍**：备1 是 7B 级免费模型，实测首包偶发 15s、生成 322 chunks 约 2.5 分钟、内容质量明显下降——**降级后可用性优先于质量，这是刻意取舍不是缺陷**；健康探测失败仅返回 FAIL **不计入熔断统计**，避免探测流量造成正反馈（探测越失败熔断越开）。
- **日期**：2026-09-09

## D31. 流式熔断的时机边界 = 首包前可 failover，首包后不重发
- **决策**：**首包之前**失败可 failover 到备用供应商（用户无感知）；**首包之后**失败不重发（用户已看到一半内容，重发会重复错乱），改为保留已生成部分落库 + 推 SSE `error` 事件 + 计入熔断统计。
- **理由**：流式输出的不可撤回性。
- **被放弃的方案**：首包后也 failover 重发（内容重复）、首包后静默截断（用户看到断尾却不知发生了什么）。
- **日期**：2026-09-09

## D32. 模型失败的落库与错误码口径 = 入口守卫 + resolveErrorCode 按异常类型分派
- **决策**：① `saveAssistantMessage`/`saveUserMessage` **入口**守卫，content 为 null/blank 时跳过 insert（守卫必须置于入口而非 catch 分支，否则只覆盖单条路径）；② `resolveErrorCode(Throwable)` 作为唯一错误码映射点，沿 cause 链下钻处理包装形态，`ModelUnavailableException`/`StreamInterruptedException` → 3002，其余含 `DataAccessException` → 1999；③ `ChatRequest.question` 契约字段澄清为 `question`（批次 0 集成收口实测澄清，此前启动包示例误写 `message`）；DTO 层 `@NotBlank` 交统筹拍板（SSE 端点 `@Valid` 失败返回 HTTP 200+JSON 会被前端静默吞掉，服务层早退已用 SSE error 1001 正确出口）。
- **理由**：容器实测中模型全链失败时以 null content 撞 `chat_message.content` 的 NOT NULL 约束，MyBatis-Plus 默认 `NOT_NULL` 字段策略会把 null 字段整个从 INSERT 省略，MySQL 8.4 严格模式报 `Field 'content' doesn't have a default value`，而宽 catch 把该 DB 异常统一报成 3002 **完全掩盖了真因**，耗费整轮排障才定位。
- **被放弃的方案**：① 给 `content` 列加默认值（掩盖问题且污染语义）；② 关闭 MySQL 严格模式（全局降级，代价过大）；③ 守卫留在 catch 分支（只覆盖单路径，正是本次缺陷根源）。
- **日期**：2026-09-09

## D33. 测试选型 = JUnit5 + Mockito 纯单测，不引入 Testcontainers
- **决策**：路 11 关键路径单测采用 JUnit5 + Mockito，**不引入 Testcontainers**。
- **理由**：避开 D20 版本敏感区与新增依赖；本项目中间件在 VM 内已长期运行，集成验证走真实环境比容器内起一套更接近部署形态。
- **被放弃的方案**：`@SpringBootTest` + Testcontainers（新增测试依赖需写进根 pom、VM 内跑容器嵌套有额外坑、成本高一个量级）。**同时记录既有实践**：路 7 两轮补刀均用「临时主程序 + 动态代理 Mapper + 反射调用私有方法」验证后删除源码与 class，在 0 测试依赖下取得了 19/19 与 16/16 的断言覆盖——这是本项目的过渡形态，路 11 落地正式单测后应替换。
- **日期**：2026-09-09

## D34. ReAct Agent + MCP 定位 = 阶段二加分项，可砐
- **决策**：阶段二的**加分项，可砐**，不进论文核心章节。
- **理由**：需新建 `cs-agent` 模块、`spring-ai-alibaba` 的 graph/agent 虽随核心 BOM 1.1.2.0 早已 import 但**从未被任何模块实际使用过**、且需模拟业务 API，工期最不可控。
- **被放弃的方案**：当核心亮点做（工期风险过高，挤占评估体系与检索增强的时间）。
- **日期**：2026-09-09

## D35. Compose 项目名必须钉死 = name: telecom-cs-rag
- **决策**：`docker-compose.yml` 保留顶层 `name` 字段并钉死为 `telecom-cs-rag`，同时在 `deploy-guide.md` 写明「compose 项目名决定数据卷前缀，改名等于换库」。
- **理由**：Compose v2 的项目名优先取 yml 顶层 `name:`、其次取 compose 文件所在**目录名**；批次 0 改造时该字段缺失，在 VM 的 `~/AIBishe` 目录下跑就挂上了全新的 `aibishe_*` 空卷，阶段一的 `telecom-cs-rag_*` 数据被**静默孤立**（数据一条没删，只是再也不会被挂载）。
- **被放弃的方案**：① 统一改用 `aibishe` 项目名并迁移数据（需 mysqldump + ES 重建索引，成本高于收益）；② 靠「固定在同一个目录跑」的口头约定（换机器/换用户即失效）。
- **日期**：2026-09-09

## D36. ES/MySQL 一致性方案 = RocketMQ 事务消息补偿（待执行），相应修订 D12
- **决策**：DEF-029 采用 RocketMQ 事务消息补偿，**相应修订 D12「禁止引入新存储组件」为「禁止引入新存储组件；消息中间件在入库一致性场景下作为例外，须经统筹确认」**。执行时机 = **待 C4（入库异步化）排期时**，批次 0 与批次 1 均不引入。
- **理由**：`IngestionService.ingest` 的 `@Transactional` 只保 MySQL，回滚后 ES 可能残留 chunk 并被检索命中；outbox 模式虽不加中间件但对「ES 写成功而 MySQL 回滚」这个方向无能为力。
- **被放弃的方案**：① outbox 本地消息表 + 定时补偿（不加中间件、守 D12，但只能覆盖单向）；② 异步入库 + 重试状态机（最简单，但极端情况仍有不一致窗口）。**注意：本条只是决策记录，批次 0 不落地，`docker-compose.yml` 不得出现 RocketMQ 服务。**
- **日期**：2026-09-09

---

## 待拍板 / 待补充（后续追加）
- 毕设题目是否采用《基于 RAG 的电信运营商智能客服系统的设计与实现》。
- 毕设约束（验收侧重 / 云端 API 限制 / 开题-中期-答辩时间线 / 论文格式要求）：导师与学院确定后回填，并据此校准里程碑。
