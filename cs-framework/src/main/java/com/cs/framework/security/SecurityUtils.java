package com.cs.framework.security;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证上下文工具 —— 业务代码获取当前登录用户的唯一入口（并行开发公共契约）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>封装 SecurityContextHolder，业务模块无需直接依赖 Spring Security API；</li>
 *   <li>受保护端点调用 {@link #requireUserId()} 或 {@link #requireAdmin()} 时，
 *       若 SecurityContext 无有效认证信息则直接抛 {@link BizException}（1002），
 *       不再提供任何开发期回退。</li>
 * </ul>
 *
 * <p><b>安全性论证（移除 DEV_FALLBACK_USER 的依据）：</b></p>
 * <ul>
 *   <li>SecurityConfig 放行清单仅包含 4 条路径：{@code /api/auth/register}、
 *       {@code /api/auth/login}、{@code /api/health/ping}、{@code /error}。
 *       这 4 个端点的 Controller 方法均<b>不调用</b> SecurityUtils，
 *       因此放行路径不会触发未认证异常。</li>
 *   <li>保留回退的风险：一旦将来某端点漏配 Security（permitAll 误配），
 *       该端点会静默以 {@code id=1, role=ADMIN} 执行，等同于全权限匿名访问，
 *       属严重安全隐患。</li>
 *   <li>移除回退后，漏配 Security 的端点会立即暴露为 1002 异常，
 *       开发者在联调阶段即可发现配置遗漏，fail-fast 优于 silent-fallback。</li>
 * </ul>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 取当前登录用户；未认证时返回 null（调用方自行决定兜底策略）。
     */
    public static LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    /**
     * 取当前用户 ID；未认证时直接抛 1002（UNAUTHORIZED）。
     *
     * <p>调用方为受保护端点（需 JWT），JwtAuthenticationFilter 已保证
     * 合法请求到达此处时 SecurityContext 非空。</p>
     *
     * @return 当前登录用户 ID
     * @throws BizException 1002 未认证或登录已过期
     */
    public static Long requireUserId() {
        LoginUser user = getCurrentUser();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    /**
     * 校验当前用户是管理员；未认证抛 1002，非管理员抛 1003。
     *
     * <p>调用方为管理类端点（统计看板、系统设置、AI 健康探测等），
     * 前端通过菜单权限控制入口可见性，后端通过本方法做最终防线。</p>
     *
     * @throws BizException 1002 未认证 或 1003 无权访问
     */
    public static void requireAdmin() {
        LoginUser user = getCurrentUser();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ADMIN".equals(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该操作仅管理员可用");
        }
    }
}
