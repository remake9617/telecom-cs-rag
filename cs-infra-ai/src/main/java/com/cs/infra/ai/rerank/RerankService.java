package com.cs.infra.ai.rerank;

/**
 * 重排服务 —— cs-infra-ai 对外暴露的 Rerank 能力契约（冻结）。
 *
 * <p>由 cs-knowledge 的 {@code RetrievalService} 实现调用：对「向量 kNN + BM25 + RRF 融合」
 * 后的候选池做精排，显著提升 TopK 质量（创新点 1 混合检索的关键一环）。</p>
 *
 * <p><b>为什么自定义</b>：Spring AI 提供了 ChatModel / EmbeddingModel 标准抽象，
 * 但<b>没有</b> Rerank 抽象，故本项目自行封装硅基流动 bge-reranker-v2-m3。</p>
 *
 * <p>实现（M1）：走 OpenAI 兼容协议 POST {base-url}/v1/rerank，
 * base-url 与 api-key 复用 application.yml 中 spring.ai.openai.* 的硅基流动配置。</p>
 */
public interface RerankService {

    /**
     * 对候选文档按与 query 的相关性重排。
     *
     * @param request 重排请求（query + documents + topN）
     * @return 重排结果（index + relevanceScore，按分数降序）；documents 为空时返回空结果
     */
    RerankResult rerank(RerankRequest request);
}
