package com.demo.consumer.auth.jwt;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * JWT Token，实现 Shiro 的 AuthenticationToken 接口
 */
public class JwtToken implements AuthenticationToken {

    private final String token;

    public JwtToken(String token) {
        this.token = token;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
