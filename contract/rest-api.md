# REST API 契约（MVP）

> **用途**：前端路4 据此 Mock 并行开发；后端各路据此实现 Controller。这是阶段 B 的冻结契约之一，变更需同步所有路（见 CONVENTIONS 第 11 节）。
> **统一响应**：所有接口返回 `R<T>` = `{ code, message, data, timestamp, traceId }`，`code=0` 成功（见 CONVENTIONS 第 5 节）。
> **认证**：除注册/登录外，均需请求头 `Authorization: Bearer <JWT>`。

---

## 通用约定
- **BaseURL**：`/api`
- **分页**：请求 `?current=1&size=10`；响应 `data = { records:[], total, current, size }`
- **时间格式**：`yyyy-MM-dd HH:mm:ss`（东八区）
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
| GET | `/api/qa/conversations/{id}/messages` | — | `[MessageVO{id,role,content,references?,createdAt}]` | 会话历史 |
| DELETE | `/api/qa/conversations/{id}` | — | `null` | 删除会话 |

## 3. 知识库 `/api/kb`（cs-knowledge / cs-ingestion，管理员）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/kb/bases` | — | `[KnowledgeBaseVO]` | 知识库列表 |
| POST | `/api/kb/bases` | `{name, description}` | `KnowledgeBaseVO` | 新建知识库 |
| GET | `/api/kb/documents` | `?kbId=&current=&size=` | `PageVO<DocumentVO>` | 文档列表 |
| POST | `/api/kb/documents/upload` | multipart `{file, kbId}` | `DocumentVO` | 上传文档（触发入库流水线） |
| POST | `/api/kb/documents/url` | `{url, kbId}` | `DocumentVO` | URL 抓取入库 |
| DELETE | `/api/kb/documents/{id}` | — | `null` | 删文档（含 ES chunk 清理） |
| POST | `/api/kb/documents/{id}/reindex` | — | `null` | 重建索引 |
| GET | `/api/kb/documents/{id}/status` | — | `{status, chunkCount}` | 入库进度轮询 |

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
| GET | `/api/stats/overview` | — | `{askCount, resolveRate, ticketRate, ...}` | 概览看板 |
| GET | `/api/stats/hot-questions` | `?limit=10` | `[{question, count}]` | 热点问题 |
| GET | `/api/stats/trend` | `?days=7` | `[{date, askCount, resolveCount}]` | 趋势 |

## 7. 系统管理 `/api/system`（cs-system，管理员）
| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| GET | `/api/system/users` | `?current=&size=&keyword=` | `PageVO<UserVO>` | 用户列表（仅 ADMIN；keyword 按用户名/昵称模糊搜索）|

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
- `UserVO`：`{id, username, nickname, role}`
- `ConversationVO`：`{id, title, lastActiveAt}`
- `MessageVO`：`{id, role(user/assistant), content, references?:[{docTitle,chunkText,score}], createdAt}`
- `DocumentVO`：`{id, kbId, title, sourceType, fileType, chunkCount, status, createdAt}`
- `KnowledgeBaseVO`：`{id, name, description, embeddingModel, status}`
- `TicketVO`：`{id, question, aiReason, status(OPEN/REPLIED/CLOSED), reply?, handlerId?, createdAt, repliedAt?}`
- `PageVO<T>`：`{records:[T], total, current, size}`
