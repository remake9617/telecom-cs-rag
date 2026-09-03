package com.cs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 电信运营商智能客服系统 —— 启动类。
 *
 * <p>位于根包 {@code com.cs}，{@link SpringBootApplication} 默认扫描本包及所有子包，
 * 因此各业务模块（com.cs.framework / com.cs.infra.ai / com.cs.knowledge / com.cs.qa ...）
 * 的 Bean 都会被自动装配。</p>
 *
 * <p>M0 骨架阶段：仅验证多模块聚合与 Spring 上下文能正常启动；
 * 中间件（MySQL/ES/Redis）与模型 API Key 就绪后即可完整运行。</p>
 */
@SpringBootApplication
public class CsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsApplication.class, args);
    }
}
