package com.cs.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * chunk 元数据（对应表 kb_chunk_meta）。
 *
 * <p>chunk 的正文与向量存在 ES（{@code esChunkId} = ES 文档 _id），
 * MySQL 仅存元数据用于关联文档与溯源。</p>
 */
@Data
@TableName("kb_chunk_meta")
public class KbChunkMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;

    /** ES 文档 _id（关联 ES 中的 chunk） */
    private String esChunkId;

    /** chunk 在原文档中的序号 */
    private Integer seq;

    private Integer tokenCount;

    private LocalDateTime createdAt;
}
