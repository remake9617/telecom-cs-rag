package com.cs.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 Mapper（MVP 建表对齐，未启用关联映射）。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
