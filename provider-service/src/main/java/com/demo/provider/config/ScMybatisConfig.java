package com.demo.provider.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * SC 库 MyBatis 配置 — 权限认证表（sys_user/sys_role/sys_permission 等）
 */
@Configuration
@MapperScan(basePackages = "com.demo.provider.auth.mapper", sqlSessionFactoryRef = "scSqlSessionFactory")
public class ScMybatisConfig {

    @Bean(name = "scSqlSessionFactory")
    public SqlSessionFactory scSqlSessionFactory(@Qualifier("scDataSource") DataSource dataSource,
                                                  MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 扫描 entity 别名
        bean.setTypeAliasesPackage("com.demo.provider.auth.entity");
        bean.setPlugins(mybatisPlusInterceptor);
        return bean.getObject();
    }

    @Bean(name = "scTransactionManager")
    public DataSourceTransactionManager scTransactionManager(@Qualifier("scDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
