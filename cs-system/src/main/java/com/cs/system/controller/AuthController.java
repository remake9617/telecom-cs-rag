package com.cs.system.controller;

import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.system.dto.LoginRequest;
import com.cs.system.dto.RegisterRequest;
import com.cs.system.service.AuthService;
import com.cs.system.vo.LoginVO;
import com.cs.system.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（对应 contract/rest-api.md 第 1 节 /api/auth，均为放行接口，me 除外）。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册访客账号，返回 JWT（放行） */
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request.getUsername(), request.getPassword()));
    }

    /** 登录，返回 JWT（放行） */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request.getUsername(), request.getPassword()));
    }

    /** 当前登录用户（需认证） */
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.me(SecurityUtils.requireUserId()));
    }
}
