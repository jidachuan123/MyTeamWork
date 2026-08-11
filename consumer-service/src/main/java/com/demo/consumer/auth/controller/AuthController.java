package com.demo.consumer.auth.controller;

import com.demo.consumer.auth.feign.AuthFeignClient;
import com.demo.consumer.auth.jwt.JwtUtil;
import com.demo.consumer.auth.vo.UserInfoVO;
import com.demo.consumer.utils.ApiResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/consumer/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFeignClient authFeignClient;
    private final JwtUtil jwtUtil;

    /**
     * 登录：验证用户名密码，返回 JWT Token
     */
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return ApiResult.failed("用户名和密码不能为空");
        }

        // 调 Provider 查用户
        ApiResult<UserInfoVO> userResult = authFeignClient.getUserByUsername(req.getUsername());
        if (userResult == null || !userResult.isSuccess() || userResult.getResult() == null) {
            return ApiResult.failed("用户不存在");
        }

        UserInfoVO user = userResult.getResult();

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            return ApiResult.failed("账号已被禁用");
        }

        // Shiro MD5+salt 2次迭代验证密码
        String inputHash = new SimpleHash("MD5", req.getPassword(), user.getSalt(), 2).toHex();
        if (!inputHash.equals(user.getPassword())) {
            return ApiResult.failed("用户名或密码错误");
        }

        // 生成 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roles", user.getRoles());

        log.info("用户登录成功: {}", req.getUsername());
        return ApiResult.ok(data);
    }

    /**
     * 登出：前端清除 Token 即可（无状态）
     */
    @PostMapping("/logout")
    public ApiResult<Void> logout() {
        return ApiResult.ok(null);
    }

    /**
     * 获取当前用户信息（前端从 JWT 解析）
     */
    @GetMapping("/info")
    public ApiResult<Map<String, Object>> info(@RequestHeader("Authorization") String auth) {
        String token = auth.substring(7);
        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("username", username);

        return ApiResult.ok(data);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
