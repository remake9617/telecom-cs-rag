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

import jakarta.servlet.DispatcherType;

/**
 * Spring Security 配置（M3 认证收口，D17）。
 *
 * <p><b>放行清单</b>（与前端/统筹会话对齐的关键契约，M3-report 详述）：</p>
 * <ul>
 *   <li>/api/auth/register、/api/auth/login —— 注册登录本身不能要求登录；</li>
 *   <li>/api/health/ping —— 无认证存活探针（不发起模型调用，为容器 healthcheck 预留）；</li>
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
                        // DEF-078：放行 ASYNC 异步派发，消 SSE 完成后的 AccessDenied 日志噪音。
                        // 安全性论证：① 异步派发只发生在主请求已通过鉴权之后，是同一请求的
                        //   后续阶段（容器内部 forward），不是新入口——主请求过不了鉴权就根本
                        //   不会进入 ASYNC 阶段；② DispatcherType 由容器按内部转发类型决定，
                        //   客户端无法构造一个 DispatcherType.ASYNC 的外部请求。故放行 ASYNC
                        //   不会让任何受保护端点绕过鉴权。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/logout",
                                "/api/health/ping",
                                "/error"
                        ).permitAll()
                        // DEF-092：logout 也放行——契约要求登出幂等（重复登出/已过期 token 均不报错），
                        // 但已拉黑的 token 会被 JWT 过滤器拦下，永远到不了 Controller，幂等无从谈起。
                        // 安全性论证：① Controller 直接从 Authorization 头取 token 写黑名单，
                        //   不依赖认证上下文；② 攻击者想“帮别人登出”必须先持有该 token，
                        //   而持有者本就能自行登出，无新增攻击面；③ 重复写黑名单是幂等操作。
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // JWT 过滤器在用户名密码认证过滤器之前：请求一进来就完成 token 认证
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
