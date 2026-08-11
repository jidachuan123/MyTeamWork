package com.demo.provider.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("[dbo].[sys_permission]")
public class SysPermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("perm_name")
    private String permName;

    @TableField("perm_code")
    private String permCode;

    @TableField("perm_type")
    private Integer permType;

    @TableField("url")
    private String url;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("create_time")
    private Date createTime;
}
