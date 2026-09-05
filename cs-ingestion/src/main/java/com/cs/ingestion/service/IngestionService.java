package com.cs.ingestion.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.ingestion.chunk.TextChunker;
import com.cs.ingestion.parser.TikaDocumentParser;
import com.cs.ingestion.parser.UrlContentFetcher;
import com.cs.knowledge.entity.KbChunkMeta;
import com.cs.knowledge.entity.KbDocument;
import com.cs.knowledge.mapper.KbChunkMetaMapper;
import com.cs.knowledge.mapper.KbDocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 删除一篇文档：清理 ES 中的全部 chunk + MySQL 元数据（kb_chunk_meta、kb_document）。
     *
     * <p><b>为什么顺序必须是「先 ES 后 MySQL」</b>：MySQL 侧包在 {@link Transactional} 中，
     * 若先删 MySQL 再删 ES 且 ES 失败，事务回滚后 MySQL 元数据仍在，但一旦 ES 已部分删除
     * 就会失配；更危险的反例是「先提交 MySQL 删除、ES 删除随后失败」——ES 里的 chunk 会因
     * 失去 doc_id/chunk_meta 元数据而永久无法定位清理，成为检索会命中的幽灵数据。反之，
     * 先删 ES：ES 删除失败时抛异常触发 MySQL 回滚，两边都保持原状；ES 删成功而 MySQL 失败时，
     * 最多残留几行可再次删除的孤立元数据（无害、可重试），不会产生幽灵 chunk。</p>
     *
     * <p><b>term 值必须是字符串</b>：metadata.doc_id 在 ES mapping 中是 keyword 类型，
     * 入库时写入的是 {@code String.valueOf(docId)}（见 {@link #indexChunkToEs}），
     * 若传数字则 term 匹配不到、chunk 删不掉。</p>
     *
     * <p><b>错误码分派（对齐 CONVENTIONS §6：区分「可预期业务失败」与「未预期系统错误」）</b>：
     * 文档不存在 → 2002；ES {@code deleteByQuery} 失败 → 2007 DOC_DELETE_FAILED（删除链路里明确可识别、
     * 且需触发 MySQL 回滚的可预期失败）；而 MySQL 删除阶段（删 kb_chunk_meta / kb_document）的意外异常
     * <b>不包装成 2007</b>——它属未预期系统错误，原样冒泡，既触发 {@link Transactional} 回滚，
     * 又由 GlobalExceptionHandler 兜底为 1999，保留原始堆栈。</p>
     *
     * @param docId 文档 ID
     * @throws BizException 2002 文档不存在；2007 ES 删除失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long docId) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND);
        }

        // 1) 先删 ES：按 metadata.doc_id（keyword，传字符串）批量删除该文档全部 chunk
        try {
            esClient.deleteByQuery(d -> d
                    .index(INDEX)
                    .query(q -> q.term(t -> t.field("metadata.doc_id").value(String.valueOf(docId)))));
        } catch (IOException e) {
            log.error("删除文档 ES chunk 失败: docId={}, err={}", docId, e.getMessage(), e);
            // 抛异常触发 MySQL 回滚，保证「ES 未删成则 MySQL 不动」
            throw new BizException(ErrorCode.DOC_DELETE_FAILED, "删除 ES 索引失败: " + e.getMessage());
        }

        // 2) 再删 MySQL：先子表 kb_chunk_meta 再主表 kb_document
        kbChunkMetaMapper.delete(new QueryWrapper<KbChunkMeta>().eq("doc_id", docId));
        kbDocumentMapper.deleteById(docId);
        log.info("文档删除完成: id={}, title={}", docId, doc.getTitle());
    }

    /**
     * 重建一篇文档的索引（同步执行）：从 ES 回读该文档的全部 chunk 正文 → 重新向量化 →
     * 用相同 _id 覆盖写回 ES。
     *
     * <p><b>为什么用「ES 回读 + 重新向量化」而非「重抓原文」</b>：UPLOAD 类文档的原始
     * InputStream 来自 multipart 请求，项目从未落盘持久化，物理上无法重读；URL 类重抓会因
     * 网页内容变动产生不可复现的结果。而 chunk 正文本就完整存在 ES 里，回读后重新向量化对
     * 两种 sourceType 都有效，且这才是「重建索引」的真实语义——更换 embedding 模型或调整 IK
     * 词典后，用同一批正文重算向量/重分词即可，无需触碰原始来源。</p>
     *
     * <p><b>为什么同步而非异步</b>：D5 的语料规模是数百文档/数千 chunk，单文档重建是秒级操作；
     * 异步需引入状态机与额外轮询复杂度，MVP 不划算。前端 useReindexDocument 的 onSuccess 只提示
     * 并 invalidate 列表，同步返回完全兼容。</p>
     *
     * <p><b>为什么用相同 _id 覆盖写回而非先删后写</b>：ES 的 index 操作天然 upsert，相同 _id
     * 直接覆盖旧文档；先删后写会在删除成功、写入失败时留下空洞（该 chunk 永久丢失）。</p>
     *
     * <p><b>为什么不加 {@link Transactional}</b>：本方法需要让 PROCESSING/FAILED/DONE 等中间与终态
     * 状态各自独立提交并持久化——若包在事务里，抛出异常时 FAILED 状态会被一并回滚，前端将永远
     * 看不到失败态、陷入无限轮询。故每次 updateById 走各自自动提交。</p>
     *
     * <p><b>错误码分派（对齐 CONVENTIONS §6，务必区分「可预期业务失败」与「未预期系统错误」）</b>：</p>
     * <ul>
     *   <li>文档不存在 → 2002 DOC_NOT_FOUND；</li>
     *   <li>ES 回读命中 0 条（该文档确实缺少可重建的索引数据）→ 2006 DOC_REINDEX_UNSUPPORTED，语义精确；</li>
     *   <li>向量化失败（{@code embeddingModel.embed} 抛错，明确可识别的一类）→ 2005 EMBEDDING_FAIL；</li>
     *   <li>其它一切意外异常（ES 回读/写回的 IOException、NPE 等未预期错误）→ <b>不包装成任何业务码</b>，
     *       先落 FAILED 再把原始异常原样冒泡，由 GlobalExceptionHandler 兜底为 1999。把 embedding 超时、
     *       ES 写回失败一律标成 2006「缺少可重建数据」会误导排障方向、并绕过全局兜底，故严禁。</li>
     * </ul>
     *
     * @param docId 文档 ID
     * @throws BizException 2002 文档不存在；2006 无可重建的 chunk；2005 向量化失败
     * @throws IOException  ES 回读/写回的受检 I/O 意外——先落 FAILED 再原样冒泡，由 GlobalExceptionHandler 兜底 1999
     */
    public void reindex(Long docId) throws IOException {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND);
        }

        // 置 PROCESSING（独立提交，前端可观测中间态）
        doc.setStatus("PROCESSING");
        kbDocumentMapper.updateById(doc);

        try {
            // 1) 从 ES 按 metadata.doc_id（keyword，传字符串）回读该文档全部 chunk；size 设足够大
            SearchResponse<JsonNode> resp = esClient.search(s -> s
                            .index(INDEX)
                            .size(10000)
                            .query(q -> q.term(t -> t.field("metadata.doc_id").value(String.valueOf(docId)))),
                    JsonNode.class);

            // 2) 抽取 _id / content / seq（保持三者同序，供覆盖写回复用原 _id）
            List<String> chunkIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<Integer> seqs = new ArrayList<>();
            for (Hit<JsonNode> hit : resp.hits().hits()) {
                JsonNode src = hit.source();
                if (src == null) {
                    continue;
                }
                chunkIds.add(hit.id());
                contents.add(src.path("content").asText());
                seqs.add(src.path("metadata").path("seq").asInt());
            }

            // 3) 命中 0 条：ES 里没有可重建的 chunk —— 语义精确对应 2006 DOC_REINDEX_UNSUPPORTED。
            //    此处只负责抛出精确业务码，FAILED 落库统一交给下方 catch (BizException)，避免双写。
            if (contents.isEmpty()) {
                throw new BizException(ErrorCode.DOC_REINDEX_UNSUPPORTED);
            }

            // 4) 重新批量向量化（沿用 ingest 的整批 embed 方式与批次）。
            //    向量化失败是「明确可识别」的一类失败，单独 try 精确映射为 2005 EMBEDDING_FAIL，
            //    绝不与 ES 回读/写回等未预期意外异常混为一谈（后者应冒泡到 1999）。
            List<float[]> vectors;
            try {
                vectors = embeddingModel.embed(contents);
            } catch (Exception embedEx) {
                throw new BizException(ErrorCode.EMBEDDING_FAIL,
                        "重建索引向量化失败: docId=" + docId + ", 原因=" + embedEx.getMessage());
            }

            // 5) 用相同 _id 覆盖写回 ES（index 天然 upsert，不先删）
            for (int i = 0; i < contents.size(); i++) {
                indexChunkToEs(chunkIds.get(i), doc.getKbId(), docId, doc.getTitle(),
                        doc.getSourceType(), seqs.get(i), contents.get(i), vectors.get(i));
            }

            // 6) 成功：置 DONE + 刷新 chunk_count / updated_at
            doc.setStatus("DONE");
            doc.setChunkCount(contents.size());
            doc.setUpdatedAt(LocalDateTime.now());
            kbDocumentMapper.updateById(doc);
            log.info("文档重建索引完成: id={}, chunks={}", docId, contents.size());
        } catch (BizException e) {
            // 可预期业务失败（2006 无可重建数据 / 2005 向量化失败）：先落 FAILED 保证状态不卡在
            // PROCESSING，再「原样透传」保留其精确错误码，绝不再包装。
            // 必须排在最前：BizException 是 RuntimeException 子类，否则会被下方 RuntimeException 分支拦截。
            markFailed(doc);
            throw e;
        } catch (IOException e) {
            // ES 回读/写回的受检 I/O 意外：先落 FAILED，再原样冒泡（方法已声明 throws IOException），
            // 交由 GlobalExceptionHandler 兜底为 1999 —— 不包装成任何业务错误码，保留原始堆栈可诊断性。
            markFailed(doc);
            log.error("文档重建索引 ES I/O 失败，状态已置 FAILED，原样冒泡交全局兜底(1999): docId={}", docId, e);
            throw e;
        } catch (RuntimeException e) {
            // 其它未预期运行时异常（NPE 等）：同样先落 FAILED 再原样冒泡到 1999，不贴任何业务码。
            markFailed(doc);
            log.error("文档重建索引意外失败，状态已置 FAILED，原样冒泡交全局兜底(1999): docId={}", docId, e);
            throw e;
        }
    }

    /**
     * 置文档为 FAILED 终态并刷新 updated_at（独立提交）。
     *
     * <p>抽出复用：reindex 的各失败路径（2006 无可重建数据、2005 向量化失败、意外异常）都需落 FAILED，
     * 保证前端大小写敏感的轮询终止判定成立、不会无限轮询。</p>
     *
     * <p><b>为什么内部吞掉自身异常只记日志、绝不外抛</b>：本方法是在失败处理路径上被调用的，若它自己
     * 再抛异常（如置 FAILED 时 DB 连接抖动），会掩盖真正触发本次失败的原始异常，使排障丢失第一现场。
     * 故此处 catch 后仅 log.error——「落 FAILED 失败」本身不该改变原始异常的传播路径。</p>
     */
    private void markFailed(KbDocument doc) {
        try {
            doc.setStatus("FAILED");
            doc.setUpdatedAt(LocalDateTime.now());
            kbDocumentMapper.updateById(doc);
        } catch (Exception ex) {
            log.error("置文档 FAILED 状态失败(不掩盖原始异常，仅记录): docId={}, err={}",
                    doc.getId(), ex.getMessage(), ex);
        }
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
