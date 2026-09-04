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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                LoginUser user = new LoginUser(
                        claims.get("uid", Long.class),
                        claims.getSubject(),
                        claims.get("role", String.class));
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                // token 无效/过期：清空上下文交给授权环节 401，不打断放行接口
                log.debug("JWT 校验失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
