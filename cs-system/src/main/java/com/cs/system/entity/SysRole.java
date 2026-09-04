package com.cs.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色（对应表 sys_role；MVP 仅建表对齐 schema，未启用关联映射）。
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 角色编码：ADMIN / AGENT / VISITOR */
    private String code;

    private LocalDateTime createdAt;
}
