package com.cs.system.service;

import com.cs.framework.common.PageVO;
import com.cs.system.vo.UserVO;

/**
 * 用户管理服务（rest-api 第 7 节 /api/system，仅 ADMIN）。
 */
public interface SysUserService {

    /**
     * 用户分页列表，支持按用户名/昵称模糊搜索。
     *
     * @param keyword 模糊关键词（可空 = 不过滤）
     * @param current 页码（1 起）
     * @param size    每页大小
     * @return 分页结果（VO 不含密码）
     */
    PageVO<UserVO> pageUsers(String keyword, long current, long size);
}
