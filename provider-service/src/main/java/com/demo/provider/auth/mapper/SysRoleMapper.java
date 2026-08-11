package com.demo.provider.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.provider.auth.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM [dbo].[sys_role] r " +
            "INNER JOIN [dbo].[sys_user_role] ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysRole> findRolesByUserId(@Param("userId") Long userId);
}
