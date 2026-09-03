package com.cs.infra.ai.rerank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 重排请求 —— 封装硅基流动 bge-reranker-v2-m3 的入参。
 *
 * <p>Spring AI 无标准 Rerank 抽象，故本项目自定义（D11/D13）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankRequest {

    /** 查询词（与 documents 逐一算相关性） */
    private String query;

    /** 待重排的候选文档正文列表（一般为 RRF 融合后的候选池） */
    private List<String> documents;

    /** 返回相关性最高的前 N 个，默认 5 */
    @Builder.Default
    private int topN = 5;

    /** 重排模型，默认硅基流动 BAAI/bge-reranker-v2-m3（免费） */
    @Builder.Default
    private String model = "BAAI/bge-reranker-v2-m3";
}
