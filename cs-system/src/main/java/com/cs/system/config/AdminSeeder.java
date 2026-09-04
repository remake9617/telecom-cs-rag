package com.cs.system.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cs.system.entity.SysUser;
import com.cs.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认管理员播种（对应 schema.sql 末行预留注释：admin 账号待 M3 用 BCrypt 生成后插入）。
 *
 * <p>仅当系统无任何 ADMIN 用户时创建，重复启动幂等；密码可用环境变量
 * ADMIN_DEFAULT_PASSWORD 覆盖（演示/开发默认 admin123，公网部署前必须改）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${cs.admin.default-password:admin123}")
    private String defaultPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long admins = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "ADMIN"));
        if (admins > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(defaultPassword));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setStatus(1);
        userMapper.insert(admin);
        log.warn("已创建默认管理员 admin（初始密码取 ADMIN_DEFAULT_PASSWORD，默认 admin123），公网部署前请立即修改");
    }
}
