package com.cs.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 回答引用来源（对应表 chat_reference）——记录某条 assistant 消息引用了哪些 chunk，
 * 支撑「回答溯源」（前端展示 docTitle + 原文预览）。
 *
 * <p><b>为什么在此冗余 {@code docTitle} 与 {@code chunkText}（引用快照语义）：</b>
 * chat_reference 记录的是「当时向用户展示了什么」，属<b>审计快照</b>，与 kb_chunk_meta 的
 * 「当前索引状态」是两种不同职责。因此这里冗余存储标题与正文<b>不违反</b>
 * 「chunk 正文只存 ES、MySQL 仅存元数据」的原则——那条原则约束的是<b>知识库当前态</b>，
 * 而非问答历史留痕。</p>
 *
 * <p><b>成本与收益：</b>冗余约 2.5KB/次问答（5 引用 × 约 500 字符），在 D5 的语料规模
 * （数百文档、数千 chunk）下完全可接受；换来的是<b>历史回放零 ES 往返</b>，且 ES 不可用时
 * 历史接口不会 500（否则刷新会话就得实时回查 ES）。</p>
 *
 * <p>两列靠 application.yml 已开的 {@code map-underscore-to-camel-case} 自动映射
 * （doc_title→docTitle、chunk_text→chunkText），无需 {@code @TableField} 注解。</p>
 */
@Data
@TableName("chat_reference")
public class ChatReference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private Long docId;

    /** ES chunk _id */
    private String esChunkId;

    /** 引用快照：文档标题（冗余，历史回放时免 join kb_document） */
    private String docTitle;

    /** 引用快照：chunk 正文（冗余，历史回放零查询还原原文预览） */
    private String chunkText;

    /** RRF 融合分 */
    private Double score;

    /** Rerank 重排分 */
    private Double rerankScore;
}
