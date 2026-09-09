package com.cs.knowledge.eval.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.knowledge.eval.dto.EvalCaseUpsertRequest;
import com.cs.knowledge.eval.dto.EvalRunRequest;
import com.cs.knowledge.eval.entity.EvalCase;
import com.cs.knowledge.eval.entity.EvalResult;
import com.cs.knowledge.eval.service.EvalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 评估体系端点（路9，管理员）。
 *
 * <p><b>注意：契约提案状态</b>——本类 5 个端点（runs 触发/历史/详情 + cases 新增/列表 +
 * DISLIKE 回流）的完整契约提案（路径/方法/权限/入参/出参）已交统筹转用户确认，
 * 由统筹落盘 contract/rest-api.md。实现先行、契约待批，属启动包第 6 节明确授权的顺序。</p>
 *
 * <p>全部端点首行 {@code SecurityUtils.requireAdmin()}（评测是管理功能，口径同统计看板）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    /** 触发一次评测运行（异步，立即返回 runId，结果查 runs/{id}） */
    @PostMapping("/runs")
    public R<Map<String, Object>> triggerRun(@RequestBody(required = false) EvalRunRequest request) {
        SecurityUtils.requireAdmin();
        Long runId = evalService.triggerRun(request == null ? new EvalRunRequest() : request);
        return R.ok(Map.of("runId", runId, "status", "RUNNING"));
    }

    /** 评测历史（按 run 倒序，limit 条） */
    @GetMapping("/runs")
    public R<List<EvalResult>> listRuns(@RequestParam(defaultValue = "20") int limit) {
        SecurityUtils.requireAdmin();
        return R.ok(evalService.listRuns(limit));
    }

    /** 运行详情（含六项指标） */
    @GetMapping("/runs/{id}")
    public R<EvalResult> getRun(@PathVariable Long id) {
        SecurityUtils.requireAdmin();
        return R.ok(evalService.getRun(id));
    }

    /** 新增评测用例 */
    @PostMapping("/cases")
    public R<EvalCase> createCase(@RequestBody EvalCaseUpsertRequest request) {
        SecurityUtils.requireAdmin();
        return R.ok(evalService.createCase(request));
    }

    /** 用例列表（kbId 为 null 查全部） */
    @GetMapping("/cases")
    public R<List<EvalCase>> listCases(@RequestParam(required = false) Long kbId) {
        SecurityUtils.requireAdmin();
        return R.ok(evalService.listCases(kbId));
    }

    /** DISLIKE 反馈回流 bad case 池（幂等，返回新入池条数） */
    @PostMapping("/cases/dislike-sync")
    public R<Integer> syncDislikeCases() {
        SecurityUtils.requireAdmin();
        return R.ok(evalService.syncDislikeCases());
    }
}
