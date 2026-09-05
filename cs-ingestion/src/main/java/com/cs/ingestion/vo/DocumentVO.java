package com.cs.ingestion.vo;

import com.cs.knowledge.entity.KbDocument;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档视图对象（契约 DocumentVO：{id, kbId, title, sourceType, fileType, chunkCount, status, createdAt}）。
 *
 * <p>对齐 contract/rest-api.md 第 95 行 VO 速查。刻意不含 sourceUrl、updatedAt——
 * 契约与前端类型均未声明；sourceUrl 对管理端列表无展示价值，下发只增加载荷。</p>
 *
 * <p><b>时间字段</b>：createdAt 直接用 {@link LocalDateTime}，由全局 JacksonConfig
 * 统一序列化为 {@code yyyy-MM-dd HH:mm:ss}，故此处不加 @JsonFormat、不手动转 String。</p>
 */
@Data
public class DocumentVO {

    /** 文档 ID，<b>必须非 null</b>：前端 Documents.tsx 用它做 Table rowKey */
    private Long id;

    private Long kbId;

    private String title;

    /** 来源类型：UPLOAD / URL */
    private String sourceType;

    /** 文件类型：MD/TXT/PDF/WORD/EXCEL/HTML */
    private String fileType;

    /**
     * chunk 数量，<b>直读 MySQL kb_document.chunk_count</b>。
     *
     * <p>严禁为此字段实时查 ES 聚合：前端 useKb.ts 对文档列表接口有 3 秒轮询，
     * 实时 ES count 会被放大成持续的 ES 压力。入库/重建完成时已把终值写入 MySQL。</p>
     */
    private Integer chunkCount;

    /**
     * 入库状态：<b>大写</b> PENDING / PROCESSING / DONE / FAILED。
     *
     * <p>前端 useKb.ts 的轮询终止判定是 {@code ['DONE','FAILED'].includes(d.status)}，
     * <b>大小写敏感</b>——若下发小写会导致每 3 秒无限轮询列表接口直到用户离开页面。
     * DB 存的即是大写，此处再做一次防御性 toUpperCase 以杜绝任何写入路径的大小写漂移。</p>
     */
    private String status;

    private LocalDateTime createdAt;

    /**
     * 实体 → VO（唯一映射入口，遵循项目既有 UserVO.from 实践）。
     *
     * @param doc 文档实体，非 null
     * @return 契约形态的文档视图对象
     */
    public static DocumentVO from(KbDocument doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setKbId(doc.getKbId());
        vo.setTitle(doc.getTitle());
        vo.setSourceType(doc.getSourceType());
        vo.setFileType(doc.getFileType());
        vo.setChunkCount(doc.getChunkCount());
        // 防御性大写：保证前端大小写敏感的轮询终止判定成立（见字段 Javadoc）
        vo.setStatus(doc.getStatus() != null ? doc.getStatus().toUpperCase() : null);
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }
}
