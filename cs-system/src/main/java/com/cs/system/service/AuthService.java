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
}
