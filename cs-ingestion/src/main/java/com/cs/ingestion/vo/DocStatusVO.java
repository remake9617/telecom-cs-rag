package com.cs.ingestion.vo;

import com.cs.knowledge.entity.KbDocument;
import lombok.Data;

/**
 * 文档入库进度视图对象（契约 GET /api/kb/documents/{id}/status 的出参 {status, chunkCount}）。
 *
 * <p>契约第 42 行只有匿名结构、VO 速查无命名条目；前端已自行命名为 DocStatusVO
 * （types/index.ts），本类名与之对齐，作为入库进度轮询端点的返回体。</p>
 */
@Data
public class DocStatusVO {

    /**
     * 入库状态：<b>大写</b> PENDING / PROCESSING / DONE / FAILED。
     * 大小写敏感——前端轮询终止判定依赖大写终态，详见 {@link DocumentVO} 同名说明。
     */
    private String status;

    /** chunk 数量，直读 MySQL kb_document.chunk_count，不实时查 ES */
    private Integer chunkCount;

    /**
     * 实体 → VO（唯一映射入口，遵循项目既有 UserVO.from 实践）。
     *
     * @param doc 文档实体，非 null
     * @return 仅含进度字段的视图对象
     */
    public static DocStatusVO from(KbDocument doc) {
        DocStatusVO vo = new DocStatusVO();
        // 防御性大写：与 DocumentVO 保持一致，杜绝大小写漂移破坏前端轮询终止判定
        vo.setStatus(doc.getStatus() != null ? doc.getStatus().toUpperCase() : null);
        vo.setChunkCount(doc.getChunkCount());
        return vo;
    }
}
