package com.cs.knowledge.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.cs.infra.ai.rerank.RerankRequest;
import com.cs.infra.ai.rerank.RerankResult;
import com.cs.infra.ai.rerank.RerankService;
import com.cs.knowledge.dto.RetrievalMode;
import com.cs.knowledge.dto.RetrievalRequest;
import com.cs.knowledge.dto.RetrievedChunk;
import com.cs.knowledge.service.RetrievalService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 混合检索实现（DESIGN 4.3 检索漏斗）：
 * <pre>
 *   query → bge-m3 向量化
 *     → 向量 kNN 召回 ∥ BM25 关键词召回（IK 分词）
 *     → 手动 RRF 倒数排名融合去重
 *     → bge-reranker 精排 → TopK
 * </pre>
 *
 * <p><b>为什么手动 RRF 而非 ES 原生 retriever</b>：① 融合逻辑可控、原理可讲清（答辩/面试）；
 * ② 便于创新点1 的调优对比实验（自由调 rank_constant、召回预算、纯向量/纯BM25/混合对比）；
 * ③ kNN + match 是 ES client 最成熟的 API。</p>
 *
 * <p>RRF 公式：score(d) = Σ_over_channels 1/(k + rank_channel(d))，只用排名不用原始分，规避量纲差异。</p>
 *
 * <p><b>检索侧 embedding 失败的降级（路7 收敛）</b>：检索侧没有向量就没法 kNN，无法像 rerank
 * 那样等价替代，但也不应像原实现那样整链 catch 返回空——空结果会被上游误判为「无召回」而
 * 建工单转人工。正确行为是退化为<b>纯 BM25 检索</b>（单通道 RRF 退化为原序）并记录日志，
 * 召回质量下降但服务可用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private static final String INDEX = "cs_knowledge_chunk";
    /** RRF 排名常数 k：越大则高排名权重差异越平滑（创新点1 可调参实验） */
    private static final int RRF_K = 60;

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient esClient;
    private final RerankService rerankService;

    @Override
    public List<RetrievedChunk> hybridRetrieve(RetrievalRequest request) {
        try {
            String query = request.getQuery();

            // 0) 消融通道开关（路9）：null 视为 HYBRID_RERANK（生产默认），缺省行为与改造前逐条一致
            RetrievalMode mode = RetrievalMode.ofOrDefault(request.getMode());

            // 1) query 向量化（bge-m3 → 1024 维）。检索侧 embedding 失败无法降级成等价物
            //    （没有向量就没法 kNN），退化为纯 BM25 检索并记录，而不是返回空（空会被上游
            //    误判为「无召回」而建工单转人工）。纯 BM25 通道不需要向量，跳过向量化省一次模型调用
            float[] queryVector = null;
            if (mode != RetrievalMode.BM25) {
                try {
                    queryVector = embeddingModel.embed(query);
                } catch (Exception embedEx) {
                    log.warn("query 向量化失败，退化为纯 BM25 检索（向量通道跳过）: {}", embedEx.getMessage());
                }
            }

            // 2) 向量 kNN 召回 + 3) BM25 关键词召回。
            //    kbId 非 null 时两通道都加 metadata.kb_id term filter（路9 启用，闭环 DEF-049）
            List<RetrievedChunk> vectorHits = queryVector != null
                    ? knnSearch(queryVector, request.getVectorTopN(), request.getKbId())
                    : List.of();
            List<RetrievedChunk> keywordHits = bm25Search(query, request.getKeywordTopN(), request.getKbId());

            // 4) 通道裁剪 + RRF 融合去重（单通道时 RRF 退化为原序，分数为排名分）
            List<RetrievedChunk> fused = switch (mode) {
                case VECTOR -> rrfFuse(vectorHits, List.of());
                case BM25 -> rrfFuse(List.of(), keywordHits);
                case HYBRID, HYBRID_RERANK -> rrfFuse(vectorHits, keywordHits);
            };
            if (fused.isEmpty()) {
                return List.of();
            }

            // 5) Rerank 精排取 TopK（只在 HYBRID_RERANK 通道生效；enableRerank 是其既有 kill-switch）
            List<RetrievedChunk> result = mode == RetrievalMode.HYBRID_RERANK
                    ? rerank(query, fused, request.getTopK(), request.isEnableRerank())
                    : fused.stream().limit(request.getTopK()).collect(Collectors.toList());

            // 6) minScore 过滤（路9 启用，闭环 DEF-049）：阈值作用在最终排序分上
            //    （rerankScore 优先、否则 RRF 分，与 ReferenceVO.score 口径一致）；null 不过滤
            if (request.getMinScore() != null) {
                double threshold = request.getMinScore();
                result = result.stream()
                        .filter(c -> (c.getRerankScore() != null ? c.getRerankScore() : c.getScore()) >= threshold)
                        .collect(Collectors.toList());
            }
            return result;
        } catch (Exception e) {
            log.error("混合检索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 向量 kNN 检索（embedding 字段，cosine 相似度）。
     *
     * <p>kbId 非 null 时加 metadata.kb_id term filter（kNN 的 filter 在召回阶段收窄候选，
     * 不影响相似度打分）；kbId 为 null 时不加 filter，与既有行为逐字节一致。</p>
     */
    private List<RetrievedChunk> knnSearch(float[] vector, int topN, Long kbId) throws Exception {
        List<Float> queryVector = new ArrayList<>(vector.length);
        for (float v : vector) {
            queryVector.add(v);
        }
        SearchResponse<JsonNode> resp = esClient.search(s -> {
            s.index(INDEX)
                    .size(topN)
                    .knn(k -> {
                        k.field("embedding")
                                .queryVector(queryVector)
                                .k(topN)
                                .numCandidates(topN * 5);
                        if (kbId != null) {
                            // metadata.kb_id 是 keyword 类型，值必须传字符串（与 metadata.doc_id 同理）
                            k.filter(f -> f.term(t -> t.field("metadata.kb_id").value(String.valueOf(kbId))));
                        }
                        return k;
                    });
            return s;
        }, JsonNode.class);
        return toChunks(resp);
    }

    /**
     * BM25 关键词检索（content 字段，IK 分词）。
     *
     * <p>kbId 非 null 时包一层 bool + filter（must 保持原 match 打分，filter 只收窄候选、
     * 不参与相关性打分）；kbId 为 null 时保持原有裸 match 查询，与既有行为逐字节一致。</p>
     */
    private List<RetrievedChunk> bm25Search(String query, int topN, Long kbId) throws Exception {
        SearchResponse<JsonNode> resp = esClient.search(s -> {
            s.index(INDEX).size(topN);
            if (kbId != null) {
                s.query(q -> q.bool(b -> b
                        .must(m -> m.match(mq -> mq.field("content").query(query)))
                        .filter(f -> f.term(t -> t.field("metadata.kb_id").value(String.valueOf(kbId))))));
            } else {
                s.query(q -> q.match(m -> m.field("content").query(query)));
            }
            return s;
        }, JsonNode.class);
        return toChunks(resp);
    }

    /** 解析 ES 命中为 RetrievedChunk（保留各自通道的原始排序，供 RRF 用排名） */
    private List<RetrievedChunk> toChunks(SearchResponse<JsonNode> resp) {
        List<RetrievedChunk> list = new ArrayList<>();
        for (Hit<JsonNode> hit : resp.hits().hits()) {
            JsonNode src = hit.source();
            if (src == null) {
                continue;
            }
            JsonNode meta = src.path("metadata");
            list.add(RetrievedChunk.builder()
                    .chunkId(meta.path("chunk_id").asText(hit.id()))
                    .docId(meta.path("doc_id").asLong())
                    .docTitle(meta.path("doc_title").asText())
                    .content(src.path("content").asText())
                    .score(hit.score() != null ? hit.score() : 0.0)
                    .seq(meta.path("seq").asInt())
                    .build());
        }
        return list;
    }

    /** RRF 倒数排名融合：对每个 chunk 累加各通道的 1/(k+rank)，去重后按融合分降序 */
    private List<RetrievedChunk> rrfFuse(List<RetrievedChunk> vectorHits, List<RetrievedChunk> keywordHits) {
        Map<String, RetrievedChunk> byId = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        accumulate(vectorHits, byId, scores);
        accumulate(keywordHits, byId, scores);
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RetrievedChunk c = byId.get(e.getKey());
                    c.setScore(e.getValue());   // 融合分覆盖，供后续参考
                    return c;
                })
                .collect(Collectors.toList());
    }

    private void accumulate(List<RetrievedChunk> hits, Map<String, RetrievedChunk> byId, Map<String, Double> scores) {
        for (int rank = 0; rank < hits.size(); rank++) {
            RetrievedChunk c = hits.get(rank);
            String id = c.getChunkId();
            scores.merge(id, 1.0 / (RRF_K + rank + 1), Double::sum);
            byId.putIfAbsent(id, c);
        }
    }

    /** Rerank 精排；关闭或失败时降级为 RRF 顺序取 TopK（面向失败设计） */
    private List<RetrievedChunk> rerank(String query, List<RetrievedChunk> fused, int topK, boolean enable) {
        if (!enable) {
            return fused.stream().limit(topK).collect(Collectors.toList());
        }
        List<String> docs = fused.stream().map(RetrievedChunk::getContent).collect(Collectors.toList());
        RerankResult rr = rerankService.rerank(RerankRequest.builder()
                .query(query)
                .documents(docs)
                .topN(Math.min(topK, docs.size()))
                .build());
        // Rerank 无结果（失败降级）→ 回退 RRF 顺序
        if (rr.getResults() == null || rr.getResults().isEmpty()) {
            return fused.stream().limit(topK).collect(Collectors.toList());
        }
        List<RetrievedChunk> result = new ArrayList<>();
        for (RerankResult.Item item : rr.getResults()) {
            int idx = item.getIndex();
            if (idx >= 0 && idx < fused.size()) {
                RetrievedChunk c = fused.get(idx);
                c.setRerankScore(item.getRelevanceScore());
                result.add(c);
            }
        }
        return result;
    }
}
