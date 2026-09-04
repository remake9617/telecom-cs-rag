package com.cs.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cs.framework.common.PageVO;
import com.cs.system.entity.SysUser;
import com.cs.system.mapper.SysUserMapper;
import com.cs.system.service.SysUserService;
import com.cs.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理服务实现。
 *
 * <p>keyword 用 LambdaQueryWrapper 参数化拼接（非字符串拼 SQL），
 * 天然规避 LIKE 注入；模糊匹配 username/nickname 两个字段（契约第 7 节）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    @Override
    public PageVO<UserVO> pageUsers(String keyword, long current, long size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getNickname, kw));
        }
        wrapper.orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(Page.of(current, size), wrapper);
        List<UserVO> records = page.getRecords().stream().map(UserVO::from).toList();
        return PageVO.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
