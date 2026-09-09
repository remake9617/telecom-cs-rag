package com.cs.framework.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 限流配置（{@code cs.ratelimit.*}），允许运维在 application.yml 中按规则名覆盖
 * {@link RateLimit} 注解的默认窗口与阈值——改配置无需改代码、无需重新编译。
 *
 * <p>yml 形态：</p>
 * <pre>
 * cs:
 *   ratelimit:
 *     enabled: true
 *     rules:
 *       qa-chat-stream:        # 与 @RateLimit(name="qa-chat-stream") 对应
 *         window-seconds: 60
 *         max-requests: 10
 * </pre>
 *
 * <p>为什么用 {@code Map&lt;String, Rule&gt;} 而非逐端点硬编码字段：限流点是会增长的
 * （当前仅流式问答，将来可扩展到 /api/kb 上传等），Map 结构新增规则零代码改动。
 * 字段用包装类型：yml 未配置的项回落到注解默认值，支持部分覆盖。</p>
 */
@Data
@ConfigurationProperties(prefix = "cs.ratelimit")
public class RateLimitProperties {

    /** 限流总开关：false 时所有 @RateLimit 注解直通（压测/联调时用） */
    private boolean enabled = true;

    /** 按规则名索引的覆盖规则，key = @RateLimit#name() */
    private Map<String, Rule> rules = new HashMap<>();

    /** 单条规则覆盖项；null 表示该项用注解默认值 */
    @Data
    public static class Rule {
        /** 滑动窗口长度（秒） */
        private Integer windowSeconds;
        /** 窗口内最大请求数 */
        private Integer maxRequests;
    }
}
