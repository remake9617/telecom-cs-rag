# REST API 契约（MVP）

> **用途**：前端路4 据此 Mock 并行开发；后端各路据此实现 Controller。这是阶段 B 的冻结契约之一，变更需同步所有路（见 CONVENTIONS 第 11 节）。
> **统一响应**：所有接口返回 `R<T>` = `{ code, message, data, timestamp, traceId }`，`code=0` 成功（见 CONVENTIONS 第 5 节）。
> **认证**：除**放行清单**外，均需请求头 `Authorization: Bearer <JWT>`。
> **放行清单（共 4 条，与 `cs-system/.../config/SecurityConfig.java` 逐字一致）**：`/api/auth/register`、`/api/auth/login`、`/api/health/ping`、`/error`。
> - `/error` 是 Spring Boot 错误页转发路径，**不放行会把真实错误包装成 401**，排障困难（M3 已追加，本轮同步进契约）。
> - `/api/health/**` **不整体放行**，仅精确放行 `/api/health/ping`；`/api/health/ai` 需 ADMIN（见第 8 节）。

---

## 通用约定
- **BaseURL**：`/api`
- **分页**：请求 `?current=1&size=10`；响应 `data = { records:[], total, current, size }`
- **时间格式**：`yyyy-MM-dd HH:mm:ss`（东八区）。**实现说明**：后端通过全局 JSR-310 序列化器（`cs-bootstrap` 的 `JacksonConfig`）输出该格式——`spring.jackson.date-format` 对 `LocalDateTime` **无效**，故不走该配置项（见 D27）；`TrendVO.date` 是 `yyyy-MM-dd` 字符串，不受影响。
- **错误**：非 0 的 `code` + `message`，HTTP 状态码统一 200（业务错误看 code）。**认证/授权例外**：未认证（无 token 或 token 失效）→ **HTTP 401**（前端据此跳登录）；已认证但角色不足 → **HTTP 200 + code=1003**（前端提示“无权限”，不跳登录）

---

## 1. 认证 `/api/auth`（cs-system）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{username, password}` | `{token, user}` | 访客注册，返回 JWT |
| POST | `/api/auth/login` | `{username, password}` | `{token, user}` | 登录 |
| GET | `/api/auth/me` | — | `UserVO{id,username,nickname,role}` | 当前登录用户 |

## 2. 问答 `/api/qa`（cs-qa）
| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| POST | `/api/qa/chat/stream` | `{conversationId?, question}` | **SSE** | 流式问答（见下方 SSE 事件） |
| GET | `/api/qa/conversations` | — | `[ConversationVO{id,title,lastActiveAt}]` | 我的会话列表 |
| GET | `/api/qa/conversations/{id}/messages` | — | `[MessageVO{id,role,content,references?,createdAt,tokenCost?}]` | 会话历史 |
| DELETE | `/api/qa/conversations/{id}` | — | `null` | 删除会话 |

## 3. 知识库 `/api/kb`（cs-ingestion，管理员）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/kb/bases` | — | `[KnowledgeBaseVO]` | 知识库列表（**管理员**） |
| POST | `/api/kb/bases` | `{name, description}` | `KnowledgeBaseVO` | 新建知识库（**管理员**；`description` 可空串，`embeddingModel`/`status` 由后端填 `bge-m3`/启用） |
| GET | `/api/kb/documents` | `?kbId=&current=&size=` | `PageVO<DocumentVO>` | 文档列表（**管理员**；`kbId` 可空=查全部库，按 `id` 倒序） |
| POST | `/api/kb/documents/upload` | multipart `{file, kbId}` | `DocumentVO` | 上传文档（**管理员**；触发入库流水线） |
| POST | `/api/kb/documents/url` | `{url, kbId}` | `DocumentVO` | URL 抓取入库（**管理员**） |
| DELETE | `/api/kb/documents/{id}` | — | `null` | 删文档（**管理员**；含 ES chunk 清理，先 ES 后 MySQL） |
| POST | `/api/kb/documents/{id}/reindex` | — | `null` | 重建索引（**管理员**；同步执行：ES 回读正文 → 重新向量化 → 相同 `_id` 覆盖写回） |
| GET | `/api/kb/documents/{id}/status` | — | `DocStatusVO{status, chunkCount}` | 入库进度轮询（**管理员**） |
| POST | `/api/kb/search` | `{query, topK}`（`topK` 默认 5） | `[RetrievedChunk]`（检索命中明细，非面向前端的契约 VO） | **管理员**：检索调试/验收端点，面向管理员排障，**会返回内部检索细节**（各通道分数、`metadata` 等 RAG 链路调试数据），不下发给普通用户 |

## 4. 工单 `/api/ticket`（cs-ticket）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| POST | `/api/ticket` | `{question, conversationId?}` | `TicketVO` | 创建工单（AI 判定或用户手动） |
| GET | `/api/ticket/mine` | — | `[TicketVO]` | 我的工单（访客） |
| GET | `/api/ticket` | `?status=&current=&size=` | `PageVO<TicketVO>` | 工单列表（管理员） |
| PUT | `/api/ticket/{id}/reply` | `{reply}` | `TicketVO` | 后台回复（管理员） |

## 5. 反馈 `/api/feedback`（cs-system）
| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| POST | `/api/feedback` | `{messageId, type(LIKE/DISLIKE), comment?}` | `null` | 点赞点踩（仅落库） |

## 6. 统计 `/api/stats`（cs-stats，管理员）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/stats/overview` | — | `OverviewVO{askCount, resolveRate, ticketRate, openTicketCount, kbCount, docCount, userCount}` | 概览看板（字段口径见下表注） |
| GET | `/api/stats/hot-questions` | `?limit=10` | `[{question, count}]` | 热点问题 |
| GET | `/api/stats/trend` | `?days=7` | `[{date, askCount, resolveCount}]` | 趋势 |

> **`OverviewVO` 字段口径（D24 裁决，本轮冻结；此前契约用省略号 `...`，该 VO 实际上从未被冻结）**：
> - `askCount` 咨询量 = `chat_message` 中 `role='user'` 的消息数。
> - `resolveRate` 解决率 = LIKE /(LIKE + DISLIKE)，**无反馈样本时返回 `null` 而非 `0`**（D24：不硬造 0%），前端展示「-」。
> - `ticketRate` 转人工率 = 工单数 / 咨询量，**`askCount` 为 0 时返回 `null` 而非 `0.0`**（与 `resolveRate` 的 D24 口径对称：空库看板两个比率统一显示「-」，不出现「转人工率 0.0%」与「解决率 -」并存；DEF-070 已修）。
> - `openTicketCount` = `ticket` 中 `status='OPEN'` 的工单数。
> - `kbCount` = `kb_knowledge_base` **全部行数（含停用库）**。
> - `docCount` = `kb_document` **全部行数（含 PENDING/PROCESSING/FAILED）**，是**存量规模**而非仅可用数。
> - `userCount` = `sys_user` **全部行数（含禁用账号）**。
> - `avgTokenCost` **本轮不实现**：D10 把 token 成本分析归入阶段二，前端亦零消费点。
> - 依赖方向（D24）：以上全部由 `cs-stats` 的只读聚合 SQL 直查产出，不依赖其它模块的 Service。

## 7. 系统管理 `/api/system`（cs-system，管理员）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/system/users` | `?current=&size=&keyword=` | `PageVO<UserVO>` | 用户列表（仅 ADMIN；keyword 按用户名/昵称模糊搜索）|

## 8. 健康检查与探活 `/api/health`（cs-infra-ai）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/health/ping` | — | `"pong"`（`R<String>`） | **公开（在放行清单内）**：**不发起任何模型调用**，仅证明应用已启动且可响应 HTTP；用途是容器 healthcheck / 负载均衡存活探针 |
| GET | `/api/health/ai` | — | AI 联通探测明细（chat / embedding 两路各自的 OK\|FAIL + 模型名、维度或错误信息） | **管理员**：**会真实发起一次 chat + 一次 embedding 调用，消耗供应商额度**，故收归管理员（未认证 / 非管理员分别抛 1002 / 1003） |

---

## SSE 流式问答事件格式（`POST /api/qa/chat/stream`）
`Content-Type: text/event-stream`，按顺序推送：
```
event: message
data: {"delta":"答案文本分片"}        # 多次，流式正文

event: reference
data: {"references":[{"docTitle":"5G套餐资费","chunkText":"...原文片段...","score":0.87}]}

event: done
data: {"messageId":123,"conversationId":45,"tokenCost":850}

event: error                          # 出错时（替代 done）
data: {"code":3002,"message":"模型调用失败"}
```
- **转人工（TICKET 意图 / 检索为空兜底）**：后端**自动创建工单**并推 `event: ticket_hint`，data = `{conversationId, ticketId, autoCreated:true}`。前端据此显示「查看工单 #ticketId」并跳转工单页，**不要再 POST /api/ticket**（后端已建单，避免重复）。
- `POST /api/ticket` 仅用于用户**主动**转人工（非 AI 自动判定）的场景，如答案旁的「转人工」按钮。

---

## VO 结构速查（前端 Mock 用）
- `UserVO`：`{id, username, nickname, role}`（`role` 枚举：`ADMIN` \| `AGENT` \| `VISITOR`）
- `LoginVO`：`{token, user}` —— `/api/auth/login` 与 `/api/auth/register` 的出参（`token` 为 JWT，`user` 为 `UserVO`；前端类型名 `AuthResult`）
- `ConversationVO`：`{id, title, lastActiveAt}`
- `MessageVO`：`{id, role(user/assistant), content, references?:[{docTitle,chunkText,score}], createdAt, tokenCost?}`（`tokenCost` **可空**：DashScope 未回传 usage 时为 `null`，前端以 `typeof === 'number'` 守卫自动隐藏标签）
- `ReferenceVO`：`{docTitle, chunkText, score}` —— **它同时是 SSE `reference` 事件的元素结构与 `MessageVO.references` 的元素结构（两处共用同一形状）**；三字段一律非 `null`（`docTitle`/`chunkText` 空值回填 `""`、`score` 回填 `0.0`）；`score` 口径 = `rerankScore != null ? rerankScore : score`，流式与历史回放两条路径必须严格一致
- `DocumentVO`：`{id, kbId, title, sourceType, fileType, chunkCount, status, createdAt}`
- `DocStatusVO`：`{status, chunkCount}` —— `GET /api/kb/documents/{id}/status` 的出参（入库进度轮询）
- `KnowledgeBaseVO`：`{id, name, description, embeddingModel, status}`
- `TicketVO`：`{id, question, aiReason, status(OPEN/REPLIED/CLOSED), reply?, handlerId?, createdAt, repliedAt?}`
- `OverviewVO`：`{askCount, resolveRate, ticketRate, openTicketCount, kbCount, docCount, userCount}`（字段口径见第 6 节表注；`resolveRate` 与 `ticketRate` 均可为 `null`）
- `HotQuestionVO`：`{question, count}` —— `GET /api/stats/hot-questions` 的元素
- `TrendVO`：`{date, askCount, resolveCount}` —— `GET /api/stats/trend` 的元素（`date` 为 `yyyy-MM-dd` 字符串）
- `PageVO<T>`：`{records:[T], total, current, size}`

### 枚举取值口径（本轮实测确认；**大小写敏感**，前端有直接依赖）
| 字段 | 取值 | 说明 |
|---|---|---|
| `DocumentVO.sourceType` | `UPLOAD` \| `URL` | **注意不是 `FILE`** |
| `DocumentVO.fileType` | `MD` \| `TXT` \| `PDF` \| `WORD` \| `EXCEL` \| `HTML` | 全大写 |
| `DocumentVO.status` / `DocStatusVO.status` | `PENDING` \| `PROCESSING` \| `DONE` \| `FAILED` | **必须大写**：前端入库进度轮询的终止判定大小写敏感，下发小写会导致每 3 秒无限轮询。**四态可达性见下方注** |
| `KnowledgeBaseVO.status` | `ACTIVE` \| `DISABLED` | **字符串**，不是 Integer 1/0；实体为 TINYINT，由后端 VO 层做映射 |
| `MessageVO.role` | `user` \| `assistant` | **必须小写**：前端严格比较 `role === 'user'` 决定气泡左右 |
| `TicketVO.status` | `OPEN` \| `REPLIED` \| `CLOSED` | 大写 |

> **`DocumentVO.status` 四态的可达性（本轮实测如实标注，关联 DEF-064）**：四态是 schema 层合法取值，但**当前入库为同步事务**——`IngestionService.ingest` 在 `insert` 时置 `PROCESSING`、同一事务末尾置 `DONE`，事务提交时对外已是 `DONE`，故 `PENDING`/`PROCESSING` **对外不可观测**；`upload`/`url` 路径失败会整行回滚、文档行直接消失，`FAILED` 在该路径**不可达**，仅 `reindex` 路径（非事务）会写 `FAILED`。连带后果：契约的「入库进度轮询」与前端 3 秒轮询实际**无进度可轮**，首次拉取即命中终止判定。异步入库（Pipeline 节点编排，D10 承诺项）挂阶段二解决（DEF-064）。
