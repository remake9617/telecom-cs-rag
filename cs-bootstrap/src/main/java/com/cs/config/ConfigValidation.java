package com.cs.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 配置 fail-fast 安全闸（闭环 {@link com.cs DEF-030}，DESIGN §4.1「配置 fail-fast」的实现）。
 *
 * <h3>为什么必须存在（缺陷严重性）</h3>
 * <p>{@code application.yml} 对 {@code cs.jwt.secret} 与 {@code cs.admin.default-password}
 * 提供了开发默认值兜底（否则本地未配 {@code .env} 时占位符无法解析、连启动都起不来）。
 * 这带来一个致命隐患：<b>公网部署时只要忘配一个环境变量，系统就会静默地用开发密钥启动</b>——
 * 等于把一个任何人都能拿到（就在开源仓库里）的 JWT 签名密钥挂到互联网上，
 * 攻击者可伪造任意用户（含 ADMIN）的合法 token；管理员弱密码则可直接被猜中登入后台。
 * 因此必须在启动的最早期把「弱配置」拦下来，而不是等到被打了才发现。</p>
 *
 * <h3>技术路线权衡（为什么选 EnvironmentPostProcessor）</h3>
 * <ul>
 *   <li><b>EnvironmentPostProcessor（本实现）</b>：在环境准备阶段、任何 Bean 创建之前运行，
 *       失败时整个 JVM 直接终止。弱密钥<b>不可能</b>被任何 Bean（如 JwtService）捕获到，
 *       也不会打开任何数据库连接池、绑定任何端口，是最彻底的 fail-fast。
 *       注册方式为 {@code META-INF/spring.factories}（Spring Boot 约定）。</li>
 *   <li>ApplicationRunner：上下文已完全启动、端口已绑定、JwtService 已捕获弱密钥后才检查，
 *       「拒绝启动」只是延迟版的自杀，拦截意义大打折扣，不选。</li>
 *   <li>@ConfigurationProperties + @Validated：只能校验「缺失/格式」，表达不了
 *       「值等于已知弱默认值」这种业务语义（需要自定义注解），且校验发生在 Bean 绑定期，
 *       时机仍晚于环境准备，不选。</li>
 * </ul>
 *
 * <h3>严格模式与本地宽松模式</h3>
 * <p>用 {@code cs.strict-config}（环境变量 {@code CS_STRICT_CONFIG}）开关，默认 {@code false}：
 * <ul>
 *   <li><b>严格模式</b>（公网/生产部署必须开启；激活 {@code prod} / {@code public} profile 时也自动开启）：
 *       发现任何问题 → 打印问题清单后抛异常，<b>拒绝启动</b>。</li>
 *   <li><b>宽松模式</b>（本地开发默认）：同样检查，但只打 WARN 不终止——
 *       否则本地开发者没配 {@code JWT_SECRET} 就起不了后端，是开发体验的倒退。</li>
 * </ul></p>
 *
 * <h3>生命周期限制说明</h3>
 * <p>本类运行于日志系统初始化之前（{@code ApplicationEnvironmentPreparedEvent} 在全部
 * EnvironmentPostProcessor 之后才发布），因此问题清单直接经 {@code System.err} 打印——
 * 保证在任何日志后端就绪时都必然可见。</p>
 *
 * <h3>安全约束</h3>
 * <p>清单只打印<b>键名与状态</b>（缺失 / 弱默认值 / 强度不足），<b>绝不回显配置值</b>，
 * 防止密钥经启动日志泄露。</p>
 */
public class ConfigValidation implements EnvironmentPostProcessor, Ordered {

    /** application.yml 中 JWT 密钥的开发兜底值——公网启动时等于它就等于裸奔 */
    static final String WEAK_JWT_SECRET = "dev-only-cs-server-jwt-secret-key-change-me-in-prod";

    /** 管理员默认密码的开发兜底值 */
    static final String WEAK_ADMIN_PASSWORD = "admin123";

    /** HS256 要求密钥 >= 32 字节（RFC 7518） */
    static final int MIN_JWT_SECRET_BYTES = 32;

    /** 激活这些 profile 任一时自动进入严格模式（双保险，防忘配 CS_STRICT_CONFIG） */
    private static final List<String> STRICT_PROFILES = Arrays.asList("prod", "public");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> issues = new ArrayList<>();

        // ---- 1. JWT 签名密钥：缺失 / 弱默认值 / 强度不足（HS256 >= 32 字节）----
        String jwtSecret = environment.getProperty("cs.jwt.secret");
        if (!StringUtils.hasText(jwtSecret)) {
            issues.add("[缺  失] cs.jwt.secret (JWT_SECRET)：JWT 将无法安全签发。生成方式：openssl rand -base64 48");
        } else if (WEAK_JWT_SECRET.equals(jwtSecret)) {
            issues.add("[弱默认] cs.jwt.secret (JWT_SECRET) 等于开发兜底密钥：该值在开源仓库里公开，"
                    + "公网部署等于允许任何人伪造包括 ADMIN 在内的任意用户 token");
        } else if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            issues.add("[强度低] cs.jwt.secret (JWT_SECRET) 不足 " + MIN_JWT_SECRET_BYTES
                    + " 字节（当前 " + jwtSecret.getBytes(StandardCharsets.UTF_8).length + " 字节）：HS256 安全性不达标，易被暴力破解");
        }

        // ---- 2. 管理员默认密码：缺失 / 弱默认值 ----
        String adminPassword = environment.getProperty("cs.admin.default-password");
        if (!StringUtils.hasText(adminPassword)) {
            issues.add("[缺  失] cs.admin.default-password (ADMIN_DEFAULT_PASSWORD)：管理员账号无法安全播种");
        } else if (WEAK_ADMIN_PASSWORD.equals(adminPassword)) {
            issues.add("[弱默认] cs.admin.default-password (ADMIN_DEFAULT_PASSWORD) 等于 admin123："
                    + "公网上可直接被猜中登入管理后台");
        }

        // ---- 3. 模型供应商 API Key：为空则核心能力（对话/检索/重排）全链路不可用 ----
        checkAiKey(environment, issues, "spring.ai.dashscope.api-key", "AI_DASHSCOPE_API_KEY",
                "Chat 主力（阿里百炼 qwen-plus）不可用，问答全链路降级");
        checkAiKey(environment, issues, "spring.ai.openai.api-key", "SILICONFLOW_API_KEY",
                "Embedding/Rerank（硅基流动）不可用，知识库检索失效");

        if (issues.isEmpty()) {
            return;
        }

        boolean strict = isStrictMode(environment);
        String header = "==================== 配置安全闸 (DEF-030 fail-fast) ====================";
        if (strict) {
            System.err.println(header);
            issues.forEach(i -> System.err.println("  " + i));
            System.err.println("  当前为严格模式(cs.strict-config=true 或 prod/public profile)：拒绝启动。");
            System.err.println("  修复：在 .env / 环境变量中提供真实强配置后重启（清单只列键名，不回显值）。");
            System.err.println("======================================================================");
            throw new IllegalStateException(
                    "配置校验失败（" + issues.size() + " 项），严格模式拒绝启动，详见上方问题清单");
        }
        System.err.println(header);
        issues.forEach(i -> System.err.println("  [WARN] " + i));
        System.err.println("  当前为本地宽松模式，允许继续启动；公网/生产部署必须置 CS_STRICT_CONFIG=true。");
        System.err.println("======================================================================");
    }

    /** 严格模式判定：显式开关优先，其次 prod/public profile 兜底 */
    private boolean isStrictMode(Environment environment) {
        if (environment.getProperty("cs.strict-config", Boolean.class, Boolean.FALSE)) {
            return true;
        }
        return Arrays.stream(environment.getActiveProfiles()).anyMatch(STRICT_PROFILES::contains);
    }

    private void checkAiKey(Environment environment, List<String> issues, String propertyKey,
                            String envKey, String impact) {
        String value = environment.getProperty(propertyKey);
        if (!StringUtils.hasText(value)) {
            issues.add("[缺  失] " + propertyKey + " (" + envKey + ")：" + impact);
        }
    }

    /**
     * 必须晚于 {@code ConfigDataEnvironmentPostProcessor}（order = HIGHEST_PRECEDENCE + 10）运行，
     * 否则读不到 application.yml 与 spring.config.import 进来的 .env 配置。
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
