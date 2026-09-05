package com.cs.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建知识库请求（rest-api 第 3 节 POST /api/kb/bases，入参 {name, description}）。
 *
 * <p>embeddingModel 前端不发，由后端填默认 bge-m3（对齐 D13）；status 后端固定填 1（启用）。</p>
 */
@Data
public class KbCreateRequest {

    /** 知识库名称，必填（长度对齐 schema.sql kb_knowledge_base.name VARCHAR(128)） */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称最长 128 字符")
    private String name;

    /**
     * 知识库描述，<b>可空</b>：前端会发空串，空串是合法值，故不加 @NotBlank。
     * 长度对齐 schema.sql kb_knowledge_base.description VARCHAR(512)。
     */
    @Size(max = 512, message = "知识库描述最长 512 字符")
    private String description;
}
