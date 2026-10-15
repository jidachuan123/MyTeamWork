package com.demo.provider.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 销售详情3（SD3）专用数据源 —— MySQL(bcm 库)，2026-10-15 新建。
 *
 * ★ 与现有的 primaryDataSource(RDS_BC) / scDataSource(RDS_SC) 三源并存、互不影响；
 *   原有的 DataSourceConfig 未做任何改动。
 * ★ 配置来自独立的 application-sd3.yml（通过 @PropertySource + YamlPropertySourceFactory 加载），
 *   原 application.yml 不动。
 *
 * 只提供 DataSource + JdbcTemplate（SD3 不用 MyBatis，直接执行原生 SQL）。
 */
@Configuration
@PropertySource(value = "classpath:application-sd3.yml", factory = YamlPropertySourceFactory.class)
public class Sd3DataSourceConfig {

    @Bean(name = "sd3DataSource")
    public DataSource sd3DataSource(
            @Value("${sd3.datasource.jdbc-url:}") String url,
            @Value("${sd3.datasource.username:}") String username,
            @Value("${sd3.datasource.password:}") String password,
            @Value("${sd3.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driver,
            @Value("${sd3.datasource.hikari.maximum-pool-size:5}") int maxPool,
            @Value("${sd3.datasource.hikari.minimum-idle:1}") int minIdle,
            @Value("${sd3.datasource.hikari.connection-timeout:20000}") long connTimeout) {
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
        config.setPoolName("SD3-MySQL-Pool");
        return new HikariDataSource(config);
    }

    @Bean(name = "sd3JdbcTemplate")
    public JdbcTemplate sd3JdbcTemplate(@Qualifier("sd3DataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
