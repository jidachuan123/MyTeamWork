package com.demo.provider.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.util.Properties;

/**
 * 让 {@code @PropertySource} 支持加载 .yml 文件（Spring 默认只支持 .properties）。
 *
 * 用途：SD3 的配置放在独立的 application-sd3.yml 里，靠 @PropertySource 引入，
 * 从而完全不修改原有的 application.yml（SD1/SD2 的配置）。
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());
        factory.afterPropertiesSet();
        Properties props = factory.getObject();
        if (props == null) {
            props = new Properties();
        }
        String srcName = (name != null) ? name : resource.getResource().getFilename();
        return new PropertiesPropertySource(srcName, props);
    }
}
