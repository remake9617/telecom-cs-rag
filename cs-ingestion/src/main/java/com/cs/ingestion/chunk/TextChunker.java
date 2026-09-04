package com.cs.ingestion.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块器 —— MVP 策略：固定长度(500字) + 重叠(50字) + 句末边界优先。
 *
 * <p>分块质量直接影响检索召回：块过大检索不精准、过小语义破碎；重叠避免关键信息被切断；
 * 在句末标点处切分保证 chunk 语义完整。阶段二可升级为语义分块（DESIGN 6.3）。</p>
 */
@Slf4j
@Component
public class TextChunker {

    /** 每块目标长度（字符） */
    private static final int CHUNK_SIZE = 500;
    /** 相邻块重叠长度（字符），保留上下文连续性 */
    private static final int OVERLAP = 50;
    /** 句末/边界标点，优先在此处切分 */
    private static final String BOUNDARY_CHARS = "。！？；\n.!?;";

    /**
     * 将长文本切分为带重叠的 chunk 列表。
     *
     * @param text 原始文本
     * @return chunk 列表（空文本返回空列表）
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String cleaned = normalize(text);
        int len = cleaned.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            // 非最后一块：在块尾部区域寻找句末边界，尽量完整切句而非硬切
            if (end < len) {
                int searchFrom = Math.max(start + CHUNK_SIZE / 2, end - OVERLAP * 2);
                int boundary = lastBoundary(cleaned, searchFrom, end);
                if (boundary > start) {
                    end = boundary + 1; // 含边界标点
                }
            }
            String piece = cleaned.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= len) {
                break;
            }
            start = Math.max(end - OVERLAP, start + 1); // 重叠，且保证前进避免死循环
        }
        log.debug("分块完成：{} 字符 → {} 块", len, chunks.size());
        return chunks;
    }

    /** 清洗：统一换行符、压缩连续空白、去除首尾空白 */
    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /** 在 [from, to) 内从后往前找最后一个边界标点，找不到返回 -1 */
    private int lastBoundary(String s, int from, int to) {
        int upper = Math.min(to, s.length()) - 1;
        for (int i = upper; i >= from; i--) {
            if (BOUNDARY_CHARS.indexOf(s.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }
}
