package com.demo.consumer.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:demo-springcloud-secret-key-2026}")
    private String secret;

    @Value("${jwt.expire:86400000}")  // 默认 24 小时
    private long expire;

    /**
     * 生成 JWT Token
     */
    public String generateToken(Long userId, String username) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expire))
                .sign(Algorithm.HMAC256(secret));
    }

    /**
     * 验证并解析 Token
     */
    public DecodedJWT verify(String token) {
        return JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
    }

    /**
     * 从 Token 中提取 userId
     */
    public Long getUserId(String token) {
        return verify(token).getClaim("userId").asLong();
    }

    /**
     * 从 Token 中提取 username
     */
    public String getUsername(String token) {
        return verify(token).getClaim("username").asString();
    }
}
