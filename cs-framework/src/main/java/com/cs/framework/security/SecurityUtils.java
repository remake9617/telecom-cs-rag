package com.cs.framework.security;

import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证上下文工具 —— 业务代码获取当前登录用户的唯一入口（并行开发公共契约）。
 *
 * <p>设计要点（M3-report 有详细说明）：</p>
 * <ul>
 *   <li>封装 SecurityContextHolder，业务模块无需直接依赖 Spring Security API；</li>
 *   <li>跨模块约定：cs-qa 的 QaController 后续由统筹会话集成时改用
 *       {@link #requireUserId()} 取真实 userId（替代现在的默认 userId=1）；</li>
 *   <li><b>开发期过渡</b>：M3 认证收口前，工单/反馈/统计接口需要可端到端自测，
 *       因此未认证时回退到 {@link #DEV_FALLBACK_USER}（id=1, role=ADMIN）。
 *       认证上线后，受保护接口必须携带 JWT 才能通过过滤器链，匿名请求根本到不了
 *       Controller，此回退仅对放行接口（注册/登录/health）生效，不会绕过鉴权。</li>
 * </ul>
 */
public final class SecurityUtils {

    /**
     * 开发期回退用户（与 M2 QaService.DEFAULT_USER_ID 对齐：id=1）。
     * TODO(M3-集成)：认证收口联调通过后，如需严格模式可删除回退逻辑。
     */
    public static final LoginUser DEV_FALLBACK_USER = new LoginUser(1L, "dev-admin", "ADMIN");

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
     * 取当前用户 ID；未认证时回退开发默认用户（见类注释），保证认证收口前接口可自测。
     */
    public static Long requireUserId() {
        LoginUser user = getCurrentUser();
        return user != null ? user.getUserId() : DEV_FALLBACK_USER.getUserId();
    }

    /**
     * 校验当前用户是管理员，否则抛 1003（无权访问）。
     * 未认证（开发期）回退用户角色为 ADMIN，视为放行——认证上线后匿名请求
     * 已被过滤器链拦截，不会走到这里。
     */
    public static void requireAdmin() {
        LoginUser user = getCurrentUser();
        String role = user != null ? user.getRole() : DEV_FALLBACK_USER.getRole();
        if (!"ADMIN".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "该操作仅管理员可用");
        }
    }
}
