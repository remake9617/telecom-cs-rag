package com.cs.framework.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 限流配置注册：cs-framework 自身无启动类，由业务模块统一扫描装配（CsApplication 位于
 * {@code com.cs} 根包，自动覆盖 {@code com.cs.framework} 子包），此处仅负责把
 * {@link RateLimitProperties} 纳入配置绑定。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
