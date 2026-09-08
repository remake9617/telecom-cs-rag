package com.cs.infra.ai.rerank;

import com.cs.infra.ai.resilience.AiResilienceProperties;
import com.cs.infra.ai.resilience.CircuitBreaker;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
 * 「沿用 RRF 融合顺序」，不阻断检索主链（DESIGN 4.5）。原散落的裸 try-catch 已收敛进
 * 封装口径：连接/读取超时可配 + 独立熔断器（{@link CircuitBreaker}，与 Chat 供应商熔断
 * 互不影响）。A 轮验证实测触发过瞬时网络失败自动降级 RRF 的真实案例。</p>
 *
 * <p><b>为什么 rerank 不做重试</b>：检索主链是同步请求路径，重试会线性放大首 token 延迟
 * （最坏 +2×readTimeout）；瞬时抖动由超时兜底、持续故障由熔断快速拦截（OPEN 后直接跳过
 * 真实调用，用户走 RRF 顺序几乎无感）。</p>
 */
@Slf4j
@Service
public class SiliconFlowRerankService implements RerankService {

    private final RestClient restClient;
    /** rerank 独立熔断器：参数与 Chat 熔断器同源（cs.ai.resilience.circuit），互不影响 */
    private final CircuitBreaker breaker;

    public SiliconFlowRerankService(
            @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            AiResilienceProperties props) {
        // 原裸 RestClient 无超时（DEF-026 病灶之一），补上连接/读取超时（复用 cs.ai.resilience 配置）
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) props.getConnectTimeoutMs());
        rf.setReadTimeout((int) props.getCallTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(rf)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        AiResilienceProperties.Circuit c = props.getCircuit();
        this.breaker = new CircuitBreaker("rerank", c.getWindowSize(), c.getMinCalls(),
                c.getFailureRateThreshold(), c.getOpenDurationMs(),
                c.getHalfOpenMaxTrials(), c.getHalfOpenSuccessThreshold());
    }

    @Override
    public RerankResult rerank(RerankRequest request) {
        List<String> documents = request.getDocuments();
        if (documents == null || documents.isEmpty()) {
            return RerankResult.builder().results(List.of()).build();
        }

        // 熔断 OPEN 时快速降级：不真实调用，直接走 RRF 顺序（对用户几乎无感）
        if (!breaker.tryAcquire()) {
            log.warn("Rerank 熔断器 OPEN，快速降级为不重排（沿用 RRF 顺序）");
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
            breaker.onSuccess();
            return RerankResult.builder().results(items).build();
        } catch (Exception e) {
            breaker.onFailure();
            log.error("Rerank 调用失败，降级为不重排（沿用 RRF 顺序）: {}", e.getMessage());
            return RerankResult.builder().results(List.of()).build();
        }
    }
}
