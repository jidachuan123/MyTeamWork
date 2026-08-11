package com.demo.provider.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.provider.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM [dbo].[sys_user] WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);

    @org.apache.ibatis.annotations.Insert("INSERT INTO [dbo].[sys_user_role] (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRoleRelation(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
