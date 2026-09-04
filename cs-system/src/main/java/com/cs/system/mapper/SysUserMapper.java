package com.cs.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cs.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
