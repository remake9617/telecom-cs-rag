package com.cs.system.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验（D17，jjwt 0.12.x API）。
 *
 * <p>HS256 对称签名：单服务部署下签发与校验同钥，简单够用；
 * 秘钥走环境变量 JWT_SECRET（不入库，D19）。payload 自定义声明：
 * uid / username / role，校验时还原为 {@code LoginUser}。</p>
 *
 * <p>C2（DEF-028）：每个 token 带唯一 {@code jti}（JWT ID），作为登出黑名单
 * 的 Redis key（{@code jwt:blacklist:{jti}}）；历史无 jti 的旧 token 由
 * TokenRevocationService 回退到 token 摘要作 key，过滤器做兼容。</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireHours;

    public JwtService(@Value("${cs.jwt.secret}") String secret,
                      @Value("${cs.jwt.expire-hours:24}") long expireHours) {
        // HS256 要求秘钥长度 >= 256bit（32 字节），fail-fast 暴露配置错误
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("cs.jwt.secret 长度不足 32 字节（HS256 要求 >=256bit）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    /**
     * 签发 JWT。
     *
     * @param userId   用户 ID（写入自定义声明 uid）
     * @param username 用户名（写入 subject）
     * @param role     角色（写入自定义声明 role）
     */
    public String generate(Long userId, String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                // jti：token 唯一标识，登出黑名单用它作 Redis key（短于整串 token，且为后续审计追踪留基础）
                .id(UUID.randomUUID().toString())
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 JWT（签名/过期校验失败抛 {@link JwtException}）。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
