package com.cs.infra.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RerankService 的硅基流动实现 —— 调用 bge-reranker-v2-m3（/v1/rerank，Jina/Cohere 兼容格式）。
 *
 * <p>复用 application.yml 中 spring.ai.openai.* 的硅基流动 base-url 与 api-key（同一供应商）；
 * 用 Spring 6 的 RestClient 同步调用。</p>
 *
 * <p><b>面向失败设计</b>：Rerank 属精排增强，调用失败时返回空结果，由调用方降级为
 * 「沿用 RRF 融合顺序」，不阻断检索主链（DESIGN 4.5）。</p>
 */
@Slf4j
@Service
public class SiliconFlowRerankService implements RerankService {

    private final RestClient restClient;

    public SiliconFlowRerankService(
            @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public RerankResult rerank(RerankRequest request) {
        List<String> documents = request.getDocuments();
        if (documents == null || documents.isEmpty()) {
            return RerankResult.builder().results(List.of()).build();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("query", request.getQuery());
        body.put("documents", documents);
        body.put("top_n", request.getTopN());

        try {
            JsonNode resp = restClient.post()
                    .uri("/v1/rerank")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            List<RerankResult.Item> items = new ArrayList<>();
            if (resp != null && resp.has("results")) {
                for (JsonNode node : resp.get("results")) {
                    items.add(RerankResult.Item.builder()
                            .index(node.path("index").asInt())
                            .relevanceScore(node.path("relevance_score").asDouble())
                            .build());
                }
            }
            return RerankResult.builder().results(items).build();
        } catch (Exception e) {
            log.error("Rerank 调用失败，降级为不重排（沿用 RRF 顺序）: {}", e.getMessage());
            return RerankResult.builder().results(List.of()).build();
        }
    }
}
