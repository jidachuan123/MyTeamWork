package com.demo.consumer.auth.filter;

import com.demo.consumer.auth.jwt.JwtToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JwtFilter extends BasicHttpAuthenticationFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected boolean isLoginAttempt(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;
        String auth = req.getHeader("Authorization");
        return auth != null && auth.startsWith("Bearer ");
    }

    @Override
    protected boolean executeLogin(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;
        String auth = req.getHeader("Authorization");
        String token = auth.substring(7);  // 去掉 "Bearer "
        JwtToken jwtToken = new JwtToken(token);
        getSubject(request, response).login(jwtToken);
        return true;
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        if (isLoginAttempt(request, response)) {
            try {
                executeLogin(request, response);
            } catch (Exception e) {
                log.debug("JWT 登录失败: {}", e.getMessage());
                responseUnauthorized(response, "Token 无效或已过期");
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) {
        responseUnauthorized(response, "请先登录");
        return false;
    }

    /**
     * 返回 401 未授权 JSON 响应
     */
    private void responseUnauthorized(ServletResponse response, String message) {
        try {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            Map<String, Object> result = new HashMap<>();
            result.put("code", 401);
            result.put("msg", message);
            resp.getWriter().write(MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            log.error("写入 401 响应失败", e);
        }
    }
}
