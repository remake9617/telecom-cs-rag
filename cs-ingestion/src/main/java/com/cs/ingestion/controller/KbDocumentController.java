package com.cs.ingestion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cs.framework.common.R;
import com.cs.ingestion.service.IngestionService;
import com.cs.knowledge.dto.RetrievalRequest;
import com.cs.knowledge.dto.RetrievedChunk;
import com.cs.knowledge.entity.KbDocument;
import com.cs.knowledge.mapper.KbDocumentMapper;
import com.cs.knowledge.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档与检索接口（对应 contract/rest-api.md 第 3 节 /api/kb）。
 *
 * <p>置于 cs-ingestion：该模块依赖 cs-knowledge，可同时调用 IngestionService(入库)
 * 与 RetrievalService(检索)，聚合成一组知识库入口 API。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbDocumentController {

    private final IngestionService ingestionService;
    private final RetrievalService retrievalService;
    private final KbDocumentMapper kbDocumentMapper;

    /** 上传文档入库（multipart）：解析 → 分块 → 向量化 → ES 双写 */
    @PostMapping("/documents/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "1") Long kbId) throws IOException {
        String filename = file.getOriginalFilename();
        Long docId = ingestionService.ingest(kbId, filename, "UPLOAD", detectType(filename), null, file.getInputStream());
        return R.ok(Map.of("docId", docId, "title", filename == null ? "unknown" : filename));
    }

    /** URL 抓取入库 */
    @PostMapping("/documents/url")
    public R<Map<String, Object>> ingestUrl(@RequestBody Map<String, Object> body) throws IOException {
        String url = String.valueOf(body.get("url"));
        Long kbId = body.get("kbId") != null ? Long.valueOf(String.valueOf(body.get("kbId"))) : 1L;
        Long docId = ingestionService.ingest(kbId, url, "URL", "HTML", url, null);
        return R.ok(Map.of("docId", docId, "url", url));
    }

    /** 混合检索测试（M1 验收核心）：query → kNN + BM25 + RRF + Rerank → TopK */
    @PostMapping("/search")
    public R<List<RetrievedChunk>> search(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.get("query"));
        int topK = body.get("topK") != null ? Integer.parseInt(String.valueOf(body.get("topK"))) : 5;
        RetrievalRequest req = RetrievalRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<RetrievedChunk> hits = retrievalService.hybridRetrieve(req);
        log.info("检索[{}] 命中 {} 条", query, hits.size());
        return R.ok(hits);
    }

    /** 文档列表 */
    @GetMapping("/documents")
    public R<List<KbDocument>> list(@RequestParam(defaultValue = "1") Long kbId) {
        List<KbDocument> docs = kbDocumentMapper.selectList(
                new QueryWrapper<KbDocument>().eq("kb_id", kbId).orderByDesc("id"));
        return R.ok(docs);
    }

    /** 由文件名后缀推断类型 */
    private String detectType(String filename) {
        if (filename == null) {
            return "TXT";
        }
        String f = filename.toLowerCase();
        if (f.endsWith(".pdf")) return "PDF";
        if (f.endsWith(".doc") || f.endsWith(".docx")) return "WORD";
        if (f.endsWith(".xls") || f.endsWith(".xlsx")) return "EXCEL";
        if (f.endsWith(".md")) return "MD";
        if (f.endsWith(".html") || f.endsWith(".htm")) return "HTML";
        return "TXT";
    }
}
