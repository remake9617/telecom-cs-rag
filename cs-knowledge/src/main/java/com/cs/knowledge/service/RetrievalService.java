package com.cs.knowledge.service;

import com.cs.knowledge.dto.RetrievalRequest;
import com.cs.knowledge.dto.RetrievedChunk;

import java.util.List;

/**
 * 混合检索服务 —— cs-knowledge（路1）暴露给 cs-qa（路2）的<b>冻结契约</b>。
 *
 * <p>这是阶段 B 并行开发的关键接口：路2 只依赖本接口编程，路1 负责实现，
 * 两路并行、最后合并即可对接（见 CONVENTIONS 第 11 节、DESIGN 第 13 节）。</p>
 *
 * <p>实现对应的检索漏斗（DESIGN 4.3）：
 * 向量 kNN ∥ BM25 并行召回 → RRF 倒数排名融合去重 → bge-reranker 重排取 TopK。</p>
 */
public interface RetrievalService {

    /**
     * 混合检索：从指定知识库召回与 query 最相关的知识片段。
     *
     * @param request 检索请求（query / kbId / topK / 召回预算 / 是否重排 等）
     * @return 按相关性降序排列的 chunk 列表；无命中时返回<b>空列表</b>（不返回 null）
     */
    List<RetrievedChunk> hybridRetrieve(RetrievalRequest request);
}
