package com.demo.consumer.portal.controller;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.demo.consumer.auth.jwt.JwtUtil;
import com.demo.consumer.portal.config.PortalProperties;
import com.demo.consumer.utils.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.util.*;

/**
 * 门户系统 - 单点登录(SSO)支持接口
 *
 * 真实对接设计（方式A：共享密钥）：
 *  - 登录复用 /consumer/auth/login（JWT）
 *  - 门户登录后，进入某子系统前，调用 /sso-ticket 生成一次性票据（带 targetApp 目标应用标识）
 *  - 接口返回 ticket + redirectUrl，前端用 window.location.href 真实跳转到子系统域名
 *  - 子系统用「共享 JWT 密钥」本地验签 ticket（不回调门户）
 *  - 子系统配置在 application.yml 的 portal.subsystems（不再硬编码）
 *
 *  /validate-ticket 仅保留给「未拿到共享密钥的第三方」（方式B）或本工程内置的模拟子系统 B/C 使用。
 */
@Slf4j
@RestController
@RequestMapping("/consumer/portal")
@RequiredArgsConstructor
public class PortalController {

    private final JwtUtil jwtUtil;
    private final PortalProperties portalProperties;

    @Value("${jwt.secret}")
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
     * 门户点击"进入子系统"时调用，入参 body.appCode 指定目标子系统编码，
     * 返回 ticket + redirectUrl（真实子系统）或 path（模拟子系统）。
     */
    @PostMapping("/sso-ticket")
    public ApiResult<Map<String, Object>> createSsoTicket(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String appCode = body == null ? null : body.get("appCode");
            if (appCode == null || appCode.trim().isEmpty()) {
                return ApiResult.failed("缺少目标子系统编码 appCode");
            }
            PortalProperties.Subsystem sub = findByCode(appCode);
            if (sub == null) {
                return ApiResult.failed("未登记的子系统编码: " + appCode);
            }

            String token = resolveToken(auth);
            DecodedJWT jwt = jwtUtil.verify(token);

            Long userId = jwt.getClaim("userId").asLong();
            String username = jwt.getClaim("username").asString();
            String realName = jwt.getClaim("realName").asString();

            // 生成一次性 SSO ticket：
            //  - ticketType=SSO：与登录 JWT 区分，防止拿登录 token 冒充 ticket
            //  - targetApp=appCode：票据只对目标子系统有效，防止 A 的票被 B 使用
            String ticket = JWT.create()
                    .withClaim("ticketType", "SSO")
                    .withClaim("targetApp", appCode)
                    .withClaim("userId", userId)
                    .withClaim("username", username)
                    .withClaim("realName", realName)
                    .withIssuedAt(new Date())
                    .withExpiresAt(new Date(System.currentTimeMillis() + TICKET_EXPIRE))
                    .sign(Algorithm.HMAC256(secret));

            Map<String, Object> data = new HashMap<>();
            data.put("ticket", ticket);
            data.put("expireSeconds", TICKET_EXPIRE / 1000);
            if (sub.isRealRedirect()) {
                // 真实子系统：返回完整跳转地址，前端 window.location.href 跳出本站
                String sep = sub.getUrl().contains("?") ? "&" : "?";
                data.put("mode", "redirect");
                if (sub.isWithTicket()) {
                    data.put("redirectUrl", sub.getUrl() + sep + "ticket=" + URLEncoder.encode(ticket, "UTF-8"));
                } else {
                    // 纯外链跳转：子系统自带的登录页，门户不附带 SSO 票据
                    data.put("redirectUrl", sub.getUrl());
                }
            } else {
                // 模拟子系统：返回前端路由，前端 router.push
                data.put("mode", "mock");
                data.put("path", sub.getPath());
            }
            return ApiResult.ok(data);
        } catch (Exception e) {
            return ApiResult.failed("Token 无效或已过期");
        }
    }

    /**
     * 子系统验证票据（方式B：供未持有共享密钥的子系统后端回调，或本工程模拟子系统 B/C 使用）
     * 真实对接（方式A）的子系统（如 HerTeamWork/her-subsystem）不回调此接口，而是本地验签。
     */
    @GetMapping("/validate-ticket")
    public ApiResult<Map<String, Object>> validateTicket(
            @RequestParam("ticket") String ticket,
            @RequestParam(value = "appCode", required = false) String appCode) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(ticket);
            if (!"SSO".equals(jwt.getClaim("ticketType").asString())) {
                return ApiResult.failed("票据类型错误");
            }
            if (appCode != null && !appCode.isEmpty() && !appCode.equals(jwt.getClaim("targetApp").asString())) {
                return ApiResult.failed("票据目标应用不匹配");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("userId", jwt.getClaim("userId").asLong());
            data.put("username", jwt.getClaim("username").asString());
            String realName = jwt.getClaim("realName").isNull() ? null : jwt.getClaim("realName").asString();
            data.put("realName", realName == null ? jwt.getClaim("username").asString() : realName);
            data.put("targetApp", jwt.getClaim("targetApp").asString());
            data.put("authType", "SSO");
            return ApiResult.ok(data);
        } catch (Exception e) {
            return ApiResult.failed("票据无效或已过期");
        }
    }

    /**
     * 子系统配置列表（从 application.yml 的 portal.subsystems 读取，不再硬编码）
     */
    @GetMapping("/subsystems")
    public ApiResult<List<Map<String, Object>>> subsystems() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PortalProperties.Subsystem s : portalProperties.getSubsystems()) {
            Map<String, Object> m = new HashMap<>();
            m.put("code", s.getCode());
            m.put("name", s.getName());
            m.put("desc", s.getDesc());
            m.put("remark", s.getRemark());
            m.put("color", s.getColor());
            m.put("icon", s.getIcon());
            // 前端据此判断跳转方式：real=window.location.href 跳真实地址；mock=router.push 前端路由
            m.put("mode", s.isRealRedirect() ? "redirect" : "mock");
            if (s.isRealRedirect()) {
                m.put("url", s.getUrl());
            } else {
                m.put("path", s.getPath());
            }
            list.add(m);
        }
        return ApiResult.ok(list);
    }

    private PortalProperties.Subsystem findByCode(String code) {
        for (PortalProperties.Subsystem s : portalProperties.getSubsystems()) {
            if (s.getCode() != null && s.getCode().equals(code)) {
                return s;
            }
        }
        return null;
    }

    private String resolveToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new IllegalArgumentException("未提供有效 Token");
        }
        return auth.substring(7);
    }
}
