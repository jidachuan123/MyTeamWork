package com.demo.consumer.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Shiro 权限不足
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleUnauthorized(UnauthorizedException e) {
        log.warn("权限不足: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("msg", "权限不足: " + e.getMessage());
        return result;
    }

    /**
     * Shiro 未认证
     */
    @ExceptionHandler(org.apache.shiro.authz.UnauthenticatedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUnauthenticated(Exception e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("msg", "请先登录");
        return result;
    }
}
