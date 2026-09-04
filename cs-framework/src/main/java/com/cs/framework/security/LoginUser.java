package com.cs.framework.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户（认证上下文载体）。
 *
 * <p>由 cs-system 的 {@code JwtAuthenticationFilter} 在校验 JWT 后写入 SecurityContext；
 * 业务代码经 {@link SecurityUtils} 读取，不直接接触 Spring Security API。</p>
 *
 * <p>MVP 的 RBAC 为「单角色」模型（sys_user.role：VISITOR/AGENT/ADMIN，见 DESIGN 2.1），
 * 因此这里只携带一个角色字符串，而非权限集合。</p>
 */
@Data
@AllArgsConstructor
public class LoginUser {

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色：VISITOR / AGENT / ADMIN */
    private String role;
}
