package com.cs.knowledge.eval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.knowledge.dto.RetrievalMode;
import com.cs.knowledge.dto.RetrievalRequest;
import com.cs.knowledge.dto.RetrievedChunk;
import com.cs.knowledge.entity.KbChunkMeta;
import com.cs.knowledge.eval.dto.EvalCaseUpsertRequest;
import com.cs.knowledge.eval.dto.EvalMetrics;
import com.cs.knowledge.eval.dto.EvalRunRequest;
import com.cs.knowledge.eval.dto.DislikeFeedbackRow;
import com.cs.knowledge.eval.entity.EvalCase;
import com.cs.knowledge.eval.entity.EvalResult;
import com.cs.knowledge.eval.mapper.EvalCaseMapper;
import com.cs.knowledge.eval.mapper.EvalResultMapper;
import com.cs.knowledge.eval.service.EvalService;
import com.cs.knowledge.mapper.KbChunkMetaMapper;
import com.cs.knowledge.service.RetrievalService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * RAG 评估体系实现（路9，创新点3）。
 *
 * <p>指标公式与宏平均口径见 {@link EvalMetrics} Javadoc；评估服务归属 cs-knowledge 的
 * 架构裁决见 {@link EvalService} Javadoc。</p>
 *
 * <p><b>异步执行设计</b>：评测会打 ES（每用例 1~2 次查询）与 embedding/rerank 模型调用，
 * 用例一多耗时不可控，绝不能阻塞 HTTP 请求线程。做法：triggerRun 先以 RUNNING 同步落库
 * 拿到 run id 立即返回，执行提交给单线程 executor（常量放代码、不新增配置键）；单线程
 * 串行使多次触发天然排队、同一时刻至多一个评测在打 ES/模型，避免压垮免费额度模型。
 * 异步线程不读 SecurityContext，无鉴权上下文丢失问题。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvalServiceImpl implements EvalService {

    private static final Set<String> VALID_SOURCES = Set.of("MANUAL", "SAMPLED", "DISLIKE");
    private static final String SOURCE_DISLIKE = "DISLIKE";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_FAILED = "FAILED";
    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_TOP_N = 20;
    private static final int MAX_LIMIT = 100;

    private final EvalCaseMapper evalCaseMapper;
    private final EvalResultMapper evalResultMapper;
    private final RetrievalService retrievalService;
    private final KbChunkMetaMapper kbChunkMetaMapper;
    private final ObjectMapper objectMapper;

    private static final ExecutorService EVAL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "eval-runner");
        t.setDaemon(true);
        return t;
    });

    @Override
    public EvalCase createCase(EvalCaseUpsertRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "question 不能为空");
        }
        String source = request.getSource() == null || request.getSource().isBlank()
                ? "MANUAL" : request.getSource().trim().toUpperCase();
        if (!VALID_SOURCES.contains(source)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "source 取值非法: " + request.getSource());
        }
        EvalCase c = new EvalCase();
        c.setKbId(request.getKbId());
        c.setQuestion(request.getQuestion().trim());
        c.setExpectedChunkIds(toJson(request.getExpectedChunkIds()));
        c.setExpectedDocIds(toJson(request.getExpectedDocIds()));
        c.setSource(source);
        c.setNote(request.getNote());
        evalCaseMapper.insert(c);
        return c;
    }

    @Override
    public List<EvalCase> listCases(Long kbId) {
        LambdaQueryWrapper<EvalCase> w = new LambdaQueryWrapper<EvalCase>().orderByDesc(EvalCase::getId);
        if (kbId != null) {
            w.eq(EvalCase::getKbId, kbId);
        }
        return evalCaseMapper.selectList(w);
    }

    @Override
    public Long triggerRun(EvalRunRequest request) {
        RetrievalRunParams params = resolveParams(request);
        List<EvalCase> cases = loadEvaluableCases(request);

        EvalResult run = new EvalResult();
        run.setKbId(request.getKbId());
        run.setMode(params.mode().name());
        run.setCaseCount(cases.size());
        run.setTopK(params.topK());
        run.setStatus(STATUS_RUNNING);
        evalResultMapper.insert(run);

        EVAL_EXECUTOR.submit(() -> executeRun(run.getId(), params, cases));
        log.info("评测运行已受理: runId={} mode={} caseCount={} topK={}",
                run.getId(), params.mode(), cases.size(), params.topK());
        return run.getId();
    }

    @Override
    public List<EvalResult> listRuns(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "limit 取值 1~" + MAX_LIMIT);
        }
        // limit 已校验为纯 int，last() 无注入面
        return evalResultMapper.selectList(new LambdaQueryWrapper<EvalResult>()
                .orderByDesc(EvalResult::getId)
                .last("LIMIT " + limit));
    }

    @Override
    public EvalResult getRun(Long runId) {
        EvalResult run = evalResultMapper.selectById(runId);
        if (run == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评测运行不存在: " + runId);
        }
        return run;
    }

    @Override
    public int syncDislikeCases() {
        List<DislikeFeedbackRow> rows = evalCaseMapper.selectDislikeFeedbackRows();
        // 一条点踩消息（含 N 条引用）聚合为一条用例
        Map<Long, List<DislikeFeedbackRow>> byMessage = rows.stream()
                .collect(Collectors.groupingBy(DislikeFeedbackRow::getMessageId,
                        LinkedHashMap::new, Collectors.toList()));

        int inserted = 0;
        int skippedNoQuestion = 0;
        for (Map.Entry<Long, List<DislikeFeedbackRow>> e : byMessage.entrySet()) {
            Long messageId = e.getKey();
            String note = "DISLIKE message_id=" + messageId;
            Long exists = evalCaseMapper.selectCount(new LambdaQueryWrapper<EvalCase>()
                    .eq(EvalCase::getSource, SOURCE_DISLIKE)
                    .eq(EvalCase::getNote, note));
            if (exists != null && exists > 0) {
                continue;   // 幂等：重复点踩/重复回流不重复入池
            }
            String question = e.getValue().stream()
                    .map(DislikeFeedbackRow::getQuestion)
                    .filter(q -> q != null && !q.isBlank())
                    .findFirst().orElse(null);
            if (question == null) {
                skippedNoQuestion++;
                continue;
            }
            List<String> chunkIds = e.getValue().stream()
                    .map(DislikeFeedbackRow::getEsChunkId)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct().collect(Collectors.toList());
            List<Long> docIds = e.getValue().stream()
                    .map(DislikeFeedbackRow::getDocId)
                    .filter(id -> id != null)
                    .distinct().collect(Collectors.toList());

            EvalCase c = new EvalCase();
            c.setQuestion(question);
            c.setExpectedChunkIds(toJson(chunkIds));
            c.setExpectedDocIds(toJson(docIds));
            c.setSource(SOURCE_DISLIKE);
            c.setNote(note);
            evalCaseMapper.insert(c);
            inserted++;
        }
        log.info("DISLIKE bad case 回流完成: 新入池 {} 条, 跳过(找不到提问) {} 条", inserted, skippedNoQuestion);
        return inserted;
    }

    // ==================== 评测执行 ====================

    /** 一次运行解析后的执行参数（record 避免 executor lambda 捕获可变对象） */
    private record RetrievalRunParams(RetrievalMode mode, int topK, int vectorTopN, int keywordTopN,
                                      Double minScore, Long kbId) {
    }

    private RetrievalRunParams resolveParams(EvalRunRequest request) {
        return new RetrievalRunParams(
                parseMode(request.getMode()),
                request.getTopK() != null && request.getTopK() > 0 ? request.getTopK() : DEFAULT_TOP_K,
                request.getVectorTopN() != null && request.getVectorTopN() > 0 ? request.getVectorTopN() : DEFAULT_TOP_N,
                request.getKeywordTopN() != null && request.getKeywordTopN() > 0 ? request.getKeywordTopN() : DEFAULT_TOP_N,
                request.getMinScore(),
                request.getKbId());
    }

    private RetrievalMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return RetrievalMode.HYBRID_RERANK;
        }
        try {
            return RetrievalMode.valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "mode 取值非法: " + mode);
        }
    }

    /**
     * 加载可评测用例：默认排除 DISLIKE（bad case 池是「已知答得不好」的反例，无正向标注，
     * 参与 recall 反而虚高，见 {@link EvalService#syncDislikeCases}）。
     *
     * <p>显式指定 caseIds 时<b>只按 id 集合取用例、不再按 kbId 过滤</b>：调用方已精确圈定
     * 范围（如「把无库标注的用例强行在指定库下检索」的跨库污染验证），否则会静默取到
     * 0 条用例（实测踩过：mini 用例无 kbId + run.kbId=1 被过滤成 cases=0）。</p>
     */
    private List<EvalCase> loadEvaluableCases(EvalRunRequest request) {
        boolean explicitCases = request.getCaseIds() != null && !request.getCaseIds().isEmpty();
        LambdaQueryWrapper<EvalCase> w = new LambdaQueryWrapper<EvalCase>()
                .ne(EvalCase::getSource, SOURCE_DISLIKE)
                .orderByAsc(EvalCase::getId);
        if (explicitCases) {
            w.in(EvalCase::getId, request.getCaseIds());
        } else if (request.getKbId() != null) {
            w.eq(EvalCase::getKbId, request.getKbId());
        }
        return evalCaseMapper.selectList(w);
    }

    private void executeRun(Long runId, RetrievalRunParams params, List<EvalCase> cases) {
        try {
            EvalMetrics m = computeMetrics(params, cases);
            EvalResult update = new EvalResult();
            update.setId(runId);
            update.setStatus(STATUS_DONE);
            update.setRecallAtK(m.getRecallAtK());
            update.setPrecisionAtK(m.getPrecisionAtK());
            update.setMrr(m.getMrr());
            update.setNdcg(m.getNdcg());
            update.setHitRate(m.getHitRate());
            update.setRefPrecision(m.getRefPrecision());
            evalResultMapper.updateById(update);
            log.info("评测完成: runId={} evaluatedCases={} recall@k={} precision@k={} mrr={} ndcg={} hitRate={} refPrecision={}",
                    runId, m.getEvaluatedCases(), m.getRecallAtK(), m.getPrecisionAtK(),
                    m.getMrr(), m.getNdcg(), m.getHitRate(), m.getRefPrecision());
        } catch (Exception e) {
            log.error("评测运行失败: runId={}", runId, e);
            EvalResult update = new EvalResult();
            update.setId(runId);
            update.setStatus(STATUS_FAILED);
            update.setErrorMsg(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 512));
            evalResultMapper.updateById(update);
        }
    }

    /**
     * 跑用例集合并计算指标（公式见 {@link EvalMetrics}）。
     *
     * <p>每次运行重新检索：评测驱动 {@link RetrievalService} 按 mode 逐用例调用——这就是
     * 「评估体系驱动检索」的含义，也是消融对比数据的来源。</p>
     */
    private EvalMetrics computeMetrics(RetrievalRunParams params, List<EvalCase> cases) {
        double recallSum = 0, precisionSum = 0, mrrSum = 0, ndcgSum = 0, refSum = 0;
        int evaluated = 0, hitCases = 0, refEvaluated = 0;

        for (EvalCase c : cases) {
            List<String> expectedChunks = parseStringList(c.getExpectedChunkIds());
            List<Long> expectedDocs = parseLongList(c.getExpectedDocIds());
            if (expectedChunks.isEmpty() && expectedDocs.isEmpty()) {
                continue;   // 无标注用例不参与指标平均
            }
            RetrievalRequest rr = RetrievalRequest.builder()
                    .query(c.getQuestion())
                    // 检索库归口：用例自标注 kbId 优先，否则随运行指定的 kbId（平行库对比实验按 run 指定）
                    .kbId(c.getKbId() != null ? c.getKbId() : params.kbId())
                    .mode(params.mode())
                    .topK(params.topK())
                    .vectorTopN(params.vectorTopN())
                    .keywordTopN(params.keywordTopN())
                    .minScore(params.minScore())
                    .build();
            List<RetrievedChunk> results = retrievalService.hybridRetrieve(rr);

            boolean chunkLevel = !expectedChunks.isEmpty();
            Set<String> eChunks = new HashSet<>(expectedChunks);
            Set<Long> eDocs = new HashSet<>(expectedDocs);
            Predicate<RetrievedChunk> hitPred = chunkLevel
                    ? r -> eChunks.contains(r.getChunkId())
                    : r -> r.getDocId() != null && eDocs.contains(r.getDocId());

            int n = results.size();
            int hitCount = 0;
            int firstHitRank = 0;
            double dcg = 0;
            for (int i = 0; i < n; i++) {
                if (hitPred.test(results.get(i))) {
                    hitCount++;
                    if (firstHitRank == 0) {
                        firstHitRank = i + 1;
                    }
                    dcg += 1.0 / log2(i + 2);
                }
            }

            evaluated++;
            // recall@k：chunk 级用召回条数 / |E|；doc 级用「召回的期望文档数」（多 chunk 同文档不重复计）
            if (chunkLevel) {
                recallSum += (double) hitCount / eChunks.size();
            } else {
                long distinctDocs = results.stream().map(RetrievedChunk::getDocId)
                        .filter(id -> id != null && eDocs.contains(id)).distinct().count();
                recallSum += (double) distinctDocs / eDocs.size();
            }
            precisionSum += n == 0 ? 0 : (double) hitCount / n;
            mrrSum += firstHitRank == 0 ? 0 : 1.0 / firstHitRank;
            int idealCount = chunkLevel ? eChunks.size() : eDocs.size();
            double idcg = 0;
            for (int i = 0; i < Math.min(idealCount, params.topK()); i++) {
                idcg += 1.0 / log2(i + 2);
            }
            ndcgSum += idcg == 0 ? 0 : dcg / idcg;
            if (hitCount >= 1) {
                hitCases++;
            }

            // ref_precision（文档粒度）：expected_doc_ids 缺失时由 chunk ids 反查 kb_chunk_meta 补齐
            Set<Long> refDocs = new HashSet<>(expectedDocs);
            if (refDocs.isEmpty() && chunkLevel) {
                List<KbChunkMeta> metas = kbChunkMetaMapper.selectList(
                        new LambdaQueryWrapper<KbChunkMeta>().in(KbChunkMeta::getEsChunkId, expectedChunks));
                metas.forEach(meta -> refDocs.add(meta.getDocId()));
            }
            if (!refDocs.isEmpty()) {
                refEvaluated++;
                long refHits = results.stream()
                        .filter(r -> r.getDocId() != null && refDocs.contains(r.getDocId()))
                        .count();
                refSum += n == 0 ? 0 : (double) refHits / n;
            }
        }

        EvalMetrics m = new EvalMetrics();
        m.setEvaluatedCases(evaluated);
        if (evaluated > 0) {
            m.setRecallAtK(recallSum / evaluated);
            m.setPrecisionAtK(precisionSum / evaluated);
            m.setMrr(mrrSum / evaluated);
            m.setNdcg(ndcgSum / evaluated);
            m.setHitRate((double) hitCases / evaluated);
            m.setRefPrecision(refEvaluated == 0 ? null : refSum / refEvaluated);
        }
        return m;
    }

    private double log2(int x) {
        return Math.log(x) / Math.log(2);
    }

    // ==================== JSON / 通用小工具 ====================

    private String toJson(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标注序列化失败: " + e.getMessage());
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("expected_chunk_ids 解析失败, 忽略该标注: {}", json);
            return List.of();
        }
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("expected_doc_ids 解析失败, 忽略该标注: {}", json);
            return List.of();
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
