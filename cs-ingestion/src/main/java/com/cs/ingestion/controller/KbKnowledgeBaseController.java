package com.cs.ingestion.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.ingestion.dto.KbCreateRequest;
import com.cs.ingestion.vo.KnowledgeBaseVO;
import com.cs.knowledge.entity.KbKnowledgeBase;
import com.cs.knowledge.mapper.KbKnowledgeBaseMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库管理接口（对应 contract/rest-api.md 第 3 节 /api/kb/bases，管理员）。
 *
 * <p><b>归属依据（为什么放 cs-ingestion 而非 cs-knowledge）</b>：① 契约把 /api/kb/* 归为一组，
 * 现有 {@link KbDocumentController} 已在本模块，知识库 CRUD 与文档 CRUD 同属一组入口，放一起守
 * CONVENTIONS §4 的命名语义与单一职责；② cs-knowledge 的定位是「混合检索 + 元数据」，全模块
 * {@code @RestController} 命中为 0，不含任何 HTTP 入口；③ 依赖方向 cs-ingestion → cs-knowledge
 * 合法（§2），本 Controller 仅注入对方的 Mapper 与实体（§2 只禁止依赖对方 impl 类），
 * 沿用 {@link KbDocumentController} 既有的跨模块注入 Mapper 风格。</p>
 *
 * <p><b>权限</b>：全部端点仅 ADMIN，方法首行调用 {@link SecurityUtils#requireAdmin()}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/bases")
@RequiredArgsConstructor
public class KbKnowledgeBaseController {

    /** 默认向量化模型标识（对齐 D13；前端新建时不发此字段，由后端填充） */
    private static final String DEFAULT_EMBEDDING_MODEL = "bge-m3";

    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;

    /**
     * 知识库列表。仅 ADMIN。
     *
     * @return 全部知识库（MVP 单库，直接 selectList(null) 后转 VO）
     */
    @GetMapping
    public R<List<KnowledgeBaseVO>> list() {
        SecurityUtils.requireAdmin();
        List<KbKnowledgeBase> bases = kbKnowledgeBaseMapper.selectList(null);
        List<KnowledgeBaseVO> vos = bases.stream().map(KnowledgeBaseVO::from).toList();
        return R.ok(vos);
    }

    /**
     * 新建知识库。仅 ADMIN。
     *
     * <p>embeddingModel 前端不发，后端填默认 {@value #DEFAULT_EMBEDDING_MODEL}；status 固定填 1（启用）；
     * createdAt/updatedAt <b>显式置 {@link LocalDateTime#now()}</b>——不依赖 MySQL 的
     * DEFAULT CURRENT_TIMESTAMP，因为 MyBatis-Plus insert 后不回读 DB 生成值，Java 对象里会是 null。</p>
     *
     * @param request 名称（必填）+ 描述（可空，空串合法）
     * @return 新建并回读后的知识库视图
     */
    @PostMapping
    public R<KnowledgeBaseVO> create(@Valid @RequestBody KbCreateRequest request) {
        SecurityUtils.requireAdmin();
        KbKnowledgeBase kb = new KbKnowledgeBase();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setEmbeddingModel(DEFAULT_EMBEDDING_MODEL);
        kb.setStatus(1);
        LocalDateTime now = LocalDateTime.now();
        kb.setCreatedAt(now);
        kb.setUpdatedAt(now);
        kbKnowledgeBaseMapper.insert(kb);

        // 回读已落库行再转 VO，保证返回值与 DB 一致（含自增 id 与时间字段）
        KbKnowledgeBase saved = kbKnowledgeBaseMapper.selectById(kb.getId());
        log.info("新建知识库: id={}, name={}", saved.getId(), saved.getName());
        return R.ok(KnowledgeBaseVO.from(saved));
    }
}
