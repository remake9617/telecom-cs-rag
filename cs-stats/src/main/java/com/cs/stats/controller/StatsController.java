package com.cs.stats.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.stats.dto.EvalRunVO;
import com.cs.stats.dto.HotQuestionVO;
import com.cs.stats.dto.OverviewVO;
import com.cs.stats.dto.TrendVO;
import com.cs.stats.service.EvalStatsService;
import com.cs.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计看板接口（对应 contract/rest-api.md 第 6 节 /api/stats，管理员）。
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final EvalStatsService evalStatsService;

    /** 概览看板 */
    @GetMapping("/overview")
    public R<OverviewVO> overview() {
        SecurityUtils.requireAdmin();
        return R.ok(statsService.overview());
    }

    /** 热点问题 TopN */
    @GetMapping("/hot-questions")
    public R<List<HotQuestionVO>> hotQuestions(@RequestParam(defaultValue = "10") int limit) {
        SecurityUtils.requireAdmin();
        return R.ok(statsService.hotQuestions(limit));
    }

    /** 近 N 天趋势 */
    @GetMapping("/trend")
    public R<List<TrendVO>> trend(@RequestParam(defaultValue = "7") int days) {
        SecurityUtils.requireAdmin();
        return R.ok(statsService.trend(days));
    }

    /** 历史评测结果（路9 评估体系的看板读侧，只读直查 eval_result，D24 无损） */
    @GetMapping("/eval-runs")
    public R<List<EvalRunVO>> evalRuns(@RequestParam(defaultValue = "20") int limit) {
        SecurityUtils.requireAdmin();
        return R.ok(evalStatsService.recentRuns(limit));
    }
}
