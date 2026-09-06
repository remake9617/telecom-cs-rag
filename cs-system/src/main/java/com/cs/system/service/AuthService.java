package com.cs.system.service;

import com.cs.system.vo.LoginVO;
import com.cs.system.vo.UserVO;

/**
 * 认证服务（rest-api 第 1 节 /api/auth）。
 */
public interface AuthService {

    /** 注册访客账号并直接签发 JWT（注册即登录，减少一步交互） */
    LoginVO register(String username, String password);

    /** 登录校验（BCrypt）并签发 JWT */
    LoginVO login(String username, String password);

    /** 当前登录用户信息 */
    UserVO me(Long userId);

    /** 登出：把当前 token 加入 Redis 黑名单（C2，DEF-028） */
    void logout(String token);

    /**
     * 用户级即时失效：该时刻之前签发的所有 token 一律拒绝（禁用用户时调用）。
     *
     * <p>当前无调用方（项目没有「禁用用户」管理端点），待管理端补齐禁用
     * 功能时接入；不要为了使用它而新增管理端点。</p>
     */
    void invalidateUser(Long userId);
}
