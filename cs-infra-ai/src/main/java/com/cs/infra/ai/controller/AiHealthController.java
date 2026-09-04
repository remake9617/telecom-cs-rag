package com.cs.infra.ai.controller;

import com.cs.framework.common.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 能力联通性检查（M0 验收用）——验证 Chat(qwen-plus) 与 Embedding(bge-m3) 的 API Key 是否可用。
 *
 * <p>启动后访问 {@code GET /api/health/ai}，一次性探测两个模型供应商的连通性，
 * 对应 DESIGN M0 验收标准「三方 Key 联通」。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class AiHealthController {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public AiHealthController(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/ai")
    public R<Map<String, Object>> checkAi() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1) Chat 联通（阿里 qwen-plus）
        try {
            String reply = chatClient.prompt().user("用一句话介绍你自己").call().content();
            result.put("chat", "OK");
            result.put("chatModel", "qwen-plus");
            result.put("chatReply", reply);
        } catch (Exception e) {
            log.error("Chat 联通失败", e);
            result.put("chat", "FAIL");
            result.put("chatError", e.getMessage());
        }

        // 2) Embedding 联通（硅基流动 bge-m3，期望 1024 维）
        try {
            float[] vector = embeddingModel.embed("5G畅享套餐资费");
            result.put("embedding", "OK");
            result.put("embeddingModel", "bge-m3");
            result.put("dimensions", vector.length);
        } catch (Exception e) {
            log.error("Embedding 联通失败", e);
            result.put("embedding", "FAIL");
            result.put("embeddingError", e.getMessage());
        }

        return R.ok(result);
    }
}
