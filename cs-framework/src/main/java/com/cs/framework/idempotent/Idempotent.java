package com.cs.framework.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 建单幂等注解（阶段二批次 0 路10，兑现 DESIGN §6.1 承诺）：同一用户在 TTL 窗口内
 * 重复提交同一请求，返回既有结果而非报错、也不产生第二条数据。
 *
 * <p><b>为什么只给建单加（范围裁决，Javadoc 固化）</b>：问答重复提问是合法行为
 * （用户可能真想再问一次），不该拦；{@code POST /api/feedback} 已是 upsert 改票语义、
 * 天然幂等。全仓唯一需要防的是「双击/网络重试导致重复建单」。</p>
 *
 * <p><b>幂等的正确语义</b>：同一请求得到<b>同一结果</b>，而不是返回一个错误——
 * 占位命中时由 {@link #replay()} 指定的提供方按存量值（如 ticketId）还原业务结果直接返回。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** Redis key 前缀（提案：{@code idem:ticket}），最终 key = 前缀:userId:sha256(key) */
    String prefix();

    /**
     * 业务键 SpEL（对方法参数求值），如 {@code "#request.conversationId + '|' + #request.question"}；
     * 经 SHA-256 摘要后进 key，避免长问题文本与特殊字符污染命名空间。
     */
    String key();

    /** 幂等窗口（秒），默认 60——够挡住双击与网络重试，又不妨碍用户稍后真的再提一次 */
    long ttlSeconds() default 60;

    /** 占位命中时的结果还原提供方（由业务模块实现，切面经 Spring 容器获取） */
    Class<? extends IdempotentReplay> replay();

    /**
     * 业务执行成功后从返回值提取存量标识的 SpEL（对 {@code #result} 求值），
     * 如 {@code "#result.data.id"}；留空则不回写（占位值保持 PROCESSING 到 TTL 过期）。
     */
    String resultId() default "";
}
