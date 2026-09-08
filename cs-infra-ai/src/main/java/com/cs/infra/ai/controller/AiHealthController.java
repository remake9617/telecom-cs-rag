package com.cs.infra.ai.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.infra.ai.resilience.ChatModelFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 能力联通性检查与存活探针。
 *
 * <p>提供两个端点：</p>
 * <ul>
 *   <li>{@code GET /api/health/ping} —— 无认证存活探针，不发起任何模型调用，
 *       为将来容器 healthcheck 预留（SecurityConfig 已精确放行该路径）。</li>
 *   <li>{@code GET /api/health/ai} —— AI 能力联通性探测（需 ADMIN），
 *       真实发起一次 Chat + 一次 Embedding 调用，消耗供应商额度。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class AiHealthController {

    private final ChatModelFacade chatModelFacade;
    private final EmbeddingModel embeddingModel;

    /**
     * 无认证存活探针——不发起任何模型调用，仅证明应用已启动且可响应 HTTP 请求。
     *
     * <p>为将来 Docker healthcheck / K8s livenessProbe 预留；
     * SecurityConfig 已精确放行 {@code /api/health/ping}。</p>
     */
    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("pong");
    }

    /**
     * AI 能力联通性探测（仅 ADMIN）。
     *
     * <p>本端点会真实发起一次 Chat 调用（阿里 qwen-plus）和一次 Embedding 调用
     * （硅基流动 bge-m3），消耗供应商额度，故收归 ADMIN 权限。
     * 未认证或非管理员调用将分别抛 1002/1003。</p>
     */
    @GetMapping("/ai")
    public R<Map<String, Object>> checkAi() {
        SecurityUtils.requireAdmin();
        Map<String, Object> result = new LinkedHashMap<>();

        // 1) Chat 联通（阿里 qwen-plus）：走 facade.probe()——只打主供应商且不计熔断
        // （探测流量计入熔断会造成正反馈：探测越失败熔断越开、熔断开了探测更失败）
        try {
            String reply = chatModelFacade.probe(null, "用一句话介绍你自己");
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
