package com.cs.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求（rest-api 第 1 节 POST /api/auth/login）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
