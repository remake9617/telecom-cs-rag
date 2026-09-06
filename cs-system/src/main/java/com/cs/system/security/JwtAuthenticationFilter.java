package com.cs.system.security;

import com.cs.framework.security.LoginUser;
import com.cs.system.util.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 —— Security 过滤器链中的"认证"环节（D17）。
 *
 * <p>流程：取 Authorization: Bearer 头 → 校验签名/过期 → 还原 LoginUser →
 * 写入 SecurityContext（权限 ROLE_&lt;role&gt;）。无 token / token 非法时
 * <b>不直接拒绝</b>，继续走过滤器链：由授权规则（anyRequest.authenticated）
 * 触发 {@link RestAuthenticationEntryPoint} 统一返回 401——认证与授权职责分离，
 * 放行接口（注册/登录）也因此不受影响。</p>
 *
 * <p>SSE 说明：POST /api/qa/chat/stream 走 fetch + Authorization 头，
 * 本过滤器是标准 OncePerRequestFilter，对 SSE 同样生效，无需特殊处理。</p>
 *
 * <p>C2（DEF-028）主动作废：签名/过期校验通过后还要过两道 Redis 检查——
 * ① 登出黑名单（{@code jwt:blacklist:{jti}}，EXISTS，O(1)）；
 * ② 用户级失效时间戳（{@code jwt:user-invalid:{uid}}，GET 后与 issuedAt 比对）。
 * <b>性能说明</b>：这是每个受保护请求都要走的路径，最多多两次 Redis 往返
 * （内网 RTT 亚毫秒级，相对下游模型调用/MySQL 查询可忽略）；
 * 本地缓存（Caffeine）可减往返但会牺牲登出/封号的即时性且需新增依赖，MVP 不引。
 * 两道检查任一命中均视同未认证：清空上下文交给授权环节 401，与非法 token 同路。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parse(token);
                Long userId = claims.get("uid", Long.class);
                // 主动作废检查：黑名单命中（登出）或早于用户级失效时间戳（禁用）→ 视同未认证
                if (tokenRevocationService.isRevoked(token, claims)
                        || tokenRevocationService.isUserInvalidated(userId, claims.getIssuedAt())) {
                    log.debug("JWT 已被主动作废（黑名单或用户级失效）: uid={}", userId);
                    SecurityContextHolder.clearContext();
                } else {
                    LoginUser user = new LoginUser(
                            userId,
                            claims.getSubject(),
                            claims.get("role", String.class));
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // token 无效/过期：清空上下文交给授权环节 401，不打断放行接口
                log.debug("JWT 校验失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
