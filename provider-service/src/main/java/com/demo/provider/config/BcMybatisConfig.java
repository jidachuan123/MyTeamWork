package com.demo.provider.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * BC 库 MyBatis 配置 — 商品/报表等业务表
 */
@Configuration
@MapperScan(basePackages = "com.demo.provider.mapper", sqlSessionFactoryRef = "bcSqlSessionFactory")
public class BcMybatisConfig {

    @Bean(name = "bcSqlSessionFactory")
    @Primary
    public SqlSessionFactory bcSqlSessionFactory(@Qualifier("primaryDataSource") DataSource dataSource,
                                                  MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setTypeAliasesPackage("com.demo.provider.entity");
        bean.setPlugins(mybatisPlusInterceptor);
        return bean.getObject();
    }

    @Bean(name = "bcTransactionManager")
    @Primary
    public DataSourceTransactionManager bcTransactionManager(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
