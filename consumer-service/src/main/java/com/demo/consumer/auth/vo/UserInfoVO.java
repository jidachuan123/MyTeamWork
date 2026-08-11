package com.demo.consumer.auth.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Provider 端返回的用户信息 VO（Feign 反序列化用）
 */
@Data
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String password;
    private String salt;
    private String realName;
    private Integer status;
    private List<String> roles;
}
