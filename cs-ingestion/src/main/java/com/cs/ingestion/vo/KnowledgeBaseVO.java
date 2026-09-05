package com.cs.ingestion.vo;

import com.cs.knowledge.entity.KbKnowledgeBase;
import lombok.Data;

/**
 * 知识库视图对象（契约 KnowledgeBaseVO：{id, name, description, embeddingModel, status}）。
 *
 * <p>对齐 contract/rest-api.md 第 3 节与第 96 行 VO 速查。刻意不含 createdAt/updatedAt——
 * 契约与前端类型（types/index.ts）均未声明，下发只增加无意义载荷。</p>
 */
@Data
public class KnowledgeBaseVO {

    private Long id;

    private String name;

    /** 描述；实体为 null 时回填 ""（前端类型声明为必填 string，缺失会渲染出 undefined） */
    private String description;

    /** 向量化模型标识（默认 bge-m3，对齐 D13） */
    private String embeddingModel;

    /**
     * 启用状态：ACTIVE / DISABLED（<b>字符串</b>，非 Integer）。
     *
     * <p>实体 {@link KbKnowledgeBase#getStatus()} 是 Integer(1 启用 / 0 停用)，
     * 而前端 types/index.ts 声明为 string 且 KnowledgeBases.tsx 直接
     * {@code <Tag color="green">{kb.status}</Tag>}。若原样透传 Integer，
     * 答辩现场会渲染出一个绿色的「1」——TS 编译期无法捕获此类语义漂移，
     * 故必须在此做 Integer→String 映射。</p>
     */
    private String status;

    /**
     * 实体 → VO（唯一映射入口，遵循项目既有 UserVO.from 实践，避免各处重复拼装）。
     *
     * @param kb 知识库实体，非 null
     * @return 契约形态的知识库视图对象
     */
    public static KnowledgeBaseVO from(KbKnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        // description 为 null 时兜底为空串，保证前端必填 string 类型契约
        vo.setDescription(kb.getDescription() != null ? kb.getDescription() : "");
        vo.setEmbeddingModel(kb.getEmbeddingModel());
        vo.setStatus(mapStatus(kb.getStatus()));
        return vo;
    }

    /**
     * Integer 状态码 → 契约字符串。含 null 兜底：未知/缺失一律视为 DISABLED，
     * 绝不把裸数字或 null 下发给前端。
     */
    private static String mapStatus(Integer status) {
        if (status == null) {
            return "DISABLED";
        }
        return status == 1 ? "ACTIVE" : "DISABLED";
    }
}
