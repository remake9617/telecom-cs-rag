package com.cs.ingestion.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.common.PageVO;
import com.cs.framework.common.R;
import com.cs.framework.exception.BizException;
import com.cs.framework.security.SecurityUtils;
import com.cs.ingestion.service.IngestionService;
import com.cs.ingestion.vo.DocStatusVO;
import com.cs.ingestion.vo.DocumentVO;
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
 *
 * <p><b>权限收口</b>：本 Controller 全部端点均属契约标注的「管理员」范畴，每个方法首行调用
 * {@link SecurityUtils#requireAdmin()}。此前 cs-ingestion 的 requireAdmin 命中数为 0，
 * 任何持有效 JWT 的 VISITOR 都能上传文档、触发 Tika 解析 + bge-m3 向量化 + ES 写入，
 * 消耗付费额度并污染知识库——本轮修复该安全隐患（DEF-002）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbDocumentController {

    private final IngestionService ingestionService;
    private final RetrievalService retrievalService;
    private final KbDocumentMapper kbDocumentMapper;

    /**
     * 上传文档入库（multipart）：解析 → 分块 → 向量化 → ES 双写。仅 ADMIN。
     *
     * @return 入库后的文档视图（回读实体转 {@link DocumentVO}，替代旧的 Map 返回，对齐契约）
     */
    @PostMapping("/documents/upload")
    public R<DocumentVO> upload(@RequestParam("file") MultipartFile file,
                                @RequestParam(defaultValue = "1") Long kbId) throws IOException {
        SecurityUtils.requireAdmin();
        String filename = file.getOriginalFilename();
        Long docId = ingestionService.ingest(kbId, filename, "UPLOAD", detectType(filename), null, file.getInputStream());
        return R.ok(toDocumentVO(docId));
    }

    /**
     * URL 抓取入库。仅 ADMIN。
     *
     * @return 入库后的文档视图（回读实体转 {@link DocumentVO}）
     */
    @PostMapping("/documents/url")
    public R<DocumentVO> ingestUrl(@RequestBody Map<String, Object> body) throws IOException {
        SecurityUtils.requireAdmin();
        String url = String.valueOf(body.get("url"));
        Long kbId = body.get("kbId") != null ? Long.valueOf(String.valueOf(body.get("kbId"))) : 1L;
        Long docId = ingestionService.ingest(kbId, url, "URL", "HTML", url, null);
        return R.ok(toDocumentVO(docId));
    }

    /**
     * 混合检索<b>调试/验收端点</b>（M1 验收核心）：query → kNN + BM25 + RRF + Rerank → TopK。仅 ADMIN。
     *
     * <p>刻意保留返回 {@code List<RetrievedChunk>} 而不新建 VO：这是仅供管理员使用的检索调试入口，
     * 会返回内部检索明细（含各通道分数、metadata 等 RAG 链路调试数据），不下发给普通用户，
     * 故不纳入面向前端的 VO 体系，避免 VO 膨胀。</p>
     */
    @PostMapping("/search")
    public R<List<RetrievedChunk>> search(@RequestBody Map<String, Object> body) {
        SecurityUtils.requireAdmin();
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

    /**
     * 文档分页列表。仅 ADMIN。
     *
     * <p>返回 {@code R<PageVO<DocumentVO>>}（对齐契约与 CONVENTIONS §7）。kbId 为 null 时查全部库，
     * 非 null 时按 kb_id 过滤，一律按 id 倒序。<b>chunkCount 直读 MySQL，不实时查 ES</b>——
     * 前端对该列表接口有 3 秒轮询，实时 ES 聚合会被放大成持续压力。</p>
     *
     * @param kbId    知识库过滤（可空，空则查全部）
     * @param current 页码，默认 1
     * @param size    每页大小，默认 10
     */
    @GetMapping("/documents")
    public R<PageVO<DocumentVO>> list(@RequestParam(required = false) Long kbId,
                                      @RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size) {
        SecurityUtils.requireAdmin();
        QueryWrapper<KbDocument> wrapper = new QueryWrapper<>();
        if (kbId != null) {
            wrapper.eq("kb_id", kbId);
        }
        wrapper.orderByDesc("id");
        Page<KbDocument> page = kbDocumentMapper.selectPage(Page.of(current, size), wrapper);
        // 转换范式对齐 SysUserServiceImpl / TicketServiceImpl：records/total/current/size
        List<DocumentVO> records = page.getRecords().stream().map(DocumentVO::from).toList();
        return R.ok(PageVO.of(records, page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /**
     * 删除文档（含 ES chunk 清理）。仅 ADMIN。
     *
     * @param id 文档 ID
     */
    @DeleteMapping("/documents/{id}")
    public R<Void> delete(@PathVariable Long id) {
        SecurityUtils.requireAdmin();
        ingestionService.deleteDocument(id);
        return R.ok();
    }

    /**
     * 重建文档索引（同步：ES 回读正文 → 重新向量化 → 相同 _id 覆盖写回）。仅 ADMIN。
     *
     * <p>声明 {@code throws IOException} 与 {@link #upload}/{@link #ingestUrl} 一致：
     * IngestionService.reindex 的 ES 回读/写回属未预期 I/O 异常，原样冒泡后由
     * GlobalExceptionHandler 兜底为 1999，无需在 Controller 手动处理。</p>
     *
     * @param id 文档 ID
     * @throws IOException ES 回读/写回的 I/O 意外，冒泡交全局兜底
     */
    @PostMapping("/documents/{id}/reindex")
    public R<Void> reindex(@PathVariable Long id) throws IOException {
        SecurityUtils.requireAdmin();
        ingestionService.reindex(id);
        return R.ok();
    }

    /**
     * 入库进度轮询：返回文档当前 status 与 chunkCount。仅 ADMIN。
     *
     * @param id 文档 ID
     * @throws BizException 2002 文档不存在
     */
    @GetMapping("/documents/{id}/status")
    public R<DocStatusVO> status(@PathVariable Long id) {
        SecurityUtils.requireAdmin();
        KbDocument doc = kbDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.DOC_NOT_FOUND);
        }
        return R.ok(DocStatusVO.from(doc));
    }

    /**
     * 按 docId 回读实体并转 {@link DocumentVO}。
     *
     * <p>入库方法返回的是 docId；ingest 的 {@code @Transactional} 在其返回时已提交，
     * 此处 selectById 读到的是已落库的 DONE 行，可安全映射为契约 VO。</p>
     */
    private DocumentVO toDocumentVO(Long docId) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            // 理论上不会发生（ingest 刚提交）；防御性抛出，避免向前端返回 null 造成静默空渲染
            throw new BizException(ErrorCode.DOC_NOT_FOUND);
        }
        return DocumentVO.from(doc);
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
