package com.cs.system.config;

import com.cs.system.security.JwtAuthenticationFilter;
import com.cs.system.security.RestAccessDeniedHandler;
import com.cs.system.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（M3 认证收口，D17）。
 *
 * <p><b>放行清单</b>（与前端/统筹会话对齐的关键契约，M3-report 详述）：</p>
 * <ul>
 *   <li>/api/auth/register、/api/auth/login —— 注册登录本身不能要求登录；</li>
 *   <li>/api/health/** —— 存活探针；</li>
 *   <li>/error —— Spring Boot 错误页转发，不放行会把真实错误包装成 401，排障困难；</li>
 *   <li>其余 /api/** 一律 authenticated；<b>SSE /api/qa/chat/stream 不放行</b>：
 *       前端用 fetch 携带 Authorization 头，标准过滤器链自然生效。</li>
 * </ul>
 *
 * <p>无状态（STATELESS）：JWT 自包含身份，服务端不建 HttpSession——
 * 水平扩展无需会话共享，这也是选 JWT 而非 Session 的核心理由之一。</p>
 *
 * <p>接口内 RBAC 采用编程式 SecurityUtils.requireAdmin()（错误码统一走 R 体系），
 * 故此处不启用 @EnableMethodSecurity。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** BCrypt：自随机盐 + 慢哈希（强度 10），密码存储标准选择 */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT 方案下 CSRF 无意义（不用 Cookie 携带会话凭证，无跨站伪造面）
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/health/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // JWT 过滤器在用户名密码认证过滤器之前：请求一进来就完成 token 认证
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
