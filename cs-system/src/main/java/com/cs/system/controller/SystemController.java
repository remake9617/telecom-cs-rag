package com.cs.system.controller;

import com.cs.framework.common.PageVO;
import com.cs.framework.common.R;
import com.cs.framework.security.SecurityUtils;
import com.cs.system.service.SysUserService;
import com.cs.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理接口（对应 contract/rest-api.md 第 7 节 /api/system，仅 ADMIN）。
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserService sysUserService;

    /** 用户列表：分页 + keyword 模糊搜 username/nickname（管理员） */
    @GetMapping("/users")
    public R<PageVO<UserVO>> users(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String keyword) {
        SecurityUtils.requireAdmin();
        return R.ok(sysUserService.pageUsers(keyword, current, size));
    }
}
