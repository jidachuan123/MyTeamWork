package com.demo.provider.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 双数据源配置
 * - primaryDataSource: RDS_BC 库（原有用户查询用）
 * - scDataSource: RDS_SC 库（盘点报表专用）
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setPoolName("RDS_BC-Pool");
        return new HikariDataSource(config);
    }

    @Bean(name = "scDataSource")
    public DataSource scDataSource(
            @Value("${spring.datasource-sc.url}") String url,
            @Value("${spring.datasource-sc.username}") String username,
            @Value("${spring.datasource-sc.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setPoolName("RDS_SC-Pool");
        return new HikariDataSource(config);
    }
}
