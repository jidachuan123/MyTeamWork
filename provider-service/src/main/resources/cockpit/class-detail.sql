-- ============================================================
-- 集团驾驶舱 · 业态下钻汇总（按 tb_md_store_ex.c_class 圈定门店）
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
--
-- 参数：
--   ${tenantId}   租户号，固定 8
--   ${storeClass} 业态编码，即卡片 c_group_id（10 百货 / 21 超市 / 22 便利店 / 40 IP店）
--   ${date}       业务日期，默认今天的前一天（T+1 表）
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @c_class varchar(50) = '${storeClass}';
DECLARE @c_sale_da DATE = '${date}';
DECLARE @D DATE = @c_sale_da;

DECLARE @month_start DATE = DATEFROMPARTS(YEAR(@D), MONTH(@D), 1);
DECLARE @prev_month_start DATE = DATEFROMPARTS(YEAR(DATEADD(MONTH,-1,@D)), MONTH(DATEADD(MONTH,-1,@D)), 1);
DECLARE @year_month_start DATE = DATEFROMPARTS(YEAR(@D)-1, MONTH(@D), 1);
DECLARE @year_start DATE = DATEFROMPARTS(YEAR(@D), 1, 1);
DECLARE @prev_year_start DATE = DATEFROMPARTS(YEAR(@D)-1, 1, 1);

;WITH agg AS (
    SELECT
        MAX(ISNULL(d.c_name, @c_class)) AS c_class_name,
        COUNT(DISTINCT sx.c_id) AS c_store_count,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @D AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= DATEADD(DAY,-1,@D) AND ta.c_fact_da < @D THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_prev_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= DATEADD(DAY,-364,@D) AND ta.c_fact_da < DATEADD(DAY,-363,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @month_start AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @prev_month_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(MONTH,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_prev_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @year_month_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @year_start AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @prev_year_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS year_prev_sale
    FROM tbs_rd_store ta (NOLOCK)
    INNER JOIN tb_md_store_ex sx (NOLOCK) ON sx.c_tenant_id = ta.c_tenant_id AND sx.c_id = ta.c_store_id AND sx.c_class is not null AND sx.c_class <> ''
     JOIN tb_id_sys d ON d.c_class = 's_md_retail' AND d.c_id = @c_class AND d.c_status = '1'
    WHERE ta.c_tenant_id = @c_tenant_id
      AND sx.c_class = @c_class
      AND ta.c_fact_da >= @prev_year_start
      AND ta.c_fact_da < DATEADD(DAY,1,@D)
)
SELECT
    c_class_name,
    c_store_count,
    day_sale,
    month_sale,
    year_sale,
    (day_sale - day_prev_sale) / NULLIF(ABS(day_prev_sale), 0) AS day_chain_rate,
    (day_sale - day_year_sale) / NULLIF(ABS(day_year_sale), 0) AS day_year_rate,
    (month_sale - month_prev_sale) / NULLIF(ABS(month_prev_sale), 0) AS month_chain_rate,
    (month_sale - month_year_sale) / NULLIF(ABS(month_year_sale), 0) AS month_year_rate,
    (year_sale - year_prev_sale) / NULLIF(ABS(year_prev_sale), 0) AS year_year_rate
FROM agg;
