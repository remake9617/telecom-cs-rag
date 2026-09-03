package com.cs.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 混合检索请求 —— 路2(cs-qa) 调用路1(cs-knowledge) 的入参契约（冻结）。
 *
 * <p>对应 DESIGN 4.3 检索漏斗：向量 kNN 与 BM25 各自召回 topN，RRF 融合去重后 Rerank 取 topK。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalRequest {

    /** 检索词（一般为「问题重写」后的 query，而非用户原始输入） */
    private String query;

    /** 目标知识库 ID（MVP 单库，阶段二多库路由） */
    private Long kbId;

    /** 最终返回条数（Rerank 重排后取 TopK），默认 5 */
    @Builder.Default
    private int topK = 5;

    /** 向量通道召回预算（kNN 取 TopN），默认 20 */
    @Builder.Default
    private int vectorTopN = 20;

    /** 关键词通道召回预算（BM25 取 TopN），默认 20 */
    @Builder.Default
    private int keywordTopN = 20;

    /** 最低相关性分数过滤（低于则丢弃，可为 null 表示不过滤） */
    private Double minScore;

    /** 是否启用 Rerank 重排（bge-reranker-v2-m3），默认 true */
    @Builder.Default
    private boolean enableRerank = true;
}
