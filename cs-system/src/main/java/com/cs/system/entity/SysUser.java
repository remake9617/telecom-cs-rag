package com.cs.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（对应表 sys_user，三角色 VISITOR/AGENT/ADMIN）。
 *
 * <p>MVP 采用「单角色字段」而非完整 RBAC 关联表（DESIGN 2.1 注：sys_role/sys_permission
 * 建表但 MVP 不做关联映射），角色直接存 role 列——够用、可讲、阶段二可平滑升级。</p>
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文（绝不出库到 VO） */
    private String password;

    private String nickname;

    /** VISITOR / AGENT / ADMIN */
    private String role;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
