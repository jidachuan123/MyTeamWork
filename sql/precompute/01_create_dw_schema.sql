/* =====================================================================
 * 销售报表1 预计算表 建库脚本（RDS_SC · dw schema）
 * 配套设计文档：C:\work\销售报表1预计算表设计方案.md
 *
 * 🔴 安全红线：
 *   - 本脚本只创建 dw. 前缀的新对象（schema / 表 / 索引 / 备注）
 *   - 不 DROP / ALTER / TRUNCATE / UPDATE / DELETE 任何现有生产表（dbo、dm、mem、tb_* 等）
 *   - 幂等：可重复执行（IF NOT EXISTS 判断）
 *
 * 执行方式：用 SSMS 连接 RDS_SC，选中本文件执行即可。
 * ===================================================================== */

-- =====================================================================
-- 1. 创建 dw schema（不存在才建）
-- =====================================================================
IF SCHEMA_ID('dw') IS NULL
    EXEC('CREATE SCHEMA dw AUTHORIZATION dbo');
GO

-- =====================================================================
-- 2. 预计算结果表：dw.rpt_sale_detail_precompute（211 列）
-- =====================================================================
IF OBJECT_ID('dw.rpt_sale_detail_precompute', 'U') IS NULL
BEGIN
    CREATE TABLE dw.rpt_sale_detail_precompute (
        -- ===== A. ETL元数据（10列） =====
        id                    BIGINT IDENTITY(1,1) NOT NULL,   -- 主键自增
        batch_id              VARCHAR(50)  NULL,              -- 批次号（如 20260818_MOM）
        etl_time              DATETIME     NULL,              -- ETL执行时间
        query_date            DATE         NULL,              -- 查询日期（业务日期=前一天）
        comparison_type       VARCHAR(10)  NULL,              -- 对比类型：MOM=环比 / YOY=同比
        cmp_start_date        DATE         NULL,              -- 对比开始日期
        cmp_end_date          DATE         NULL,              -- 对比结束日期
        report_type           VARCHAR(30)  NULL,              -- 报表类型（SALE_DETAIL_1，预留多报表）
        org_code              VARCHAR(20)  NULL,              -- 机构编码
        dept_levels           INT          NULL,              -- 档位编码：3=明细 / 2=部门合计 / 1=机构汇总(超市总计)

        -- ===== B. 查询参数（6列） =====
        tenant_id             VARCHAR(100) NULL,              -- 租户ID
        date_type             VARCHAR(20)  NULL,              -- 日期类型（记账日期/发生日期）
        lang                  VARCHAR(10)  NULL,              -- 语言（L/E）
        show_store            VARCHAR(20)  NULL,              -- 是否显示门店
        show_brand            VARCHAR(20)  NULL,              -- 是否显示品牌
        show_date             VARCHAR(20)  NULL,              -- 是否显示日期

        -- ===== C. 维度列（41列） =====
        fs_date               DATE         NULL,              -- 发生日期
        hs_date               DATE         NULL,              -- 记账日期
        store_id              VARCHAR(200) NULL,              -- 门店/机构编码
        store_name            VARCHAR(200) NULL,              -- 门店/机构名称
        adno                  VARCHAR(200) NULL,              -- 部门编码（行政编码）
        dept_id1              VARCHAR(200) NULL,              -- 一级部门编码
        dept_id2              VARCHAR(200) NULL,              -- 二级部门编码
        dept_id3              VARCHAR(200) NULL,              -- 三级部门编码
        dept_id4              VARCHAR(200) NULL,              -- 四级部门编码
        dept_id5              VARCHAR(200) NULL,              -- 五级部门编码
        dept_id6              VARCHAR(200) NULL,              -- 六级部门编码
        dept_id7              VARCHAR(200) NULL,              -- 七级部门编码
        dept_id8              VARCHAR(200) NULL,              -- 八级部门编码
        dept_name1            VARCHAR(200) NULL,              -- 一级部门名称
        dept_name2            VARCHAR(200) NULL,              -- 二级部门名称
        dept_name3            VARCHAR(200) NULL,              -- 三级部门名称
        dept_name4            VARCHAR(200) NULL,              -- 四级部门名称
        dept_name5            VARCHAR(200) NULL,              -- 五级部门名称
        dept_name6            VARCHAR(200) NULL,              -- 六级部门名称
        dept_name7            VARCHAR(200) NULL,              -- 七级部门名称
        dept_name8            VARCHAR(200) NULL,              -- 八级部门名称
        depart_name           VARCHAR(200) NULL,              -- 部门名称（最末级）
        ccode                 VARCHAR(200) NULL,              -- 品类编码（末级）
        cat_id1               VARCHAR(200) NULL,              -- 一级品类编码
        cat_id2               VARCHAR(200) NULL,              -- 二级品类编码
        cat_id3               VARCHAR(200) NULL,              -- 三级品类编码
        cat_id4               VARCHAR(200) NULL,              -- 四级品类编码
        cat_id5               VARCHAR(200) NULL,              -- 五级品类编码
        cat_id6               VARCHAR(200) NULL,              -- 六级品类编码
        cat_id7               VARCHAR(200) NULL,              -- 七级品类编码
        cat_id8               VARCHAR(200) NULL,              -- 八级品类编码
        cat_name1             VARCHAR(200) NULL,              -- 一级品类名称
        cat_name2             VARCHAR(200) NULL,              -- 二级品类名称
        cat_name3             VARCHAR(200) NULL,              -- 三级品类名称
        cat_name4             VARCHAR(200) NULL,              -- 四级品类名称
        cat_name5             VARCHAR(200) NULL,              -- 五级品类名称
        cat_name6             VARCHAR(200) NULL,              -- 六级品类名称
        cat_name7             VARCHAR(200) NULL,              -- 七级品类名称
        cat_name8             VARCHAR(200) NULL,              -- 八级品类名称
        cat_name              VARCHAR(200) NULL,              -- 品类名称（末级）
        gds_bcode             VARCHAR(63)  NULL,              -- 商品/品牌编码

        -- ===== D. 本期原始指标（24列） =====
        sale_qty              NUMERIC(14,4) NULL,             -- 销售数量
        sale_qty_fpl          NUMERIC(14,4) NULL,             -- 生鲜盘点损溢数量
        sale_revenue          NUMERIC(14,4) NULL,             -- 含税销售额
        wsale_revenue         NUMERIC(14,4) NULL,             -- 批发销售金额
        net_sale_revenue      NUMERIC(14,4) NULL,             -- 不含税销售额
        at_cost               NUMERIC(14,4) NULL,             -- 含税成本
        aet_cost              NUMERIC(14,4) NULL,             -- 不含税成本
        disc_value            NUMERIC(14,4) NULL,             -- 总折扣额（让利）
        at_disc               NUMERIC(14,4) NULL,             -- 供应商折扣承担金额
        profit                NUMERIC(14,4) NULL,             -- 含税毛利 = 销售额-折扣-含税成本+供应商折扣
        wsale_profit          NUMERIC(14,4) NULL,             -- 批发销售毛利
        net_profit            NUMERIC(14,4) NULL,             -- 不含税毛利
        sale_mem              NUMERIC(14,4) NULL,             -- 含税会员销售额
        net_sale_mem          NUMERIC(14,4) NULL,             -- 不含税会员销售额
        sale_mem_rate_raw     NUMERIC(14,4) NULL,             -- 会员销售占比（引擎原始值，未ROUND）
        sale_revenue_pro      NUMERIC(14,4) NULL,             -- 促销销售额
        net_sale_revenue_pro  NUMERIC(14,4) NULL,             -- 不含税促销销售额
        at_cost_pro           NUMERIC(14,4) NULL,             -- 含税促销成本
        aet_cost_pro          NUMERIC(14,4) NULL,             -- 不含税促销成本
        profit_pro            NUMERIC(14,4) NULL,             -- 促销毛利
        net_profit_pro        NUMERIC(14,4) NULL,             -- 不含税促销毛利
        sale_revenue_pro_rate_raw NUMERIC(14,4) NULL,         -- 促销销售占比（引擎原始值，未ROUND）
        profit_pro_rate_raw   NUMERIC(14,4) NULL,             -- 促销毛利占比（引擎原始值，未ROUND）
        pay_free              NUMERIC(14,2) NULL,             -- 支付优惠金额（用于算销售净额/净毛利）

        -- ===== E. 对期原始指标（24列） =====
        sale_revenue_dq       NUMERIC(14,4) NULL,             -- 对期含税销售额
        wsale_revenue_dq      NUMERIC(14,4) NULL,             -- 对期批发销售金额
        net_sale_revenue_dq   NUMERIC(14,4) NULL,             -- 对期不含税销售额
        at_cost_dq            NUMERIC(14,4) NULL,             -- 对期含税成本
        aet_cost_dq           NUMERIC(14,4) NULL,             -- 对期不含税成本
        disc_value_dq         NUMERIC(14,4) NULL,             -- 对期总折扣额
        at_disc_dq            NUMERIC(14,4) NULL,             -- 对期供应商折扣承担金额
        profit_dq             NUMERIC(14,4) NULL,             -- 对期含税毛利
        wsale_profit_dq       NUMERIC(14,4) NULL,             -- 对期批发销售毛利
        net_profit_dq         NUMERIC(14,4) NULL,             -- 对期不含税毛利
        sale_mem_dq           NUMERIC(14,4) NULL,             -- 对期含税会员销售额
        net_sale_mem_dq       NUMERIC(14,4) NULL,             -- 对期不含税会员销售额
        sale_mem_rate_dq_raw  NUMERIC(14,4) NULL,             -- 对期会员销售占比（引擎原始值，未ROUND）
        sale_revenue_pro_dq   NUMERIC(14,4) NULL,             -- 对期促销销售额
        net_sale_revenue_pro_dq NUMERIC(14,4) NULL,           -- 对期不含税促销销售额
        at_cost_pro_dq        NUMERIC(14,4) NULL,             -- 对期促销含税成本
        aet_cost_pro_dq       NUMERIC(14,4) NULL,             -- 对期促销不含税成本
        profit_pro_dq         NUMERIC(14,4) NULL,             -- 对期促销毛利
        net_profit_pro_dq     NUMERIC(14,4) NULL,             -- 对期不含税促销毛利
        sale_revenue_pro_rate_dq_raw NUMERIC(14,4) NULL,      -- 对期促销销售占比（引擎原始值，未ROUND）
        profit_pro_rate_dq_raw NUMERIC(14,4) NULL,            -- 对期促销毛利占比（引擎原始值，未ROUND）
        pay_free_dq           NUMERIC(14,2) NULL,             -- 对期支付优惠金额
        sale_qty_dq           NUMERIC(14,4) NULL,             -- 对期销售数量（预留，如引擎填充）

        -- ===== F. 增长率与排名（4列） =====
        sale_revenue_dbc      NUMERIC(14,4) NULL,             -- 销售额增长率（原始值，×100后显示）
        profit_dbc            NUMERIC(14,4) NULL,             -- 毛利额增长率（原始值，×100后显示）
        sale_revenue_pm       NUMERIC(14,4) NULL,             -- 销售额排名
        profit_pm             NUMERIC(14,4) NULL,             -- 毛利额排名

        -- ===== G. 交易指标（6列，来自 #TEMP_REP_TRADE） =====
        count_trade           NUMERIC(14,4) NULL,             -- 交易/收银笔数（来客数）
        count_trade_dq        NUMERIC(14,4) NULL,             -- 对期交易笔数
        count_mem             NUMERIC(14,4) NULL,             -- 会员交易笔数
        count_mem_dq          NUMERIC(14,4) NULL,             -- 对期会员交易笔数
        plan_sale             NUMERIC(14,4) NULL,             -- 销售计划额
        plan_profit           NUMERIC(14,4) NULL,             -- 毛利计划额

        -- ===== H. 累计(ACC)指标（50列） =====
        acc_sale_qty          NUMERIC(14,4) NULL,             -- 累计销售数量
        acc_sale_qty_fpl      NUMERIC(14,4) NULL,             -- 累计生鲜盘点损溢数量
        acc_sale_revenue      NUMERIC(14,4) NULL,             -- 累计含税销售额
        acc_wsale_revenue     NUMERIC(14,4) NULL,             -- 累计批发销售金额
        acc_net_sale_revenue  NUMERIC(14,4) NULL,             -- 累计不含税销售额
        acc_at_cost           NUMERIC(14,4) NULL,             -- 累计含税成本
        acc_aet_cost          NUMERIC(14,4) NULL,             -- 累计不含税成本
        acc_disc_value        NUMERIC(14,4) NULL,             -- 累计总折扣额
        acc_at_disc           NUMERIC(14,4) NULL,             -- 累计供应商折扣承担
        acc_profit            NUMERIC(14,4) NULL,             -- 累计含税毛利
        acc_wsale_profit      NUMERIC(14,4) NULL,             -- 累计批发销售毛利
        acc_net_profit        NUMERIC(14,4) NULL,             -- 累计不含税毛利
        acc_sale_mem          NUMERIC(14,4) NULL,             -- 累计含税会员销售
        acc_net_sale_mem      NUMERIC(14,4) NULL,             -- 累计不含税会员销售
        acc_sale_mem_rate     NUMERIC(14,4) NULL,             -- 累计会员销售占比
        acc_sale_revenue_pro  NUMERIC(14,4) NULL,             -- 累计促销销售
        acc_net_sale_revenue_pro NUMERIC(14,4) NULL,          -- 累计不含税促销销售
        acc_at_cost_pro       NUMERIC(14,4) NULL,             -- 累计促销含税成本
        acc_aet_cost_pro      NUMERIC(14,4) NULL,             -- 累计促销不含税成本
        acc_profit_pro        NUMERIC(14,4) NULL,             -- 累计促销毛利
        acc_net_profit_pro    NUMERIC(14,4) NULL,             -- 累计不含税促销毛利
        acc_sale_revenue_pro_rate NUMERIC(14,4) NULL,         -- 累计促销销售占比
        acc_profit_pro_rate   NUMERIC(14,4) NULL,             -- 累计促销毛利占比
        acc_pay_free          NUMERIC(14,2) NULL,             -- 累计支付优惠金额
        acc_sale_revenue_dbc  NUMERIC(14,4) NULL,             -- 累计销售额增长率
        acc_sale_revenue_dq   NUMERIC(14,4) NULL,             -- 累计对期含税销售额
        acc_wsale_revenue_dq  NUMERIC(14,4) NULL,             -- 累计对期批发销售金额
        acc_net_sale_revenue_dq NUMERIC(14,4) NULL,           -- 累计对期不含税销售额
        acc_at_cost_dq        NUMERIC(14,4) NULL,             -- 累计对期含税成本
        acc_aet_cost_dq       NUMERIC(14,4) NULL,             -- 累计对期不含税成本
        acc_disc_value_dq     NUMERIC(14,4) NULL,             -- 累计对期总折扣额
        acc_at_disc_dq        NUMERIC(14,4) NULL,             -- 累计对期供应商折扣
        acc_profit_dq         NUMERIC(14,4) NULL,             -- 累计对期含税毛利
        acc_wsale_profit_dq   NUMERIC(14,4) NULL,             -- 累计对期批发销售毛利
        acc_net_profit_dq     NUMERIC(14,4) NULL,             -- 累计对期不含税毛利
        acc_sale_mem_dq       NUMERIC(14,4) NULL,             -- 累计对期含税会员销售
        acc_net_sale_mem_dq   NUMERIC(14,4) NULL,             -- 累计对期不含税会员销售
        acc_sale_mem_rate_dq  NUMERIC(14,4) NULL,             -- 累计对期会员销售占比
        acc_sale_revenue_pro_dq NUMERIC(14,4) NULL,           -- 累计对期促销销售
        acc_net_sale_revenue_pro_dq NUMERIC(14,4) NULL,       -- 累计对期不含税促销销售
        acc_at_cost_pro_dq    NUMERIC(14,4) NULL,             -- 累计对期促销含税成本
        acc_aet_cost_pro_dq   NUMERIC(14,4) NULL,             -- 累计对期促销不含税成本
        acc_profit_pro_dq     NUMERIC(14,4) NULL,             -- 累计对期促销毛利
        acc_net_profit_pro_dq NUMERIC(14,4) NULL,             -- 累计对期不含税促销毛利
        acc_sale_revenue_pro_rate_dq NUMERIC(14,4) NULL,      -- 累计对期促销销售占比
        acc_profit_pro_rate_dq NUMERIC(14,4) NULL,            -- 累计对期促销毛利占比
        acc_pay_free_dq       NUMERIC(14,2) NULL,             -- 累计对期支付优惠金额
        acc_profit_dbc        NUMERIC(14,4) NULL,             -- 累计毛利额增长率
        acc_sale_revenue_pm   NUMERIC(14,4) NULL,             -- 累计销售额排名
        acc_profit_pm         NUMERIC(14,4) NULL,             -- 累计毛利额排名

        -- ===== I. 计算显示列（33列） =====
        profit_rate           NUMERIC(14,4) NULL,             -- 毛利率% = ROUND(profit/sale_revenue,4)*100
        net_sale_amount       NUMERIC(14,4) NULL,             -- 销售净额 = sale_revenue - pay_free
        net_profit_amount     NUMERIC(14,4) NULL,             -- 净毛利额 = profit - pay_free
        net_profit_rate       NUMERIC(14,4) NULL,             -- 净毛利率%
        wsale_revenue_rate    NUMERIC(14,4) NULL,             -- 批发销售占比%
        wsale_profit_rate     NUMERIC(14,4) NULL,             -- 批发毛利占比%
        sale_mem_rate         NUMERIC(14,4) NULL,             -- 会员销售占比%
        sale_revenue_pro_rate NUMERIC(14,4) NULL,             -- 促销销售占比%
        profit_pro_rate       NUMERIC(14,4) NULL,             -- 促销毛利占比%
        cust_price            NUMERIC(14,2) NULL,             -- 客单价
        mem_cust_price        NUMERIC(14,2) NULL,             -- 会员客单价
        mem_trade_rate        NUMERIC(14,4) NULL,             -- 会员交易占比%
        plan_sale_rate        NUMERIC(14,4) NULL,             -- 销售计划达成率%
        plan_profit_rate      NUMERIC(14,4) NULL,             -- 毛利计划达成率%
        sale_revenue_growth   NUMERIC(14,4) NULL,             -- 销售额增长率% = DBC*100
        profit_growth         NUMERIC(14,4) NULL,             -- 毛利额增长率% = DBC*100
        brand_name            NVARCHAR(200) NULL,             -- 品牌名称
        dq_net_sale_amount    NUMERIC(14,4) NULL,             -- 对期销售净额
        dq_net_profit_amount  NUMERIC(14,4) NULL,             -- 对期净毛利额
        dq_wsale_revenue_rate NUMERIC(14,4) NULL,             -- 对期批发销售占比%
        dq_wsale_profit_rate  NUMERIC(14,4) NULL,             -- 对期批发毛利占比%
        dq_sale_mem_rate      NUMERIC(14,4) NULL,             -- 对期会员销售占比%
        dq_sale_revenue_pro_rate NUMERIC(14,4) NULL,          -- 对期促销销售占比%
        dq_profit_pro_rate    NUMERIC(14,4) NULL,             -- 对期促销毛利占比%
        dq_profit_rate        NUMERIC(14,4) NULL,             -- 对期毛利率%
        dq_net_profit_rate    NUMERIC(14,4) NULL,             -- 对期净毛利率%
        dq_cust_price         NUMERIC(14,2) NULL,             -- 对期客单价
        dq_mem_cust_price     NUMERIC(14,2) NULL,             -- 对期会员客单价
        dq_mem_trade_rate     NUMERIC(14,4) NULL,             -- 对期会员交易占比%
        profit_rate_growth    NUMERIC(14,4) NULL,             -- 毛利率对比增长(百分点)
        net_sale_growth       NUMERIC(14,4) NULL,             -- 销售净额对比增长%
        net_profit_growth     NUMERIC(14,4) NULL,             -- 净毛利额对比增长%
        net_profit_rate_growth NUMERIC(14,4) NULL,            -- 净毛利率对比增长(百分点)

        -- ===== J. 外部数据（1列） =====
        stock_amount          NUMERIC(14,4) NULL,             -- 当日库存金额（剔除特定门店）

        -- ===== K. 预留扩展列（10列） =====
        ext_01                NUMERIC(14,4) NULL,             -- 预留扩展1
        ext_02                NUMERIC(14,4) NULL,             -- 预留扩展2
        ext_03                NUMERIC(14,4) NULL,             -- 预留扩展3
        ext_04                NVARCHAR(500) NULL,             -- 预留扩展4（文本）
        ext_05                NVARCHAR(500) NULL,             -- 预留扩展5（文本）
        ext_06                NVARCHAR(200) NULL,             -- 预留扩展6（文本）
        ext_07                NVARCHAR(200) NULL,             -- 预留扩展7（文本）
        ext_08                DATE         NULL,              -- 预留扩展8（日期）
        ext_09                VARCHAR(50)  NULL,              -- 预留扩展9（编码）
        ext_10                VARCHAR(50)  NULL,              -- 预留扩展10（编码）

        CONSTRAINT PK_rpt_sale_detail_precompute PRIMARY KEY (id)
    );
END
GO

-- =====================================================================
-- 3. 索引
-- =====================================================================
-- 唯一索引：防止同日同类型重复写入（仅对 deptLevels=3 的数据唯一）
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_rpt_sale_precompute' AND object_id = OBJECT_ID('dw.rpt_sale_detail_precompute'))
    CREATE UNIQUE INDEX UX_rpt_sale_precompute
    ON dw.rpt_sale_detail_precompute (
        query_date, comparison_type, org_code, report_type,
        store_id, dept_id1, dept_id2, dept_id3, dept_id4, dept_id5, dept_id6, dept_id7, dept_id8,
        ccode, cat_id1, cat_id2, cat_id3, cat_id4, cat_id5, cat_id6, cat_id7, cat_id8, gds_bcode
    )
    WHERE dept_id3 IS NOT NULL;
GO

-- 查询索引：前端最常用查询条件
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_rpt_sale_date_type_org' AND object_id = OBJECT_ID('dw.rpt_sale_detail_precompute'))
    CREATE INDEX IX_rpt_sale_date_type_org
    ON dw.rpt_sale_detail_precompute (query_date, comparison_type, org_code, report_type);
GO

-- =====================================================================
-- 4. 跑批日志表：dw.etl_batch_log
-- =====================================================================
IF OBJECT_ID('dw.etl_batch_log', 'U') IS NULL
BEGIN
    CREATE TABLE dw.etl_batch_log (
        id              BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        batch_id        VARCHAR(50)  NULL,   -- 批次号
        etl_time        DATETIME     NULL,   -- 执行时间
        query_date      DATE         NULL,   -- 业务日期
        comparison_type VARCHAR(10)  NULL,   -- MOM / YOY
        org_code        VARCHAR(20)  NULL,   -- 机构编码
        report_type     VARCHAR(30)  NULL,   -- 报表类型
        row_count       INT          NULL,   -- 写入行数
        status          VARCHAR(20)  NULL,   -- SUCCESS / FAILED / SKIPPED
        message         NVARCHAR(500) NULL,  -- 说明/错误信息
        trigger_type    VARCHAR(20)  NULL    -- SCHEDULE=定时 / MANUAL=手动
    );
    CREATE INDEX IX_etl_batch_log_date ON dw.etl_batch_log (query_date, report_type);
END
GO

-- =====================================================================
-- 5. 字段字典表：dw.rpt_column_dict（未来加报表/加字段先查这里）
-- =====================================================================
IF OBJECT_ID('dw.rpt_column_dict', 'U') IS NULL
BEGIN
    CREATE TABLE dw.rpt_column_dict (
        id             INT IDENTITY(1,1) PRIMARY KEY,
        table_name     VARCHAR(100)  NULL,   -- 表名
        column_name    VARCHAR(100)  NULL,   -- 列名
        chinese_name   NVARCHAR(200) NULL,   -- 中文含义
        source_table   VARCHAR(100)  NULL,   -- 来源表/临时表
        source_column  VARCHAR(100)  NULL,   -- 来源列
        calc_formula   NVARCHAR(500) NULL,   -- 计算公式（如适用）
        data_type      VARCHAR(50)   NULL,   -- 数据类型
        remark         NVARCHAR(500) NULL    -- 备注
    );
END
GO
