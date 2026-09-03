package com.cs.infra.ai.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 重排结果 —— 每个 {@link Item} 给出「候选在入参 documents 中的下标」与「相关性分数」。
 *
 * <p>调用方（RetrievalService 实现）据 index 映射回原始 chunk，按 relevanceScore 降序取 TopK。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {

    /** 重排后的结果项（已按相关性降序） */
    private List<Item> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 对应入参 documents 的下标 */
        private int index;
        /** 相关性分数（bge-reranker 输出，越大越相关） */
        private double relevanceScore;
    }
}
