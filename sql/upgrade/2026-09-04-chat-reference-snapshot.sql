-- =====================================================================
-- 迁移脚本：chat_reference 表追加引用快照列
-- 日期：2026-09-04
-- 关联：A 轮基础层改造（计划 §2.1.6）
-- =====================================================================
--
-- 【为什么需要本脚本】
-- sql/init/schema.sql 使用 CREATE TABLE IF NOT EXISTS，对已存在的数据库
-- 不会重新建表也不会加列。因此在 schema.sql 更新后，已有环境必须手动
-- 执行本脚本一次，使 chat_reference 表拥有 doc_title 和 chunk_text 列。
--
-- 【执行方式（二选一）】
-- 方式 A：在 VM 内通过 docker exec 执行
--   docker exec -i cs-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" cs_db < 2026-09-04-chat-reference-snapshot.sql
--
-- 方式 B：从 Windows 用 mysql 客户端连接
--   mysql -h 192.168.25.129 -P 13306 -uroot -p cs_db < sql/upgrade/2026-09-04-chat-reference-snapshot.sql
--
-- 【重复执行】
-- 若已执行过一次，再次运行会报 Duplicate column name 'doc_title'，
-- 属预期行为，无数据损坏风险，可安全忽略。
--
-- 【架构理由——引用快照语义】
-- chat_reference 记录的是「当时向用户展示了什么」，属审计快照，
-- 与 kb_chunk_meta 的「当前索引状态」是两种不同职责。
-- 冗余 doc_title + chunk_text 并不违反「MySQL 仅存元数据」原则：
--   - 写放大约 2.5KB/次问答（5 引用 × 500 字符），在 D5 的语料规模下可接受；
--   - 换来历史回放零 ES 往返，且 ES 不可用时历史接口不会 500；
--   - 溯源展示是高频读场景，避免 N+1 查询。
-- =====================================================================

USE cs_db;

ALTER TABLE chat_reference
    ADD COLUMN doc_title  VARCHAR(256) COMMENT '引用快照：文档标题（冗余，避免回放时 join）' AFTER es_chunk_id,
    ADD COLUMN chunk_text TEXT         COMMENT '引用快照：chunk 正文（冗余，回放时零查询还原原文预览）' AFTER doc_title;
