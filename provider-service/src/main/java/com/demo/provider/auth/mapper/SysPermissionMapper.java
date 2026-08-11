package com.demo.provider.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.provider.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT p.* FROM [dbo].[sys_permission] p " +
            "INNER JOIN [dbo].[sys_role_permission] rp ON p.id = rp.permission_id " +
            "INNER JOIN [dbo].[sys_user_role] ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysPermission> findPermissionsByUserId(@Param("userId") Long userId);
}
