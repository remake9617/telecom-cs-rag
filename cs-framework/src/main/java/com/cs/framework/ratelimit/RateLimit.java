package com.cs.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 拒绝式限流注解（阶段二批次 0 路10，兑现 DESIGN §6.1 承诺）。
 *
 * <p>打在 Controller 方法上，由 {@link RateLimitAspect} 在进入方法体<b>之前</b>判定：
 * 超过阈值直接拒绝，不走业务逻辑。对返回 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}
 * 的端点（如流式问答），拒绝出口是 SSE {@code error} 事件（code=1005）而非 JSON——
 * 前端以 {@code Accept: text/event-stream} 请求 SSE 端点，若切面抛异常走
 * {@code GlobalExceptionHandler} 的 JSON 通道，内容协商会失败（406）或 JSON 体被
 * 前端 SSE 解析器当成正文渲染；这也是 D32/DEF-088 已确立的架构口径：
 * <b>SSE 端点的错误一律走 SSE error 事件出口</b>。非 SSE 端点抛
 * {@code BizException(RATE_LIMITED)} → HTTP 200 + code=1005（R 体系）。</p>
 *
 * <p><b>限流与熔断的区别（答辩要点）</b>：限流保护<b>自己</b>不被用户刷垮（防内部资源被耗尽）；
 * 熔断保护<b>自己</b>不被上游供应商拖垮（防外部依赖故障扩散）。两者独立、都要有——
 * 路7 的 {@code ChatModelFacade} 做了后者，本注解做前者。</p>
 *
 * <p>窗口与阈值可在 {@code application.yml} 的 {@code cs.ratelimit.rules.<name>} 中覆盖
 * （{@link RateLimitProperties}），注解值仅作默认值，运维改配置无需改代码。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 规则名：既是 Redis key 的一部分，也是 yml 覆盖（cs.ratelimit.rules.&lt;name&gt;）的查找键 */
    String name();

    /** 滑动窗口长度（秒），默认 60 */
    int windowSeconds() default 60;

    /** 窗口内允许的最大请求数（按 userId 维度），默认 10 */
    int maxRequests() default 10;
}
