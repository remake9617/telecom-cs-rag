package com.cs.system.vo;

import lombok.Data;

/**
 * 用户视图对象（契约 UserVO：{id, username, nickname, role}；绝不含密码）。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    /** VISITOR / AGENT / ADMIN */
    private String role;
}
