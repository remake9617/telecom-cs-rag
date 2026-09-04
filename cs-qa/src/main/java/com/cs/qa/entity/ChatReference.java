package com.cs.qa.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 回答引用来源（对应表 chat_reference）——记录某条 assistant 消息引用了哪些 chunk，
 * 支撑「回答溯源」（前端展示 docTitle + 原文预览）。
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

    /** RRF 融合分 */
    private Double score;

    /** Rerank 重排分 */
    private Double rerankScore;
}
