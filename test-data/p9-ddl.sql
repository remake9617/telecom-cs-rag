-- p9 路9 实测用 DDL：与 sql/init/schema.sql 保持一致，幂等
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 评测运行结果';

SHOW TABLES LIKE 'eval_%';
