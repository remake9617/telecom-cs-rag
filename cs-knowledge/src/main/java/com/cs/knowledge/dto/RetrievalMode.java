package com.cs.knowledge.dto;

/**
 * 检索通道开关 —— 路9 RAG 评估体系的消融实验入口。
 *
 * <p><b>为什么不改架构只加开关（DECISIONS D7 边界）</b>：四条通道复用同一套 ES 查询与
 * RRF 融合代码，只是选择走哪几条通道，因此这是「在既有混合检索上做可配置裁剪」而非
 * 新架构；它让「检索改得好不好」可以被同一批评测集在四种 mode 下量化对比（消融实验）。</p>
 *
 * <p><b>缺省语义（回归判据，最重要）</b>：{@code null} 视为 {@link #HYBRID_RERANK}，
 * 即当前生产行为（kNN ∥ BM25 + RRF + Rerank）。缺省时检索结果必须与改造前逐条一致。</p>
 */
public enum RetrievalMode {

    /** 纯向量 kNN（单通道）：RRF 只有一个通道时退化为原序，分数为排名分而非原始相似度 */
    VECTOR,

    /** 纯 BM25 关键词（IK 分词，单通道） */
    BM25,

    /** kNN ∥ BM25 + 手动 RRF 融合，不做 Rerank 重排 */
    HYBRID,

    /** HYBRID 基础上再叠 bge-reranker 精排（生产默认通道） */
    HYBRID_RERANK;

    /**
     * 解析通道；{@code null}（未指定）回退生产默认 {@link #HYBRID_RERANK}。
     */
    public static RetrievalMode ofOrDefault(RetrievalMode mode) {
        return mode != null ? mode : HYBRID_RERANK;
    }
}
