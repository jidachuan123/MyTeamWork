package com.demo.provider.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 集团驾驶舱数据源（192.168.0.120 / RDS_SC）—— 2026-11-23 新建。
 *
 * 只提供一套 Bean：
 *   cockpitDataSource / cockpitJdbcTemplate
 *
 * ★ 只读：账号 readonly_jdc 本身只有查询权限，连接池再 setReadOnly(true) 兜一层。
 *   驾驶舱相关代码只允许 SELECT。
 * ★ 与现有数据源并存、互不影响：
 *     primary(RDS_BC@.66) / sc(RDS_SC@.66) / sd3(MySQL) / sd4(RDS_SC@.66) / rds27(.27)
 *   DataSourceConfig、Sd3/Sd4/Rds27 配置一行未动。
 * ★ 配置来自独立文件 application-cockpit.yml（@PropertySource + YamlPropertySourceFactory）。
 * ★ @Lazy + initializationFailTimeout=-1：.120 不可达也不影响 provider 启动，
 *   首次真正取连接时才报错（前端驾驶舱会自动退回示例数据，避免白屏）。
 */
@Configuration
@PropertySource(value = "classpath:application-cockpit.yml", factory = YamlPropertySourceFactory.class)
public class CockpitDataSourceConfig {

    @Bean(name = "cockpitDataSource")
    @Lazy
    public DataSource cockpitDataSource(
            @Value("${cockpit.datasource.url:}") String url,
            @Value("${cockpit.datasource.username:}") String username,
            @Value("${cockpit.datasource.password:}") String password,
            @Value("${cockpit.datasource.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}") String driver,
            @Value("${cockpit.datasource.read-only:true}") boolean readOnly,
            @Value("${cockpit.datasource.hikari.maximum-pool-size:3}") int maxPool,
            @Value("${cockpit.datasource.hikari.minimum-idle:1}") int minIdle,
            @Value("${cockpit.datasource.hikari.connection-timeout:30000}") long connTimeout) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        if (driver != null && !driver.trim().isEmpty()) {
            config.setDriverClassName(driver.trim());
        }
        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connTimeout);
        config.setReadOnly(readOnly);
        // -1 = 启动时不校验连接，.120 不可达也不影响 provider 启动
        config.setInitializationFailTimeout(-1);
        config.setPoolName("Cockpit-JDC-Pool");
        return new HikariDataSource(config);
    }

    @Bean(name = "cockpitJdbcTemplate")
    @Lazy
    public JdbcTemplate cockpitJdbcTemplate(@Qualifier("cockpitDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
