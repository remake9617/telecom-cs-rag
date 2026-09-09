package com.cs.stats.mapper;

import com.cs.stats.dto.EvalRunVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评测结果只读 Mapper（路9 评估体系的看板读侧）。
 *
 * <p>按 D24 约束，cs-stats 对 eval_result 同样只做只读直查（SELECT），不依赖 cs-knowledge
 * 的任何 Service 或 Mapper；写入完全由 cs-knowledge 的评估执行链路负责。此为既有
 * 「表级只读」边界在新表上的延续，D24 无损。</p>
 */
@Mapper
public interface EvalRunMapper {

    /** 最近 N 次评测运行（按 run 倒序），供管理后台看板展示历史评测结果 */
    @Select("SELECT id, kb_id, mode, case_count, status, recall_at_k, precision_at_k, "
            + "mrr, ndcg, hit_rate, ref_precision, top_k, error_msg, created_at "
            + "FROM eval_result ORDER BY id DESC LIMIT #{limit}")
    List<EvalRunVO> selectRecentRuns(@Param("limit") int limit);
}
