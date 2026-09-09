-- =====================================================================
-- 电信运营商智能客服系统 —— MySQL 初始化 schema（MVP 阶段）
-- 由 docker-compose 首次启动自动执行（挂载至 /docker-entrypoint-initdb.d）
-- 对应 DESIGN.md 第 5.1 节；引擎 InnoDB；字符集 utf8mb4
-- 阶段二表（trace_run/trace_node/intent_node/eval_result）见文件末尾预留说明
-- =====================================================================

CREATE DATABASE IF NOT EXISTS cs_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cs_db;

-- ==================== 系统：用户 / 角色 / 权限 ====================
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(64)  NOT NULL,
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 密文',
  nickname    VARCHAR(64),
  role        VARCHAR(32)  NOT NULL DEFAULT 'VISITOR' COMMENT 'VISITOR/AGENT/ADMIN',
  status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（访客/坐席/管理员）';

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT      PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL,
  code        VARCHAR(64) NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE IF NOT EXISTS sys_permission (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64)  NOT NULL,
  code        VARCHAR(128) NOT NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

-- ==================== 知识库：知识库 / 文档 / chunk 元数据 ====================
CREATE TABLE IF NOT EXISTS kb_knowledge_base (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  name            VARCHAR(128) NOT NULL,
  description     VARCHAR(512),
  embedding_model VARCHAR(64)  NOT NULL DEFAULT 'bge-m3',
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库（MVP 单库，阶段二多库路由）';

CREATE TABLE IF NOT EXISTS kb_document (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  kb_id       BIGINT       NOT NULL,
  title       VARCHAR(256) NOT NULL,
  source_type VARCHAR(16)  NOT NULL COMMENT 'UPLOAD/URL',
  file_type   VARCHAR(16)  COMMENT 'MD/TXT/PDF/WORD/EXCEL/HTML',
  source_url  VARCHAR(512),
  chunk_count INT          NOT NULL DEFAULT 0,
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kb (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据';

CREATE TABLE IF NOT EXISTS kb_chunk_meta (
  id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
  doc_id       BIGINT      NOT NULL,
  es_chunk_id  VARCHAR(64) NOT NULL COMMENT 'ES 文档 _id',
  seq          INT         NOT NULL COMMENT 'chunk 序号',
  token_count  INT,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id),
  UNIQUE KEY uk_es_chunk (es_chunk_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='chunk 元数据（正文与向量在 ES）';

-- ==================== 会话：会话 / 消息 / 引用来源 ====================
CREATE TABLE IF NOT EXISTS chat_conversation (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  title          VARCHAR(256),
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_active_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

CREATE TABLE IF NOT EXISTS chat_message (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT       NOT NULL,
  role            VARCHAR(16)  NOT NULL COMMENT 'user/assistant',
  content         TEXT         NOT NULL,
  rewritten_query VARCHAR(512) COMMENT '问题重写结果',
  intent          VARCHAR(32)  COMMENT 'KB/TICKET/CHITCHAT',
  token_cost      INT,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';

CREATE TABLE IF NOT EXISTS chat_reference (
  id           BIGINT      PRIMARY KEY AUTO_INCREMENT,
  message_id   BIGINT      NOT NULL,
  doc_id       BIGINT,
  es_chunk_id  VARCHAR(64),
  doc_title    VARCHAR(256) COMMENT '引用快照：文档标题（冗余，避免回放时 join）',
  chunk_text   TEXT         COMMENT '引用快照：chunk 正文（冗余，回放时零查询还原原文预览）',
  score        DOUBLE      COMMENT 'RRF 融合分',
  rerank_score DOUBLE      COMMENT 'Rerank 重排分',
  KEY idx_msg (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回答引用来源（溯源）';

-- ==================== 工单 ====================
CREATE TABLE IF NOT EXISTS ticket (
  id              BIGINT        PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  conversation_id BIGINT,
  question        VARCHAR(1024) NOT NULL,
  ai_reason       VARCHAR(512)  COMMENT 'AI 判定转人工的原因',
  status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/REPLIED/CLOSED',
  reply           TEXT,
  handler_id      BIGINT        COMMENT '处理人（管理员）',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  replied_at      DATETIME,
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转人工工单';

-- ==================== 反馈 / 统计 / 审计 ====================
CREATE TABLE IF NOT EXISTS feedback (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  message_id BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  type       VARCHAR(16)  NOT NULL COMMENT 'LIKE/DISLIKE',
  comment    VARCHAR(512),
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_msg (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞点踩（仅落库）';

CREATE TABLE IF NOT EXISTS stat_snapshot (
  id            BIGINT   PRIMARY KEY AUTO_INCREMENT,
  stat_date     DATE     NOT NULL,
  ask_count     INT      NOT NULL DEFAULT 0,
  resolve_count INT      NOT NULL DEFAULT 0,
  ticket_count  INT      NOT NULL DEFAULT 0,
  hot_questions JSON     COMMENT '热点问题（Top N）',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统计快照';

CREATE TABLE IF NOT EXISTS eval_case (
  id                 BIGINT        PRIMARY KEY AUTO_INCREMENT,
  kb_id              BIGINT        COMMENT '目标知识库（null 表示不限定库，跨库用例）',
  question           VARCHAR(512)  NOT NULL COMMENT '评测问题',
  expected_chunk_ids JSON          COMMENT '期望命中的 ES chunk id 列表（细粒度标注）',
  expected_doc_ids   JSON          COMMENT '期望命中的文档 id 列表（粗粒度兜底标注）',
  source             VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SAMPLED/DISLIKE（点踩回流 bad case 池）',
  note               VARCHAR(512)  COMMENT '备注（DISLIKE 用例记录 message_id 幂等去重）',
  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_kb (kb_id),
  KEY idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测用例（路9 评估体系）';

CREATE TABLE IF NOT EXISTS eval_result (
  id             BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '即 run_id，一次评测运行的唯一标识',
  kb_id          BIGINT       COMMENT '目标知识库（null = 全库）',
  mode           VARCHAR(32)  NOT NULL COMMENT '检索通道：VECTOR/BM25/HYBRID/HYBRID_RERANK',
  case_count     INT          NOT NULL DEFAULT 0 COMMENT '本次加载的用例总数',
  status         VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/DONE/FAILED（异步执行状态）',
  recall_at_k    DOUBLE       COMMENT '召回率：期望命中 chunk 被 top-k 召回的比例（宏平均）',
  precision_at_k DOUBLE       COMMENT '准确率：top-k 结果中属于期望集合的比例（宏平均）',
  mrr            DOUBLE       COMMENT '平均倒数排名：第一条命中结果排名倒数的均值',
  ndcg           DOUBLE       COMMENT '归一化折损累计增益（二值相关）',
  hit_rate       DOUBLE       COMMENT '命中率：至少召回一条期望 chunk 的用例占比',
  ref_precision  DOUBLE       COMMENT '引用准确率：返回给用户的引用落在期望文档集合内的比例',
  top_k          INT          COMMENT '本次运行的 TopK',
  error_msg      VARCHAR(512) COMMENT 'FAILED 时的错误摘要',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_kb (kb_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测运行结果（检索侧确定性指标；幻觉率/忠实度等生成侧指标属批次1，刻意未建列，见 M5 路9 回执）';

CREATE TABLE IF NOT EXISTS audit_log (
  id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT,
  action     VARCHAR(64)  NOT NULL,
  target     VARCHAR(256),
  ip         VARCHAR(64),
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (user_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ==================== 初始数据：角色 ====================
INSERT INTO sys_role (name, code) VALUES
  ('管理员', 'ADMIN'), ('客服坐席', 'AGENT'), ('访客', 'VISITOR')
ON DUPLICATE KEY UPDATE name = VALUES(name);
-- 注：默认管理员账号 admin 待 M3 认证模块实现时用 BCrypt 生成密码后插入。

-- =====================================================================
-- 阶段二预留（暂不建表，M5+ 实现可观测/意图树时补充）：
--   trace_run / trace_node   全链路 Trace
--   intent_node              树形意图节点
-- 注：eval_case / eval_result 已于批次 0 Wave 2 路9 实建（上方），不再列入预留。
-- =====================================================================
