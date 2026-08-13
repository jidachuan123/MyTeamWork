-- =============================================
-- 门户系统 SSO 可选建表脚本 — RDS_SC 库
-- 连接: 192.168.0.66:1543, 数据库: RDS_SC, schema: dbo
--
-- 注意：
--  1. 当前后端 SSO 实现为"无状态 JWT 票据"，不需要建表即可运行。
--  2. 以下两张表用于【真实生产部署】场景：
--     - sys_app      子系统注册表（真实跳转时配置各子系统地址）
--     - sso_ticket   SSO 一次性票据记录表（如需强制票据一次性使用/审计留痕）
--  3. 请先审阅，确认需要后由你手动执行。
-- =============================================

-- 1. 子系统注册表
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sys_app' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sys_app] (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        app_code    VARCHAR(50)  NOT NULL,              -- 子系统编码: A / B / C
        app_name    VARCHAR(100) NOT NULL,              -- 子系统名称
        app_url     VARCHAR(500) NOT NULL,              -- 子系统真实地址（SSO 入口）
        icon        VARCHAR(50)  NULL,                  -- 图标标识
        sort_order  INT          DEFAULT 0,             -- 排序
        status      TINYINT      DEFAULT 1,             -- 1=启用 0=停用
        remark      VARCHAR(200) NULL,
        create_time DATETIME     DEFAULT GETDATE()
    );
    CREATE UNIQUE INDEX idx_sys_app_code ON [dbo].[sys_app](app_code);
END;
GO

-- 2. SSO 一次性票据表（审计留痕用）
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='sso_ticket' AND xtype='U')
BEGIN
    CREATE TABLE [dbo].[sso_ticket] (
        id          BIGINT IDENTITY(1,1) PRIMARY KEY,
        ticket      VARCHAR(500) NOT NULL,              -- 票据内容（JWT）
        user_id     BIGINT       NOT NULL,
        username    VARCHAR(50)  NOT NULL,
        app_code    VARCHAR(50)  NULL,                  -- 目标子系统，NULL=门户首页
        status      TINYINT      DEFAULT 0,             -- 0=未使用 1=已使用 2=已过期
        expire_time DATETIME     NOT NULL,              -- 过期时间
        create_time DATETIME     DEFAULT GETDATE(),
        use_time    DATETIME     NULL
    );
    CREATE INDEX idx_sso_ticket_code ON [dbo].[sso_ticket](ticket);
END;
GO

-- =============================================
-- 初始子系统数据（A/B/C 模拟系统）
-- =============================================
IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_app] WHERE app_code = 'A')
    INSERT INTO [dbo].[sys_app] (app_code, app_name, app_url, icon, sort_order, status, remark)
    VALUES ('A', '子系统A-报表系统',   'http://localhost:8081/a/sso/login',  'el-icon-data-analysis', 1, 1, '模拟子系统A');
GO

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_app] WHERE app_code = 'B')
    INSERT INTO [dbo].[sys_app] (app_code, app_name, app_url, icon, sort_order, status, remark)
    VALUES ('B', '子系统B-商品系统',   'http://localhost:8082/b/sso/login',  'el-icon-goods',         2, 1, '模拟子系统B');
GO

IF NOT EXISTS (SELECT 1 FROM [dbo].[sys_app] WHERE app_code = 'C')
    INSERT INTO [dbo].[sys_app] (app_code, app_name, app_url, icon, sort_order, status, remark)
    VALUES ('C', '子系统C-对接系统',   'http://localhost:8083/c/sso/login',  'el-icon-connection',    3, 1, '模拟子系统C');
GO

PRINT '=== 门户 SSO 可选表创建完成 ===';
PRINT '表: sys_app（子系统注册表）, sso_ticket（SSO票据审计表）';
PRINT '说明: 当前无状态 JWT 实现不依赖以上表；真实部署时按需启用。';
