-- =============================================
-- Shiro 权限框架 DDL — RDS_SC 库
-- 连接: 192.168.0.66:1543, 数据库: RDS_SC, schema: dbo
-- =============================================

-- 1. 用户表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_user' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_user] (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        username    VARCHAR(50)  NOT NULL,
        password    VARCHAR(100) NOT NULL,
        salt        VARCHAR(50)  NOT NULL,
        nickname    VARCHAR(50)  NULL,
        status      TINYINT      DEFAULT 1,         -- 1=正常 0=禁用
        create_time DATETIME     DEFAULT GETDATE(),
        update_time DATETIME     DEFAULT GETDATE()
    );
    CREATE UNIQUE INDEX idx_sys_user_username ON [dbo].[sys_user](username);
END;
GO

-- 2. 角色表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_role' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_role] (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        role_code   VARCHAR(50)  NOT NULL,
        role_name   VARCHAR(50)  NOT NULL,
        remark      VARCHAR(200) NULL,
        create_time DATETIME     DEFAULT GETDATE()
    );
    CREATE UNIQUE INDEX idx_sys_role_code ON [dbo].[sys_role](role_code);
END;
GO

-- 3. 权限表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_permission' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_permission] (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        perm_code   VARCHAR(100) NOT NULL,
        perm_name   VARCHAR(50)  NOT NULL,
        parent_id   BIGINT       DEFAULT 0,
        sort_order  INT          DEFAULT 0,
        create_time DATETIME     DEFAULT GETDATE()
    );
    CREATE UNIQUE INDEX idx_sys_perm_code ON [dbo].[sys_permission](perm_code);
END;
GO

-- 4. 用户-角色关联表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_user_role' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_user_role] (
        id      BIGINT IDENTITY(1,1) PRIMARY KEY,
        user_id BIGINT NOT NULL,
        role_id BIGINT NOT NULL
    );
END;
GO

-- 5. 角色-权限关联表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_role_permission' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_role_permission] (
        id            BIGINT IDENTITY(1,1) PRIMARY KEY,
        role_id       BIGINT NOT NULL,
        permission_id BIGINT NOT NULL
    );
END;
GO

-- =============================================
-- 初始数据
-- =============================================

-- 角色
IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_role] WHERE role_code = 'admin')
    INSERT INTO [dbo].[sys_role] (role_code, role_name, remark) VALUES ('admin', '管理员', '拥有所有权限');

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_role] WHERE role_code = 'operator')
    INSERT INTO [dbo].[sys_role] (role_code, role_name, remark) VALUES ('operator', '操作员', '基础操作权限');

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_role] WHERE role_code = 'viewer')
    INSERT INTO [dbo].[sys_role] (role_code, role_name, remark) VALUES ('viewer', '只读用户', '只能查看不能操作');

-- 权限
IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_permission] WHERE perm_code = 'user:list')
    INSERT INTO [dbo].[sys_permission] (perm_code, perm_name, sort_order) VALUES ('user:list', '商品列表查询', 1);

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_permission] WHERE perm_code = 'user:detail')
    INSERT INTO [dbo].[sys_permission] (perm_code, perm_name, sort_order) VALUES ('user:detail', '商品详情查询', 2);

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_permission] WHERE perm_code = 'report:view')
    INSERT INTO [dbo].[sys_permission] (perm_code, perm_name, sort_order) VALUES ('report:view', '盘点报表查看', 3);

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_permission] WHERE perm_code = 'external:access')
    INSERT INTO [dbo].[sys_permission] (perm_code, perm_name, sort_order) VALUES ('external:access', '外部对接访问', 4);

-- 角色-权限关联（admin 拥有所有权限）
DECLARE @admin_role_id BIGINT = (SELECT id FROM [dbo].[sys_role] WHERE role_code = 'admin');

INSERT INTO [dbo].[sys_role_permission] (role_id, permission_id)
SELECT @admin_role_id, id FROM [dbo].[sys_permission]
WHERE NOT EXISTS (
    SELECT 1 FROM [dbo].[sys_role_permission] rp WHERE rp.role_id = @admin_role_id AND rp.permission_id = [dbo].[sys_permission].id
);

-- operator 拥有 user:list, user:detail, report:view
DECLARE @operator_role_id BIGINT = (SELECT id FROM [dbo].[sys_role] WHERE role_code = 'operator');

INSERT INTO [dbo].[sys_role_permission] (role_id, permission_id)
SELECT @operator_role_id, id FROM [dbo].[sys_permission] WHERE perm_code IN ('user:list', 'user:detail', 'report:view')
AND NOT EXISTS (
    SELECT 1 FROM [dbo].[sys_role_permission] rp WHERE rp.role_id = @operator_role_id AND rp.permission_id = [dbo].[sys_permission].id
);

-- viewer 仅有 user:list
DECLARE @viewer_role_id BIGINT = (SELECT id FROM [dbo].[sys_role] WHERE role_code = 'viewer');

INSERT INTO [dbo].[sys_role_permission] (role_id, permission_id)
SELECT @viewer_role_id, id FROM [dbo].[sys_permission] WHERE perm_code = 'user:list'
AND NOT EXISTS (
    SELECT 1 FROM [dbo].[sys_role_permission] rp WHERE rp.role_id = @viewer_role_id AND rp.permission_id = [dbo].[sys_permission].id
);

-- admin 用户初始数据（密码: 123456，Provider 启动时自动初始化）
-- 如果 Provider 启动后 auth 接口返回 admin 用户数据为空，手动执行:
-- INSERT INTO [dbo].[sys_user] (username, password, salt, nickname, status)
-- VALUES ('admin', '待Provider计算的hash', '待Provider计算的salt', '管理员', 1);
-- 然后查 sys_role 的 admin role_id，插入 sys_user_role 关联即可
-- 
-- 简单方案：直接等 Provider 启动后调用一次 login 接口，Provider 会自动插入 admin 用户
GO

PRINT '=== Shiro DDL 执行完成 ===';
PRINT '表: sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission';
PRINT '初始角色: admin / operator / viewer';
PRINT 'admin用户由Provider启动时自动创建（密码: 123456）';
