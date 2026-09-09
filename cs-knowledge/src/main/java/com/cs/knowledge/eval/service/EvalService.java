package com.cs.knowledge.eval.service;

import com.cs.knowledge.eval.dto.EvalCaseUpsertRequest;
import com.cs.knowledge.eval.dto.EvalMetrics;
import com.cs.knowledge.eval.dto.EvalRunRequest;
import com.cs.knowledge.eval.entity.EvalCase;
import com.cs.knowledge.eval.entity.EvalResult;

import java.util.List;

/**
 * RAG 评估体系服务（路9，创新点3）——评测用例管理、评测执行（异步）、DISLIKE bad case 池回流。
 *
 * <p><b>为什么评估服务放 cs-knowledge 而非 cs-stats</b>：评估必须<b>驱动检索</b>——要用不同
 * 检索配置跑同一批评测集才能产出对比数据，必然要调 {@code RetrievalService}；而 D24 规定
 * cs-stats 只读直查表、不依赖 cs-qa/cs-ticket/cs-knowledge 的 Service，若评估放 cs-stats
 * 就必须破坏 D24 的依赖方向。分层：cs-knowledge 承担评估执行与落库（如同单元测试属于被测
 * 模块），cs-stats 只做只读直查 eval_result 供管理后台看板展示历史结果，D24 无损。</p>
 */
public interface EvalService {

    /** 新增评测用例（服务层手动校验，不用 @Valid，见 DEF-088 待拍板） */
    EvalCase createCase(EvalCaseUpsertRequest request);

    /** 用例列表（kbId 为 null 查全部） */
    List<EvalCase> listCases(Long kbId);

    /**
     * 触发一次评测运行（异步）：先以 RUNNING 同步落库拿到 run id 立即返回，
     * 执行在单线程 executor 里完成，结果回写 eval_result。
     *
     * @return run id（即 eval_result.id）
     */
    Long triggerRun(EvalRunRequest request);

    /** 评测历史（按 run id 倒序，limit 条） */
    List<EvalResult> listRuns(int limit);

    /** 运行详情；不存在抛 NOT_FOUND */
    EvalResult getRun(Long runId);

    /**
     * DISLIKE 反馈回流 bad case 池：feedback(type=DISLIKE) join chat_reference 拿到当时
     * 实际命中的 chunk/doc，聚合为 source=DISLIKE 的用例入池（幂等：按 note 记录的
     * message_id 去重）。评估执行默认排除 DISLIKE 用例（它们是「已知答得不好」的反例，
     * 无正向标注，参与 recall 反而虚高）。
     *
     * @return 新入池用例数
     */
    int syncDislikeCases();
}
