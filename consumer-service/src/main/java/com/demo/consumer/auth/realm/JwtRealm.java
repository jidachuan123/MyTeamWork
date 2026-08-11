package com.demo.consumer.auth.realm;

import com.demo.consumer.auth.feign.AuthFeignClient;
import com.demo.consumer.auth.jwt.JwtToken;
import com.demo.consumer.auth.jwt.JwtUtil;
import com.demo.consumer.auth.vo.UserInfoVO;
import com.demo.consumer.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class JwtRealm extends AuthorizingRealm {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthFeignClient authFeignClient;

    /**
     * 只支持 JwtToken
     */
    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    /**
     * 授权：从 JWT 中取 userId，调 Provider 获取权限列表
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String token = (String) principals.getPrimaryPrincipal();
        Long userId = jwtUtil.getUserId(token);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();

        // 调用 Provider 获取用户信息（含角色）
        ApiResult<UserInfoVO> userResult = authFeignClient.getUserByUsername(jwtUtil.getUsername(token));
        if (userResult != null && userResult.isSuccess() && userResult.getResult() != null) {
            Set<String> roles = new HashSet<>(userResult.getResult().getRoles());
            info.setRoles(roles);
        }

        // 调用 Provider 获取权限编码列表
        ApiResult<List<String>> permResult = authFeignClient.getPermissionsByUserId(userId);
        if (permResult != null && permResult.isSuccess() && permResult.getResult() != null) {
            info.addStringPermissions(permResult.getResult());
        }

        return info;
    }

    /**
     * 认证：验证 JWT 是否有效
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        String token = (String) authenticationToken.getPrincipal();

        try {
            // 验证 JWT 签名和过期时间
            jwtUtil.verify(token);

            // 额外检查用户状态
            String username = jwtUtil.getUsername(token);
            ApiResult<UserInfoVO> userResult = authFeignClient.getUserByUsername(username);
            if (userResult == null || !userResult.isSuccess() || userResult.getResult() == null) {
                throw new AuthenticationException("用户不存在或已被禁用");
            }

            return new SimpleAuthenticationInfo(token, token, getName());
        } catch (Exception e) {
            log.debug("JWT 认证失败: {}", e.getMessage());
            throw new AuthenticationException("Token 无效或已过期", e);
        }
    }
}
