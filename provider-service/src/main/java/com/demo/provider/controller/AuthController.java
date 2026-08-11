package com.demo.provider.controller;

import com.demo.provider.auth.entity.SysPermission;
import com.demo.provider.auth.entity.SysRole;
import com.demo.provider.auth.entity.SysUser;
import com.demo.provider.auth.mapper.SysPermissionMapper;
import com.demo.provider.auth.mapper.SysRoleMapper;
import com.demo.provider.auth.mapper.SysUserMapper;
import com.demo.provider.utils.ApiResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/provider/auth")
public class AuthController {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private SysPermissionMapper sysPermissionMapper;

    /**
     * 应用启动时自动初始化 admin 用户（如果表为空）
     */
    @PostConstruct
    public void initData() {
        try {
            SysUser existing = sysUserMapper.findByUsername("admin");
            if (existing != null) {
                return;
            }
            // 生成 Shiro MD5+salt 密码
            String salt = "e3b0c44298fc1c14";
            String password = new SimpleHash("MD5", "123456", salt, 2).toHex();

            SysUser user = new SysUser();
            user.setUsername("admin");
            user.setPassword(password);
            user.setSalt(salt);
            user.setRealName("管理员");
            user.setStatus(1);
            sysUserMapper.insert(user);

            // 给 admin 分配 admin 角色（role_id=1）
            sysUserMapper.insertUserRoleRelation(user.getId(), 1L);
            log.info("初始化 admin 用户成功, userId={}", user.getId());
        } catch (Exception e) {
            log.warn("初始化 admin 用户跳过（表可能未建）: {}", e.getMessage());
        }
    }

    /**
     * 根据用户名查询用户信息（含密码哈希，供 Consumer 验证）
     */
    @GetMapping("/user")
    public ApiResult<UserInfoVO> getUser(@RequestParam("username") String username) {
        SysUser user = sysUserMapper.findByUsername(username);
        if (user == null) {
            return ApiResult.failed("用户不存在");
        }

        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setPassword(user.getPassword());
        vo.setSalt(user.getSalt());
        vo.setRealName(user.getRealName());
        vo.setStatus(user.getStatus());

        // 查角色编码列表
        List<SysRole> roles = sysRoleMapper.findRolesByUserId(user.getId());
        vo.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));

        return ApiResult.ok(vo);
    }

    /**
     * 根据 userId 查询权限编码列表（供 Consumer 授权用）
     */
    @GetMapping("/permissions")
    public ApiResult<List<String>> getPermissions(@RequestParam("userId") Long userId) {
        List<SysPermission> perms = sysPermissionMapper.findPermissionsByUserId(userId);
        List<String> codes = perms.stream().map(SysPermission::getPermCode).collect(Collectors.toList());
        return ApiResult.ok(codes);
    }

    /**
     * 返回给 Consumer 的用户信息 VO
     */
    @Data
    public static class UserInfoVO {
        private Long id;
        private String username;
        private String password;
        private String salt;
        private String realName;
        private Integer status;
        private List<String> roles;
    }
}
