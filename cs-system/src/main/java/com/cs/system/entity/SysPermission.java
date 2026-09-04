package com.cs.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限（对应表 sys_permission；MVP 仅建表对齐 schema，未启用关联映射）。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 权限编码（如 ticket:reply） */
    private String code;

    private LocalDateTime createdAt;
}
