package com.cs.ingestion.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.ingestion.chunk.TextChunker;
import com.cs.ingestion.parser.TikaDocumentParser;
import com.cs.ingestion.parser.UrlContentFetcher;
import com.cs.knowledge.entity.KbChunkMeta;
import com.cs.knowledge.entity.KbDocument;
import com.cs.knowledge.mapper.KbChunkMetaMapper;
import com.cs.knowledge.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库流水线（DESIGN 4.2 / 6.1）——编排「解析 → 分块 → 向量化 → ES 双写 + MySQL 元数据」。
 *
 * <p>一致性策略（MVP）：MySQL 写入包 {@link Transactional}，任一步失败回滚 MySQL；
 * ES 非事务，失败时可能残留已写 chunk，阶段二用 RocketMQ 事务消息补偿（DESIGN 4.4）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final String INDEX = "cs_knowledge_chunk";

    private final TikaDocumentParser tikaDocumentParser;
    private final UrlContentFetcher urlContentFetcher;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient esClient;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbChunkMetaMapper kbChunkMetaMapper;

    /**
     * 入库一篇文档。
     *
     * @param kbId       目标知识库
     * @param title      文档标题
     * @param sourceType UPLOAD / URL
     * @param fileType   MD/TXT/PDF/WORD/EXCEL/HTML
     * @param sourceUrl  URL 来源时的地址（UPLOAD 时为 null）
     * @param input      文件流（UPLOAD 时用；URL 时为 null）
     * @return 文档 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long ingest(Long kbId, String title, String sourceType, String fileType,
                       String sourceUrl, InputStream input) throws IOException {
        // 1) 解析为纯文本
        String text = "URL".equalsIgnoreCase(sourceType)
                ? urlContentFetcher.fetch(sourceUrl)
                : tikaDocumentParser.parse(input, title);

        // 2) 分块
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new BizException(ErrorCode.DOC_PARSE_FAIL, "文档解析后无有效内容: " + title);
        }

        // 3) 批量向量化（bge-m3 → 1024 维）
        List<float[]> vectors = embeddingModel.embed(chunks);

        // 4) 写 MySQL 文档元数据（先置 PROCESSING）
        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setTitle(title);
        doc.setSourceType(sourceType);
        doc.setFileType(fileType);
        doc.setSourceUrl(sourceUrl);
        doc.setChunkCount(0);
        doc.setStatus("PROCESSING");
        kbDocumentMapper.insert(doc);
        Long docId = doc.getId();

        // 5) 逐 chunk 写 ES（正文+向量+元数据）+ MySQL chunk 元数据
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = docId + "_" + i;
            String chunkText = chunks.get(i);
            indexChunkToEs(chunkId, kbId, docId, title, sourceType, i, chunkText, vectors.get(i));

            KbChunkMeta meta = new KbChunkMeta();
            meta.setDocId(docId);
            meta.setEsChunkId(chunkId);
            meta.setSeq(i);
            meta.setTokenCount(chunkText.length());
            kbChunkMetaMapper.insert(meta);
        }

        // 6) 更新文档状态为 DONE + chunk 数
        doc.setStatus("DONE");
        doc.setChunkCount(chunks.size());
        kbDocumentMapper.updateById(doc);

        log.info("文档入库完成: id={}, title={}, chunks={}", docId, title, chunks.size());
        return docId;
    }

    /**
     * 写单个 chunk 到 ES：content 供 BM25（IK 分词），embedding 供 kNN，metadata 供溯源与过滤。
     */
    private void indexChunkToEs(String chunkId, Long kbId, Long docId, String title,
                                String source, int seq, String content, float[] vector) throws IOException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunk_id", chunkId);
        metadata.put("doc_id", String.valueOf(docId));
        metadata.put("kb_id", String.valueOf(kbId));
        metadata.put("doc_title", title);
        metadata.put("seq", seq);
        metadata.put("source", source);

        Map<String, Object> esDoc = new HashMap<>();
        esDoc.put("content", content);
        esDoc.put("embedding", vector);
        esDoc.put("metadata", metadata);

        esClient.index(ix -> ix.index(INDEX).id(chunkId).document(esDoc));
    }
}
