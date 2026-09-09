package com.cs.framework.idempotent;

import com.cs.framework.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 幂等切面：Redis {@code SET key value NX EX ttl} 原子占位，防同一请求重复建单。
 *
 * <h3>Redis key 与生命周期（DESIGN §5.3 提案）</h3>
 * <pre>
 * idem:ticket:{userId}:{sha256(conversationId + "|" + question)}   String
 *   value = "__PROCESSING__"（占位）→ 建单成功后回写 ticketId
 *   TTL = 60s（从建单完成时刻重起算：幂等窗口覆盖「建单完成后用户刷新/重试」的全过程）
 * </pre>
 *
 * <h3>四种情形的处置</h3>
 * <ol>
 *   <li><b>占位成功 → 业务成功</b>：回写结果标识（ticketId），后续同键请求返回同一结果。</li>
 *   <li><b>占位成功 → 业务抛异常</b>：删除占位 key（允许用户立即重试，不留 60 秒死窗），
 *       异常原样上抛。</li>
 *   <li><b>占位失败，存量可解析</b>：经 {@link IdempotentReplay} 还原结果直接返回（幂等命中）；
 *       还原为 null（如工单已被删除）→ 删除占位、降级为正常建单（不报错）。</li>
 *   <li><b>占位失败，仍是 PROCESSING</b>：首次请求尚在建单中，短轮询等待（最多约 3 秒）；
 *       超时仍未回写（首次请求方异常且未走到 catch 清理、或建单耗时异常长）→ 删除占位、
 *       降级为正常建单——宁可极小概率多建一张，也不让用户拿到一个错误。</li>
 * </ol>
 *
 * <p><b>为什么用 SpEL 组键而非固定模板</b>：幂等键的业务构成（哪些参数参与判重）由
 * 注解使用方声明，切面保持通用——将来给别的端点加幂等只需打注解，不改切面。</p>
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

    /** 占位期间的临时值（区别于最终回写的业务标识） */
    private static final String PROCESSING = "__PROCESSING__";
    /** 占位冲突时的等待：10 轮 × 300ms ≈ 3 秒，覆盖正常建单耗时（毫秒级）加冗余 */
    private static final int WAIT_ROUNDS = 10;
    private static final long WAIT_INTERVAL_MS = 300;

    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    public IdempotentAspect(StringRedisTemplate redisTemplate, ApplicationContext applicationContext) {
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        Long userId = SecurityUtils.requireUserId();
        String redisKey = idempotent.prefix() + ":" + userId + ":"
                + sha256Hex(eval(idempotent.key(), pjp, null));

        // 原子占位：SET NX EX，并发下只有一个请求能占到（幂等正确性根基，区别于先 GET 后 SET）
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSING, Duration.ofSeconds(idempotent.ttlSeconds()));
        if (Boolean.TRUE.equals(acquired)) {
            try {
                Object result = pjp.proceed();
                if (!idempotent.resultId().isBlank()) {
                    String resultId = eval(idempotent.resultId(), pjp, result);
                    if (resultId != null) {
                        // 回写 ticketId 并重置 TTL：幂等窗口从建单完成时刻起算，语义更合理
                        redisTemplate.opsForValue()
                                .set(redisKey, resultId, Duration.ofSeconds(idempotent.ttlSeconds()));
                    }
                }
                return result;
            } catch (Throwable e) {
                redisTemplate.delete(redisKey);   // 业务失败允许立即重试
                throw e;
            }
        }

        // ===== 占位失败：同键请求已在处理中或已完成 =====
        String stored = waitForResult(redisKey);
        if (stored != null) {
            IdempotentReplay replay = applicationContext.getBean(idempotent.replay());
            Object replayed = replay.resolve(stored);
            if (replayed != null) {
                log.info("幂等命中，返回既有结果: key={}, storedValue={}", redisKey, stored);
                return replayed;
            }
            // 存量结果已被删除（如工单被删）：降级为正常建单，不报错
            log.warn("幂等存量结果已不存在，降级为正常执行: key={}, storedValue={}", redisKey, stored);
        } else {
            log.warn("幂等占位超时未回写（首次请求方可能异常），降级为正常执行: key={}", redisKey);
        }
        redisTemplate.delete(redisKey);
        return pjp.proceed();
    }

    /**
     * 短轮询等占位方回写结果；返回 null 表示「仍 PROCESSING（超时）」或「key 已消失」，
     * 两种情况调用方都降级为正常执行。
     */
    private String waitForResult(String key) {
        for (int i = 0; i < WAIT_ROUNDS; i++) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (!PROCESSING.equals(value)) {
                return value;
            }
            try {
                Thread.sleep(WAIT_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** SHA-256 十六进制摘要（JDK 标准库实现，无额外依赖；Spring 的 sha256Hex 仅收 InputStream） */
    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JVM 规范强制要求 SHA-256 可用，此分支不可达
            throw new IllegalStateException(e);
        }
    }

    /**
     * SpEL 求值：参数按名绑定（Boot 默认 -parameters 编译），#result 仅在回写阶段存在。
     * <p>安全边界：被求值的表达式字符串<b>只来自注解常量</b>（开发者写在代码里），
     * 用户输入仅作为求值上下文中的数据参与读取，永远不会被当作表达式执行。</p>
     */
    private String eval(String expr, ProceedingJoinPoint pjp, Object result) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        if (result != null) {
            ctx.setVariable("result", result);
        }
        return parser.parseExpression(expr).getValue(ctx, String.class);
    }
}
