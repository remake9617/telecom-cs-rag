package com.cs.knowledge.eval.dto;

import lombok.Data;

/**
 * DISLIKE 反馈回流行（路9 bad case 池采样用，对应 {@code EvalCaseMapper#selectDislikeFeedbackRows}）。
 *
 * <p>一条点踩消息若有 N 条引用会产生 N 行（LEFT JOIN chat_reference），服务层按 messageId 分组
 * 聚合成一条用例。question 取同会话中该消息之前最近的一条 user 消息（点踩的是 assistant 回答，
 * 评测需要的是引发该回答的问题）。</p>
 */
@Data
public class DislikeFeedbackRow {

    /** feedback.id */
    private Long feedbackId;

    /** 被点踩的 assistant 消息 id */
    private Long messageId;

    /** 同会话中该消息之前最近的 user 消息内容（无则为 null，服务层跳过该行） */
    private String question;

    /** 当时实际命中的 ES chunk id（chat_reference.es_chunk_id，可空） */
    private String esChunkId;

    /** 当时实际命中的文档 id（chat_reference.doc_id，可空） */
    private Long docId;
}
