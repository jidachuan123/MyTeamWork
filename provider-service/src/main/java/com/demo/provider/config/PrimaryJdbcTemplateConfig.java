package com.demo.provider.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 主数据源（RDS_BC）的 JdbcTemplate —— 2026-10-16 新建。
 *
 * 背景：项目原先没有显式定义主 JdbcTemplate，靠 Spring Boot 自动配置
 * （JdbcTemplateAutoConfiguration 基于 @Primary 的 primaryDataSource 创建，bean 名 jdbcTemplate）。
 * 自从 SD3 自定义了 sd3JdbcTemplate 后，自动配置的 @ConditionalOnMissingBean(JdbcOperations)
 * 条件不再成立 → 自动配置失效；此时 UserService 里 by-type 注入的 JdbcTemplate 变成
 * 「唯一候选」sd3JdbcTemplate（隐性错连到 MySQL bcm 库）。SD4 再加 sd4JdbcTemplate 后
 * 变成 2 个候选 → 启动直接失败（NoUniqueBeanDefinitionException）。
 *
 * 本类显式补上 @Primary 的主 JdbcTemplate（bean 名保持 jdbcTemplate）：
 *   - UserService 等 by-type 注入拿到主库(RDS_BC)连接（语义正确，顺带修复隐性错连）
 *   - SD3/SD4 各自用 @Qualifier 注入自己的 JdbcTemplate，不受影响
 *   - 原有 DataSourceConfig 一行未动
 */
@Configuration
public class PrimaryJdbcTemplateConfig {

    @Bean(name = "jdbcTemplate")
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
