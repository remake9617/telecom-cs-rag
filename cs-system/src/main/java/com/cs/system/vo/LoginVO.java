package com.cs.system.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册响应（契约：{token, user}）。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    /** JWT，后续请求放请求头 Authorization: Bearer <token> */
    private String token;

    private UserVO user;
}
