package com.cs.knowledge.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评测运行结果（对应表 eval_result，路9 评估体系）。
 *
 * <p>一次评测运行一行：先以 {@code status=RUNNING} 同步落库拿到 run id（即主键 id），
 * 再由单线程 executor 异步执行，完成后回写指标或 FAILED 摘要。</p>
 *
 * <p><b>幻觉率/忠实度等生成侧指标为什么没有列</b>：判定需额外调 LLM 且判定本身不稳定、
 * 需设计评判 prompt，属批次 1 深化内容；本轮只做检索侧可确定性计算指标。刻意不建空列，
 * 避免「列存在但永远为 null」对后续使用者的误导（详见 M5 路9 回执）。</p>
 */
@Data
@TableName("eval_result")
public class EvalResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标知识库（null = 全库） */
    private Long kbId;

    /** 检索通道：VECTOR/BM25/HYBRID/HYBRID_RERANK */
    private String mode;

    /** 本次加载的用例总数 */
    private Integer caseCount;

    /** RUNNING / DONE / FAILED */
    private String status;

    /** 召回率（recall@k，宏平均） */
    private Double recallAtK;

    /** 准确率（precision@k，宏平均） */
    private Double precisionAtK;

    /** 平均倒数排名（MRR） */
    private Double mrr;

    /** 归一化折损累计增益（NDCG，二值相关） */
    private Double ndcg;

    /** 命中率（hit_rate：至少召回一条期望项的用例占比） */
    private Double hitRate;

    /** 引用准确率（ref_precision：引用落在期望文档集合内的比例，宏平均） */
    private Double refPrecision;

    /** 本次运行的 TopK */
    private Integer topK;

    /** FAILED 时的错误摘要 */
    private String errorMsg;

    private LocalDateTime createdAt;
}
