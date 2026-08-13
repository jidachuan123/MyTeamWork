package com.demo.consumer.portal.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.demo.consumer.auth.jwt.JwtUtil;
import com.demo.consumer.utils.ApiResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 门户系统 - 单点登录(SSO)支持接口
 *
 * 设计说明：
 *  - 登录复用 /consumer/auth/login（JWT）
 *  - 门户登录后，进入某子系统前，调用 /sso-ticket 生成一次性票据
 *  - 子系统拿到 ticket 后，调用 /validate-ticket 换取用户信息（模拟各子系统回调）
 *  - ticket 使用 JWT 签名实现，无状态，无需建表
 */
@Slf4j
@RestController
@RequestMapping("/consumer/portal")
@RequiredArgsConstructor
public class PortalController {

    private final JwtUtil jwtUtil;

    @Value("${jwt.secret:demo-springcloud-secret-key-2026}")
    private String secret;

    /** ticket 有效期（毫秒）：2 分钟 */
    private static final long TICKET_EXPIRE = 2 * 60 * 1000L;

    /**
     * 当前登录用户信息（门户首页展示用）
     */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        try {
            String token = resolveToken(auth);
            DecodedJWT jwt = jwtUtil.verify(token);

            Map<String, Object> data = new HashMap<>();
            data.put("userId", jwt.getClaim("userId").asLong());
            data.put("username", jwt.getClaim("username").asString());
            String realName = jwt.getClaim("realName").isNull() ? null : jwt.getClaim("realName").asString();
            data.put("realName", realName == null ? jwt.getClaim("username").asString() : realName);
            return ApiResult.ok(data);
        } catch (Exception e) {
            return ApiResult.failed("Token 无效或已过期");
        }
    }

    /**
     * 生成 SSO 一次性票据
     * 门户点击"进入子系统"时调用，返回 ticket + 子系统跳转地址
     */
    @PostMapping("/sso-ticket")
    public ApiResult<Map<String, Object>> createSsoTicket(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String token = resolveToken(auth);
            DecodedJWT jwt = jwtUtil.verify(token);

            Long userId = jwt.getClaim("userId").asLong();
            String username = jwt.getClaim("username").asString();
            String realName = jwt.getClaim("realName").asString();

            // 生成一次性 SSO ticket（短时效、带 ticketType 标识）
            String ticket = JWT.create()
                    .withClaim("ticketType", "SSO")
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("realName", realName)
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + TICKET_EXPIRE))
                    .sign(Algorithm.HMAC256(secret));

            Map<String, Object> data = new HashMap<>();
            data.put("ticket", ticket);
            data.put("expireSeconds", TICKET_EXPIRE / 1000);
            return ApiResult.ok(data);
        } catch (Exception e) {
            return ApiResult.failed("Token 无效或已过期");
        }
    }

    /**
     * 子系统验证票据（模拟子系统 A/B/C 回调门户换取用户信息）
     */
    @GetMapping("/validate-ticket")
    public ApiResult<Map<String, Object>> validateTicket(@RequestParam("ticket") String ticket) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(ticket);
            if (!"SSO".equals(jwt.getClaim("ticketType").asString())) {
                return ApiResult.failed("票据类型错误");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", jwt.getClaim("userId").asLong());
            data.put("username", jwt.getClaim("username").asString());
            String realName = jwt.getClaim("realName").isNull() ? null : jwt.getClaim("realName").asString();
            data.put("realName", realName == null ? jwt.getClaim("username").asString() : realName);
            data.put("authType", "SSO");
            return ApiResult.ok(data);
        } catch (Exception e) {
            return ApiResult.failed("票据无效或已过期");
        }
    }

    /**
     * 子系统配置列表（门户首页展示 A/B/C 三个子系统）
     */
    @GetMapping("/subsystems")
    public ApiResult<List<Map<String, Object>>> subsystems() {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(buildSubsystem("A", "子系统A", "/sub/a",
                "报表查询系统（模拟）", "模拟子系统A的独立页面", "#409EFF", "DataAnalysis"));
        list.add(buildSubsystem("B", "子系统B", "/sub/b",
                "商品管理系统（模拟）", "模拟子系统B的独立页面", "#67C23A", "Goods"));
        list.add(buildSubsystem("C", "子系统C", "/sub/c",
                "外部对接系统（模拟）", "模拟子系统C的独立页面", "#E6A23C", "Connection"));

        return ApiResult.ok(list);
    }

    private Map<String, Object> buildSubsystem(String code, String name, String path, String desc, String remark, String color, String icon) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("path", path);
        m.put("desc", desc);
        m.put("remark", remark);
        m.put("color", color);
        m.put("icon", icon);
        return m;
    }

    private String resolveToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未提供有效 Token");
        }
        return auth.substring(7);
    }

    @Data
    public static class SsoTicketRequest {
        private String appCode;
    }
}
