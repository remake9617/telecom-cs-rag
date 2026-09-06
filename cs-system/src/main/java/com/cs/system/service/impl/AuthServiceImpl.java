package com.cs.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.framework.common.ErrorCode;
import com.cs.framework.exception.BizException;
import com.cs.system.entity.SysUser;
import com.cs.system.mapper.SysUserMapper;
import com.cs.system.security.TokenRevocationService;
import com.cs.system.service.AuthService;
import com.cs.system.util.JwtService;
import com.cs.system.vo.LoginVO;
import com.cs.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * 认证服务实现。
 *
 * <p><b>为什么手动查库 + BCrypt matches，而不是 DaoAuthenticationProvider</b>：
 * 本系统用户体量小、角色单一，走完整的 UserDetailsService 体系会把
 * 「用户不存在/禁用/密码错误」三种业务语义都压进 AuthenticationException 继承树，
 * 错误码（5001/5003/5004）反而难对应；手动流程直白可控、错误码一一映射，
 * BCrypt 校验逻辑不变（M3-report 详述）。</p>
 *
 * <p>登录失败统一返回 5003「用户名或密码错误」：不区分"用户不存在"与"密码错"，</p>
 * <p>避免用户名枚举探测（登录接口安全性基本要求）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册默认角色：访客（DESIGN 2.1） */
    public static final String ROLE_VISITOR = "VISITOR";

    private final SysUserMapper userMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public LoginVO register(String username, String password) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        // BCrypt 自带随机盐，同一明文每次密文不同，防彩虹表
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(username);
        user.setRole(ROLE_VISITOR);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("用户注册: id={}, username={}, role={}", user.getId(), username, ROLE_VISITOR);
        return new LoginVO(jwtService.generate(user.getId(), username, ROLE_VISITOR), UserVO.from(user));
    }

    @Override
    public LoginVO login(String username, String password) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        log.info("用户登录: id={}, username={}", user.getId(), username);
        return new LoginVO(jwtService.generate(user.getId(), user.getUsername(), user.getRole()), UserVO.from(user));
    }

    @Override
    public UserVO me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return UserVO.from(user);
    }

    @Override
    public void logout(String token) {
        try {
            Claims claims = jwtService.parse(token);
            tokenRevocationService.revoke(token, claims);
        } catch (JwtException | IllegalArgumentException e) {
            // 登出幂等：token 已过期/非法时无需拉黑（本来就无法通过过滤器）
            log.debug("登出时 token 已无效，跳过拉黑: {}", e.getMessage());
        }
    }

    @Override
    public void invalidateUser(Long userId) {
        tokenRevocationService.invalidateUser(userId);
    }
}
