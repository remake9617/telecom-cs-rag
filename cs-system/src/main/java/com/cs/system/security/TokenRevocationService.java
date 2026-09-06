package com.cs.system.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT 主动作废（C2，DEF-028）：登出黑名单 + 用户禁用即时失效，Redis 读写。
 *
 * <p><b>为什么需要它</b>：JWT 自包含身份、无状态，签发后在有效期内无法否认——
 * 登出/封号后 token 仍可用 24h。本服务把「作废状态」外置到 Redis，过滤器每次
 * 请求多一次往返换取即时失效能力（见 {@link JwtAuthenticationFilter} 的性能说明）。</p>
 *
 * <p><b>黑名单 key 形态</b>：优先 {@code jwt:blacklist:{jti}}（token 唯一 ID，短且可审计）；
 * 历史签发的无 jti 旧 token 回退 {@code jwt:blacklist:sha:{SHA-256(token)}}——
 * 用摘要而非整串 token 作 key，避免 300+ 字符的长 key 占内存、拖慢键扫描。</p>
 *
 * <p><b>TTL 策略</b>：黑名单 TTL = token 剩余有效期（exp - now），而非固定 24h——
 * token 自然过期后黑名单键同步消失，不积累无用键。用户禁用时间戳 TTL = 24h
 * （token 最大有效期），之后该用户所有旧 token 必然已自然过期，键可清理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationService {

    /** 登出黑名单 key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
    /** 旧 token（无 jti）回退摘要方案的 key 中缀 */
    private static final String BLACKLIST_DIGEST_INFIX = "sha:";
    /** 用户级失效时间戳 key 前缀（该时刻之前签发的 token 一律拒绝） */
    private static final String USER_INVALID_KEY_PREFIX = "jwt:user-invalid:";
    /** 用户级失效键 TTL = token 最大有效期，之后旧 token 全部自然过期 */
    private static final Duration USER_INVALID_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * 把 token 加入黑名单（登出时调用）。
     *
     * <p>value 存 uid，便于排查「谁在何时登出了哪个 token」；TTL = 剩余有效期，
     * 已过期 token 直接跳过（自然失效，无需占键）。</p>
     */
    public void revoke(String token, Claims claims) {
        Date expiration = claims.getExpiration();
        long ttlSeconds = expiration == null ? 0
                : (expiration.getTime() - System.currentTimeMillis()) / 1000;
        if (ttlSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(revocationKey(token, claims),
                String.valueOf(claims.get("uid", Long.class)), Duration.ofSeconds(ttlSeconds));
        log.info("token 已加入黑名单: key={}, ttl={}s", revocationKey(token, claims), ttlSeconds);
    }

    /**
     * 查询 token 是否已吊销（登出黑名单命中）。
     *
     * <p>用 {@code hasKey}（Redis EXISTS）而非 GET：不需要 value，EXISTS 对
     * O(1) 键存在性检查更省带宽。</p>
     */
    public boolean isRevoked(String token, Claims claims) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(revocationKey(token, claims)));
    }

    /**
     * 用户级即时失效：记下失效时间戳，该时刻之前签发的所有 token 一律拒绝。
     *
     * <p>当前无调用方（项目没有「禁用用户」管理端点），预留给管理端补齐
     * 禁用功能时接入；期间可通过 redis-cli 手工写 {@code jwt:user-invalid:{userId}}
     * 达到同样效果。</p>
     */
    public void invalidateUser(Long userId) {
        redisTemplate.opsForValue().set(USER_INVALID_KEY_PREFIX + userId,
                String.valueOf(System.currentTimeMillis()), USER_INVALID_TTL);
        log.info("用户 token 即时失效已生效: userId={}, ttl={}s", userId, USER_INVALID_TTL.toSeconds());
    }

    /**
     * 判断该用户在 {@code issuedAt} 时刻签发的 token 是否已被用户级失效拒绝。
     */
    public boolean isUserInvalidated(Long userId, Date issuedAt) {
        String invalidSince = redisTemplate.opsForValue().get(USER_INVALID_KEY_PREFIX + userId);
        if (invalidSince == null) {
            return false;
        }
        // token 无 issuedAt 属于异常签发，从严拒绝
        return issuedAt == null || issuedAt.getTime() < Long.parseLong(invalidSince);
    }

    /** 黑名单 key：有 jti 用 jti，旧 token 回退 SHA-256 摘要 */
    private String revocationKey(String token, Claims claims) {
        String jti = claims.getId();
        if (jti != null && !jti.isBlank()) {
            return BLACKLIST_KEY_PREFIX + jti;
        }
        return BLACKLIST_KEY_PREFIX + BLACKLIST_DIGEST_INFIX + sha256Hex(token);
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 标准算法，编译期可保证存在，理论不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
