# CONVENTIONS.md — 编码规范与并行开发契约

> **定位**：本文件是阶段 B「4 路并行开发」的**共同契约**。任何会话/开发者动代码前先读本文。
> 契约变更（统一响应 / 错误码 / Service 接口 / DB schema / API）必须同步通知所有路并更新本文。
> 配套：技术决策见 `DECISIONS.md`；架构与数据模型见 `docs/DESIGN.md`。

---

## 1. 技术与版本锚点（D20）
| 项 | 值 |
|---|---|
| JDK | 17（本项目用 `ms-17.0.20.1`，全局 JAVA_HOME 不变，见 `build.ps1`） |
| Spring Boot | 3.5.16 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 核心 BOM 1.1.2.0 + 扩展 BOM 1.1.2.1（dashscope starter 在扩展 BOM） |
| MyBatis-Plus | 3.5.9 |

- **版本一律由根 pom 的 `properties` + `dependencyManagement`（BOM）统一管理**；子模块引依赖**不写 `<version>`**。
- 需要新三方库时，先在根 pom `dependencyManagement` 声明版本，再在子模块引用。

## 2. 模块职责与依赖方向
| 模块 | 职责 | 可依赖 |
|---|---|---|
| cs-framework | 统一响应/异常/错误码/工具 | 无内部依赖（最底层） |
| cs-infra-ai | 模型客户端（Chat/Embedding/Rerank） | framework |
| cs-ingestion | 解析→分块→向量化→ES 索引 | framework, infra-ai, knowledge |
| cs-knowledge | 混合检索（向量+BM25+RRF+Rerank）+ 元数据 | framework, infra-ai |
| cs-qa | 问答主链（重写/意图/检索/生成/溯源/SSE） | framework, infra-ai, knowledge |
| cs-ticket | 转人工工单 | framework |
| cs-system | 用户/RBAC/反馈/审计/认证 | framework |
| cs-stats | 统计看板/评估 | framework |
| cs-bootstrap | 启动装配（启动类+主配置） | 所有业务模块 |

- **依赖只能自上而下**（framework 在最底），**禁止循环依赖**、禁止反向依赖。
- 跨模块调用只通过对方暴露的 **Service 接口**，不直接依赖其内部实现类。

## 3. 包结构规范
```
com.cs.<module>.<layer>
```
- `<module>`：framework / infra.ai / ingestion / knowledge / qa / ticket / system / stats
- `<layer>`：controller / service / service.impl / mapper / entity / dto / vo / config / enums / exception / util
- 示例：`com.cs.knowledge.controller.DocumentController`、`com.cs.knowledge.service.impl.RetrievalServiceImpl`

## 4. 分层与命名
| 类型 | 规范 | 示例 |
|---|---|---|
| Controller | `XxxController`，`@RestController`，**只返回 `R<T>`** | `ChatController` |
| Service | 接口 `XxxService` + 实现 `XxxServiceImpl` | `RetrievalService` / `RetrievalServiceImpl` |
| Mapper | `XxxMapper extends BaseMapper<Xxx>` | `DocumentMapper` |
| Entity | `Xxx`，`@TableName("表名")`，对应 MySQL 表 | `KbDocument` |
| 入参 DTO | `XxxDTO` 或 `XxxRequest` | `ChatRequest` |
| 出参 VO | `XxxVO` 或 `XxxResponse` | `ChatResponse` |

- 类名 PascalCase；方法/变量 camelCase；常量 UPPER_SNAKE_CASE；包名全小写。

## 5. 统一响应与错误码（契约核心）
- 所有 REST 接口返回 `com.cs.framework.common.R<T>`：成功 `R.ok(data)`，失败抛异常。
- **禁止**在 Controller 里 try-catch 手动拼错误响应——统一交给 `GlobalExceptionHandler`。
- 错误码 `com.cs.framework.common.ErrorCode` **分段**，各模块只在自己区间新增，避免并行冲突：
  - `0` 成功 ｜ `1xxx` 通用/框架 ｜ `2xxx` 知识库·入库 ｜ `3xxx` 检索·问答 ｜ `4xxx` 工单 ｜ `5xxx` 系统·统计
- 新增错误码：在对应段追加枚举值，并保持 code 唯一。

## 6. 异常处理
- 可预期业务错误：`throw new BizException(ErrorCode.XXX)`（或带自定义消息的重载）。
- 不吞异常、不用 `e.printStackTrace()`；统一用 `log`（`@Slf4j`）。
- 未预期异常由 `GlobalExceptionHandler` 兜底为 `1999`，对外不泄露内部细节。

## 7. REST API 设计规范
- 路径前缀：`/api/<module>/<resource>`，如 `/api/qa/chat`、`/api/kb/documents`、`/api/ticket`。
- 方法语义：GET 查询 / POST 新增或动作 / PUT 修改 / DELETE 删除。
- 流式回答用 **SSE**（`produces = text/event-stream`）。
- 分页统一返回 `R<PageVO<T>>`（PageVO 含 records/total/current/size）。
- 完整 endpoint 清单见 `contract/rest-api.md`（前端路4 据此 Mock 并行开发）。

## 8. 配置与密钥（D19）
- 配置写 `cs-bootstrap/src/main/resources/application.yml`；敏感值走**环境变量**。
- **API Key（DashScope/硅基流动/智谱）绝不硬编码、绝不入库**；本地用 `.env`（已 gitignore）。
- 新增配置项要在 yml 里加注释说明用途。

## 9. 数据库规范
- 表名/字段 `snake_case`；实体类 `camelCase`（MyBatis-Plus 自动映射）。
- 每张表含 `id BIGINT AUTO_INCREMENT`、`created_at`、（可变表含）`updated_at`。
- schema 变更：改 `sql/init/schema.sql` + 同步 `docs/DESIGN.md` 第 5 节。

## 10. Git 提交与分支规范（D18/D19）
- 分支（对应 4 路 worktree）：`feat/ingest-retrieval`、`feat/qa-chain`、`feat/ticket-stats-system`、`feat/frontend`。
- commit message：`<type>(<scope>): <subject>`，type ∈ feat/fix/docs/refactor/test/chore。
  - 例：`feat(knowledge): 实现 ES 向量+BM25 的 RRF 混合检索`
- **所有 commit/push/merge 由本人手动执行，AI 不自动提交**（D19）。

## 11. 并行开发纪律（阶段 B）
1. 各路**只改自己模块目录**；根 pom、cs-framework、本契约文件的变更集中到阶段 A 或经集成者。
2. 契约变更（R/ErrorCode/Service 接口/schema/API）→ 同步通知所有路 + 更新本文 + 更新 `contract/`。
3. 每路提交集成前：本模块 `mvn -pl <module> compile` 通过 + 自测通过。
4. 集成者（本人）负责合并、解冲突、端到端联调。

## 12. 注释与可讲性
- 类与公共方法写 Javadoc（职责、关键参数、返回）。
- 复杂逻辑（RRF 融合、问题重写、熔断降级）要注释清「**为什么这么做**」，不只是「做了什么」。
- 呼应项目最高目标：**每一处技术决策都要能在答辩/面试讲清楚**。
