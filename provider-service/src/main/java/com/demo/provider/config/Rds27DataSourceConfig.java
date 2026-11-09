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
 * 192.168.0.27 数据库连接（新备机 / 测试源）—— 2026-11-09 新建。
 *
 * 提供两个独立数据源 + 各自的 JdbcTemplate：
 *   - rds27BcDataSource / rds27BcJdbcTemplate ：.27 上的 RDS_BC 库
 *   - rds27ScDataSource / rds27ScJdbcTemplate ：.27 上的 RDS_SC 库
 *
 * ★ 与现有四套数据源并存、互不影响：
 *     primaryDataSource(RDS_BC@.66) / scDataSource(RDS_SC@.66) / sd3DataSource(MySQL) / sd4DataSource(RDS_SC@.66)
 *   DataSourceConfig 一行未动。
 * ★ 配置来自独立文件 application-rds27.yml（@PropertySource + YamlPropertySourceFactory），
 *   与 application.yml / application-sd3.yml / application-sd4.yml 完全隔离。
 * ★ 全部 Bean 标了 @Lazy：本数据源当前只用于排查/对比，默认不建连也不影响启动；
 *   真正有代码 @Qualifier("rds27ScJdbcTemplate") 注入使用时才初始化连接池。
 *   Hikari 的 initializationFailTimeout 设为 -1：即使 .27 宕机也只是首次取连接时报错，
 *   不会让整个 provider-service 启动失败（与现有必须连通的 .66 数据源行为不同，这是刻意为之）。
 *
 * 用法示例：
 * <pre>
 *   public XxxService(@Qualifier("rds27ScJdbcTemplate") JdbcTemplate rds27Sc) { ... }
 * </pre>
 */
@Configuration
@PropertySource(value = "classpath:application-rds27.yml", factory = YamlPropertySourceFactory.class)
public class Rds27DataSourceConfig {

    // ---------------- .27 / RDS_BC ----------------

    @Bean(name = "rds27BcDataSource")
    @Lazy
    public DataSource rds27BcDataSource(
            @Value("${rds27.bc.url:}") String url,
            @Value("${rds27.bc.username:}") String username,
            @Value("${rds27.bc.password:}") String password,
            @Value("${rds27.bc.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}") String driver,
            @Value("${rds27.bc.hikari.maximum-pool-size:5}") int maxPool,
            @Value("${rds27.bc.hikari.minimum-idle:1}") int minIdle,
            @Value("${rds27.bc.hikari.connection-timeout:30000}") long connTimeout) {
        return buildDataSource(url, username, password, driver, maxPool, minIdle, connTimeout, "RDS27_BC-Pool");
    }

    @Bean(name = "rds27BcJdbcTemplate")
    @Lazy
    public JdbcTemplate rds27BcJdbcTemplate(@Qualifier("rds27BcDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // ---------------- .27 / RDS_SC ----------------

    @Bean(name = "rds27ScDataSource")
    @Lazy
    public DataSource rds27ScDataSource(
            @Value("${rds27.sc.url:}") String url,
            @Value("${rds27.sc.username:}") String username,
            @Value("${rds27.sc.password:}") String password,
            @Value("${rds27.sc.driver-class-name:com.microsoft.sqlserver.jdbc.SQLServerDriver}") String driver,
            @Value("${rds27.sc.hikari.maximum-pool-size:5}") int maxPool,
            @Value("${rds27.sc.hikari.minimum-idle:1}") int minIdle,
            @Value("${rds27.sc.hikari.connection-timeout:30000}") long connTimeout) {
        return buildDataSource(url, username, password, driver, maxPool, minIdle, connTimeout, "RDS27_SC-Pool");
    }

    @Bean(name = "rds27ScJdbcTemplate")
    @Lazy
    public JdbcTemplate rds27ScJdbcTemplate(@Qualifier("rds27ScDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // ---------------- 公共构造 ----------------

    private DataSource buildDataSource(String url, String username, String password, String driver,
                                       int maxPool, int minIdle, long connTimeout, String poolName) {
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
        // -1 = 启动时不校验连接，.27 不可达也不影响服务启动，首次 getConnection 时才抛错
        config.setInitializationFailTimeout(-1);
        config.setPoolName(poolName);
        return new HikariDataSource(config);
    }
}
