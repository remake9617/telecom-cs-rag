package com.cs.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档元数据（对应表 kb_document）。
 *
 * <p>正文与向量存于 ES（见 kb_chunk_meta.esChunkId），此处仅存管理信息，
 * 支撑文档列表/状态/删除等（DESIGN 5.1）。</p>
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String title;

    /** 来源类型：UPLOAD / URL */
    private String sourceType;

    /** 文件类型：MD/TXT/PDF/WORD/EXCEL/HTML */
    private String fileType;

    private String sourceUrl;

    private Integer chunkCount;

    /** 入库状态：PENDING/PROCESSING/DONE/FAILED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
