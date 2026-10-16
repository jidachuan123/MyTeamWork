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
 * 销售详情4（SD4）专用数据源 —— SQL Server(RDS_SC 库)，2026-10-16 新建。
 *
 * ★ 与现有的 primaryDataSource(RDS_BC) / scDataSource(RDS_SC 盘点用) / sd3DataSource(MySQL) 并存、互不影响；
 *   原有的 DataSourceConfig 未做任何改动。
 * ★ 配置来自独立的 application-sd4.yml（通过 @PropertySource + YamlPropertySourceFactory 加载），
 *   原 application.yml 不动。
 * ★ 实测：SD4 的取数表（tb_io_om / tb_io_om_g 等）在 RDS_SC 库，RDS_BC 无此表。
 *
 * 只提供 DataSource + JdbcTemplate（SD4 不用 MyBatis，直接执行原生 SQL）。
 */
@Configuration
@PropertySource(value = "classpath:application-sd4.yml", factory = YamlPropertySourceFactory.class)
public class Sd4DataSourceConfig {

    @Bean(name = "sd4DataSource")
    public DataSource sd4DataSource(
            @Value("${sd4.datasource.url:}") String url,
            @Value("${sd4.datasource.username:}") String username,
            @Value("${sd4.datasource.password:}") String password,
            @Value("${sd4.datasource.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}") String driver,
            @Value("${sd4.datasource.hikari.maximum-pool-size:5}") int maxPool,
            @Value("${sd4.datasource.hikari.minimum-idle:1}") int minIdle,
            @Value("${sd4.datasource.hikari.connection-timeout:30000}") long connTimeout) {
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
        config.setPoolName("SD4-SQLServer-Pool");
        return new HikariDataSource(config);
    }

    @Bean(name = "sd4JdbcTemplate")
    public JdbcTemplate sd4JdbcTemplate(@Qualifier("sd4DataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
