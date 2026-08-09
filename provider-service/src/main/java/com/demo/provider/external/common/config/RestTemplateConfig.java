package com.demo.provider.external.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置 — 对外接口调用专用
 * <p>
 * 设置连接超时和读取超时，避免外部系统无响应时长时间阻塞。
 * 如需对接 HTTPS 的忽略证书校验，可替换为 HttpComponentsClientHttpRequestFactory + SSLContext。
 * </p>
 */
@Configuration
public class RestTemplateConfig {

    /** 连接超时: 10 秒 */
    private static final int CONNECT_TIMEOUT = 10_000;

    /** 读取超时: 30 秒 */
    private static final int READ_TIMEOUT = 30_000;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }
}
