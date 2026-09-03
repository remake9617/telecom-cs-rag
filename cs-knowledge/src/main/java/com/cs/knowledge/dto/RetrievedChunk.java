package com.cs.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 检索命中的知识片段 —— 路1(cs-knowledge) 返回给路2(cs-qa) 的出参契约（冻结）。
 *
 * <p>既用于喂给大模型做 grounding 生成，也用于「回答溯源」展示（docTitle + content）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    /** ES 文档 _id（关联 MySQL kb_chunk_meta.es_chunk_id） */
    private String chunkId;

    /** 所属文档 ID */
    private Long docId;

    /** 文档标题（溯源展示用） */
    private String docTitle;

    /** chunk 正文（喂给模型 + 溯源原文预览） */
    private String content;

    /** RRF 融合分（向量与 BM25 两路倒数排名融合后的分数） */
    private double score;

    /** Rerank 重排分（bge-reranker-v2-m3；未启用重排时为 null） */
    private Double rerankScore;

    /** chunk 在原文档中的序号 */
    private Integer seq;

    /** 其他元数据（来源 URL、章节等，溯源与过滤用） */
    private Map<String, Object> metadata;
}
