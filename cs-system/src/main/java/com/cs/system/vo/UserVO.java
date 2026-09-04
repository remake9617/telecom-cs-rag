package com.cs.system.vo;

import com.cs.system.entity.SysUser;
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

    /**
     * 实体 → VO（唯一映射入口，避免各处重复拼装且防止密码字段外泄）。
     */
    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        return vo;
    }
}
