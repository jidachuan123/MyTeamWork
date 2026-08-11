package com.demo.consumer.auth.feign;

import com.demo.consumer.auth.vo.UserInfoVO;
import com.demo.consumer.utils.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "provider-service", contextId = "authFeignClient")
public interface AuthFeignClient {

    /**
     * 根据用户名查询用户信息（含密码哈希）
     */
    @GetMapping("/provider/auth/user")
    ApiResult<UserInfoVO> getUserByUsername(@RequestParam("username") String username);

    /**
     * 根据 userId 查询权限编码列表
     */
    @GetMapping("/provider/auth/permissions")
    ApiResult<List<String>> getPermissionsByUserId(@RequestParam("userId") Long userId);
}
