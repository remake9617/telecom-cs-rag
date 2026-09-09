package com.cs.knowledge.eval.dto;

import lombok.Data;

/**
 * 一次评测运行的六项检索侧指标（内存态计算结果，落库时拆到 eval_result 各列）。
 *
 * <p>全部指标为<b>可评测用例上的宏平均（macro-average）</b>：每个用例先各自算出指标值，
 * 再对用例求均值——避免大用例集淹没小用例集，且单用例可单独解释（论文口径）。</p>
 *
 * <p><b>指标公式（CONVENTIONS §12：公式必须写进 Javadoc，论文逐个交代）</b>。
 * 记单用例：E = 期望命中集合（expected_chunk_ids 优先；为空时降级用 expected_doc_ids），
 * R = 本次检索 top-k 结果，rank(r) = r 在 R 中的名次（1 起）：</p>
 * <ul>
 *   <li><b>recall@k</b> = |E ∩ R| / |E|；E 为空的用例跳过不参与平均。</li>
 *   <li><b>precision@k</b> = |{r ∈ R : r 命中 E}| / |R|（分母用实际返回数而非 k，
 *       不惩罚「ES 召回不足 k 条」，避免少召回与排序差被混为一谈）。</li>
 *   <li><b>mrr</b> = 1 / min{rank(r) : r 命中 E}，无命中记 0。</li>
 *   <li><b>ndcg</b>（二值相关）= DCG / IDCG，
 *       DCG = Σ_{i=1..|R|} rel_i / log2(i+1)，
 *       IDCG = Σ_{i=1..min(|E|, k)} 1 / log2(i+1)。</li>
 *   <li><b>hit_rate</b> = 「命中数 ≥ 1」的用例占比。</li>
 *   <li><b>ref_precision</b>（引用准确率，文档粒度）：用户核对溯源引用先看「文档对不对」，
 *       故按 docId 判定：|{r ∈ R : r.docId ∈ D}| / |R|，其中 D = expected_doc_ids
 *       （缺失时由 expected_chunk_ids 经 kb_chunk_meta.es_chunk_id → doc_id 反查补齐）；
 *       D 为空的用例跳过。它区别于 chunk 级 precision@k，直接对应答辩要讲的
 *       「引用溯源可信度」。</li>
 * </ul>
 *
 * <p>「r 命中 E」定义：E 为 chunk 集合时 r.chunkId ∈ E；E 为文档集合时 r.docId ∈ E。</p>
 */
@Data
public class EvalMetrics {

    /** 实际参与指标计算的可评测用例数（跳过无标注 / DISLIKE 后剩余） */
    private int evaluatedCases;

    private Double recallAtK;

    private Double precisionAtK;

    private Double mrr;

    private Double ndcg;

    private Double hitRate;

    private Double refPrecision;
}
