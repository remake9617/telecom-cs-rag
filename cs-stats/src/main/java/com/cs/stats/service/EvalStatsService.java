package com.cs.stats.service;

import com.cs.stats.dto.EvalRunVO;

import java.util.List;

/**
 * 评测结果看板读侧服务（路9）：只读直查 eval_result，不依赖 cs-knowledge 的 Service（D24）。
 */
public interface EvalStatsService {

    /** 最近 N 次评测运行（按 run 倒序） */
    List<EvalRunVO> recentRuns(int limit);
}
