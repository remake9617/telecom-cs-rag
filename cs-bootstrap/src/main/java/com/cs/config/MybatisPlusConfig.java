package com.cs.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置（启动装配层持有，M3 引入）。
 *
 * <p>分页查询（工单列表等）必须注册 {@link PaginationInnerInterceptor}，
 * 否则 {@code selectPage} 不追加 LIMIT、退化为全表查询——这是 MyBatis-Plus
 * 的「插件式」设计：分页能力不是默认开启的。</p>
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
