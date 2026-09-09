package com.cs.knowledge.eval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 评测用例（对应表 eval_case，路9 评估体系）。
 *
 * <p>三个来源：人工标注（MANUAL）、真实问答采样（SAMPLED）、DISLIKE 反馈回流 bad case 池
 * （DISLIKE，D15 预留的评估数据源）。JSON 字段以 JSON 数组字符串承载，序列化由服务层用
 * Jackson 完成（不引 typeHandler，零新增依赖）。</p>
 */
@Data
@TableName("eval_case")
public class EvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标知识库（null = 不限定库）；批次1 平行知识库对比实验按此字段分别评测 */
    private Long kbId;

    /** 评测问题 */
    private String question;

    /** 期望命中的 ES chunk id 列表，JSON 数组字符串（如 ["doc1-chunk0"]） */
    private String expectedChunkIds;

    /** 期望命中的文档 id 列表，JSON 数组字符串（粗粒度兜底标注） */
    private String expectedDocIds;

    /** 标注来源：MANUAL / SAMPLED / DISLIKE */
    private String source;

    /** 备注（DISLIKE 用例记录 message_id，做幂等去重键） */
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
