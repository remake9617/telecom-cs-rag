package com.cs.stats.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史评测结果 VO（管理后台看板展示用，GET /api/stats/eval-runs）。
 *
 * <p>对应表 eval_result（写入权在 cs-knowledge 的评估执行链路，本侧只读直查）。
 * 指标为 null 表示该次运行无可评测用例或尚未完成，前端按「-」展示。</p>
 */
@Data
public class EvalRunVO {

    /** run id（即 eval_result.id） */
    private Long id;

    private Long kbId;

    /** 检索通道：VECTOR/BM25/HYBRID/HYBRID_RERANK */
    private String mode;

    private Integer caseCount;

    /** RUNNING / DONE / FAILED */
    private String status;

    private Double recallAtK;

    private Double precisionAtK;

    private Double mrr;

    private Double ndcg;

    private Double hitRate;

    private Double refPrecision;

    private Integer topK;

    private String errorMsg;

    private LocalDateTime createdAt;
}
