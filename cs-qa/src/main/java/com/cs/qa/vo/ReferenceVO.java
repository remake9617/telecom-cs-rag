package com.cs.qa.vo;

import com.cs.knowledge.dto.RetrievedChunk;
import com.cs.qa.entity.ChatReference;
import lombok.Data;

/**
 * 引用溯源视图对象（对齐 rest-api.md 第 78 行 SSE {@code reference} 事件元素
 * 与第 94 行 {@code MessageVO.references} 元素——二者结构完全一致）。
 *
 * <p><b>为什么流式路径与历史路径共用同一结构：</b>实时问答走 {@link RetrievedChunk}，
 * 历史回放走已落库的 {@link ChatReference}，若两条路径各自手拼 Map，字段名极易漂移
 * （如实时叫 {@code score}、历史误叫 {@code rerankScore}），前端渲染会静默错位。
 * 统一到一个 VO + 静态工厂，符合 CONVENTIONS §5「禁止手拼响应」的精神。</p>
 *
 * <p><b>三个字段一律非 null</b>：前端 {@code Reference} 类型三者全声明为必填，
 * {@code ReferenceList.tsx:15} 只对空数组做兜底，{@code score} 为 null 会渲染出「相关度 NaN%」。
 * 故 docTitle/chunkText 为 null 回填 {@code ""}、score 为 null 回填 {@code 0.0}。</p>
 */
@Data
public class ReferenceVO {

    /** 文档标题（溯源展示用） */
    private String docTitle;

    /** chunk 正文原文片段（前端展开面板预览） */
    private String chunkText;

    /** 相关度分数：取值口径见各 from 工厂，范围通常 0~1 */
    private Double score;

    /**
     * 实时流路径：{@link RetrievedChunk} → VO。
     *
     * <p><b>score 公式（与历史路径必须严格一致）：</b>{@code rerankScore != null ? rerankScore : score}。
     * 即优先用 Rerank 重排分（bge-reranker，量级 0~1），未启用重排时退回 RRF 融合分。
     * 前端 {@code ReferenceList.tsx:34} 渲染 {@code (score*100).toFixed(0)+'%'}。</p>
     *
     * @param chunk 检索命中的知识片段，可能为 null（防御性处理）
     * @return 非 null 的引用视图
     */
    public static ReferenceVO from(RetrievedChunk chunk) {
        ReferenceVO vo = new ReferenceVO();
        if (chunk == null) {
            vo.setDocTitle("");
            vo.setChunkText("");
            vo.setScore(0.0);
            return vo;
        }
        vo.setDocTitle(nullToEmpty(chunk.getDocTitle()));
        vo.setChunkText(nullToEmpty(chunk.getContent()));
        // RetrievedChunk.score 是原始 double（RRF 融合分，非 null），rerankScore 是包装 Double（可能 null）
        vo.setScore(chunk.getRerankScore() != null ? chunk.getRerankScore() : chunk.getScore());
        return vo;
    }

    /**
     * 历史回放路径：{@link ChatReference}（已落库快照）→ VO。
     *
     * <p><b>score 公式与实时路径严格一致：</b>{@code rerankScore != null ? rerankScore : score}，
     * 对应 {@code chat_reference.rerank_score} 优先、否则 {@code chat_reference.score}。
     * 这是本轮最隐蔽的一致性坑——若历史侧误用 RRF 原始分（量级约 {@code 1/(60+rank)≈0.016}），
     * 同一条答案会出现流式态显示 87%、刷新后显示 2% 的诡异现象。</p>
     *
     * @param ref              落库的引用快照行，可能为 null（防御性处理）
     * @param docTitleFallback 旧数据降级兜底标题：本轮之前的 {@code chat_reference} 行 {@code doc_title} 为 null，
     *                         由调用方按 {@code docId} 批量查 {@code kb_document.title} 得到；ref.docTitle 非 null 时忽略
     * @return 非 null 的引用视图
     */
    public static ReferenceVO from(ChatReference ref, String docTitleFallback) {
        ReferenceVO vo = new ReferenceVO();
        if (ref == null) {
            vo.setDocTitle("");
            vo.setChunkText("");
            vo.setScore(0.0);
            return vo;
        }
        // docTitle 优先用快照列；旧数据该列为 null 时用兜底标题（再 null 则空串）
        String title = ref.getDocTitle() != null ? ref.getDocTitle() : docTitleFallback;
        vo.setDocTitle(nullToEmpty(title));
        // chunkText 旧数据为 null 时回填空串（前端展开面板空白但不崩，避免 ES 回查）
        vo.setChunkText(nullToEmpty(ref.getChunkText()));
        // rerank_score 优先，否则 score；两者皆 null 时兜底 0.0
        Double score = ref.getRerankScore() != null ? ref.getRerankScore() : ref.getScore();
        vo.setScore(score != null ? score : 0.0);
        return vo;
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
