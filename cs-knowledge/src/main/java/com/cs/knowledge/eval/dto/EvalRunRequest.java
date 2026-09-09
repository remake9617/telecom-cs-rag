package com.cs.knowledge.eval.dto;

import lombok.Data;

import java.util.List;

/**
 * 触发一次评测运行入参（POST /api/eval/runs，契约提案待统筹落盘）。
 *
 * <p>评测是离线批处理，所有参数经入参传入、常量放代码里，<b>不新增任何 application.yml
 * 配置键</b>（与路10 在配置文件上零冲突）。</p>
 */
@Data
public class EvalRunRequest {

    /** 目标知识库（null = 全库） */
    private Long kbId;

    /** 检索通道：VECTOR/BM25/HYBRID/HYBRID_RERANK，null 视为 HYBRID_RERANK（生产默认） */
    private String mode;

    /** 最终返回条数，缺省 5 */
    private Integer topK;

    /** 向量通道召回预算，缺省 20 */
    private Integer vectorTopN;

    /** 关键词通道召回预算，缺省 20 */
    private Integer keywordTopN;

    /**
     * 最终排序分阈值（低于丢弃，见 {@link com.cs.knowledge.dto.RetrievalRequest#minScore}）。
     * 评测驱动检索实验的可执行入口之一；null 不过滤。
     */
    private Double minScore;

    /** 只跑指定用例 id（null = 全部可评测用例，即排除 DISLIKE bad case 池） */
    private List<Long> caseIds;
}
