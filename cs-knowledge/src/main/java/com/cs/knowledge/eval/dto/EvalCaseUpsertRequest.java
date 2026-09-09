package com.cs.knowledge.eval.dto;

import lombok.Data;

import java.util.List;

/**
 * 新增评测用例入参（POST /api/eval/cases，契约提案待统筹落盘）。
 *
 * <p>校验在服务层手动做（与 StatsServiceImpl 风格一致），不用 @Valid：
 * 见 DEF-088 关于 @Valid 失败响应形状的待拍板问题，本轮不添新变量。</p>
 */
@Data
public class EvalCaseUpsertRequest {

    /** 目标知识库（null = 不限定库，跨库用例） */
    private Long kbId;

    /** 评测问题（必填） */
    private String question;

    /** 期望命中的 ES chunk id 列表（细粒度标注，可空则用 docIds 兜底） */
    private List<String> expectedChunkIds;

    /** 期望命中的文档 id 列表（粗粒度兜底标注） */
    private List<Long> expectedDocIds;

    /** 标注来源：MANUAL/SAMPLED/DISLIKE，缺省 MANUAL */
    private String source;

    /** 备注 */
    private String note;
}
