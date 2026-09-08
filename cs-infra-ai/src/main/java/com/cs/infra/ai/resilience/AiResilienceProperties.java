package com.cs.infra.ai.resilience;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型调用高可用封装层的可配参数（配置键前缀 {@code cs.ai.resilience}，application.yml 由路6 统一写入）。
 *
 * <p><b>所有字段都带合理默认值</b>：yml 未配置（路6 未落键）期间封装层按默认值工作、
 * 功能不缺失；备用供应商的 apiKey 为空时该供应商自动跳过注册（failover 链缩短而非报错），
 * 因此主链在任意配置状态下都可启动。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "cs.ai.resilience")
public class AiResilienceProperties {

    /** 总开关：false 时 failover 链收缩为仅主供应商（熔断与超时仍生效） */
    private boolean enabled = true;

    /** 是否启用多供应商 failover（false 时只用主供应商） */
    private boolean failoverEnabled = true;

    /** 同步调用整体超时（毫秒）：block 上限，覆盖首包+全部 token 生成 */
    private long callTimeoutMs = 30000;

    /** 连接超时（毫秒）：TCP 建连阶段，连不上快速切下一供应商 */
    private long connectTimeoutMs = 5000;

    /**
     * 流式首包探测阈值（毫秒）：订阅后到收到第一个 chunk 的时间超过它即判定该供应商不健康，
     * failover 到下一供应商。这是流式场景唯一可靠的 failover 时机（首包后内容已推送，无法重发）。
     */
    private long firstTokenTimeoutMs = 15000;

    private final Retry retry = new Retry();
    private final Circuit circuit = new Circuit();
    /** 备1：硅基流动免费 Chat 模型（7B 级，能力弱于 qwen-plus，降级=可用性优先于质量） */
    private final Backup siliconflow = new Backup("https://api.siliconflow.cn",
            "Qwen/Qwen2.5-7B-Instruct", "/v1/chat/completions");
    /** 备2：智谱 GLM-4-Flash（OpenAI 兼容路径为 /api/paas/v4/chat/completions，等用户申请 key 后启用） */
    private final Backup zhipu = new Backup("https://open.bigmodel.cn/api/paas",
            "glm-4-flash", "/v4/chat/completions");
    /** 备3：DeepSeek（极低价兜底，等用户申请 key 后启用） */
    private final Backup deepseek = new Backup("https://api.deepseek.com",
            "deepseek-chat", "/v1/chat/completions");

    @Data
    public static class Retry {
        /** 同步调用同一供应商的最大尝试次数（含首次）；流式不做同供应商重试（首包等待已花掉阈值时间，直接切下一家） */
        private int maxAttempts = 3;
        /** 指数退避基数（毫秒）：第 n 次重试等待 base * 2^(n-1) */
        private long backoffBaseMs = 500;
        /** 单次退避上限（毫秒） */
        private long backoffMaxMs = 2000;
    }

    @Data
    public static class Circuit {
        /** 滑动窗口大小（最近 N 次调用样本） */
        private int windowSize = 10;
        /** 最小样本数：窗口样本不足时不判失败率（避免冷启动误熔断） */
        private int minCalls = 5;
        /** 失败率阈值（0~1）：窗口内失败率 ≥ 该值则 OPEN */
        private double failureRateThreshold = 0.5;
        /** OPEN 持续时长（毫秒），到期后转 HALF_OPEN 放行试探 */
        private long openDurationMs = 30000;
        /** HALF_OPEN 并发试探许可数（其余请求在试探期继续快速失败） */
        private int halfOpenMaxTrials = 3;
        /** HALF_OPEN → CLOSED 所需试探成功次数 */
        private int halfOpenSuccessThreshold = 2;
    }

    /**
     * 备用供应商配置（OpenAI 兼容协议）。baseUrl/model 带内置默认值（yml 可覆盖）；
     * apiKey 为空则该供应商不注册进 failover 链（key 只能来自 yml 占位符或环境变量）。
     * chatPath 为 OpenAI 兼容 chat/completions 路径（各供应商不一致，故可配）。
     */
    @Data
    public static class Backup {
        private String baseUrl;
        private String apiKey;
        private String model;
        private final String chatPath;

        public Backup(String baseUrl, String model, String chatPath) {
            this.baseUrl = baseUrl;
            this.model = model;
            this.chatPath = chatPath;
        }
    }
}
