-- ============================================================
-- 集团驾驶舱 · 昨日经营总览
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
--
-- 参数（由 CockpitService 替换）：
--   ${tenantId}  租户号，固定 8
--   ${date}      业务日期，业务侧固定传「今天的前一天」（tbs_rd_store 为 T+1 汇总表）
--
-- 输出两类行：
--   c_group_type = 1  购物中心（按门店展开）
--   c_group_type = 2  业态（百货 / 超市 / 便利店 / IP店…）
--
-- 说明：原 SQL 只输出 day_sale/month_sale/year_sale 及比率；
--       这里额外把 *_prev/*_year 原始列一并带出，供 Java 端汇总集团合计的环比/同比。
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @D DATE = '${date}';
DECLARE @month_start DATE = DATEFROMPARTS(YEAR(@D), MONTH(@D), 1);
DECLARE @prev_month_start DATE = DATEFROMPARTS(YEAR(DATEADD(MONTH,-1,@D)), MONTH(DATEADD(MONTH,-1,@D)), 1);
DECLARE @year_month_start DATE = DATEFROMPARTS(YEAR(@D)-1, MONTH(@D), 1);
DECLARE @year_start DATE = DATEFROMPARTS(YEAR(@D), 1, 1);
DECLARE @prev_year_start DATE = DATEFROMPARTS(YEAR(@D)-1, 1, 1);

;WITH base AS (
    SELECT
        sx.c_id AS c_store_id,
        sx.c_class AS c_retail,
        ISNULL(ts.c_sname, ts.c_name) AS c_store_name,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @D AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= DATEADD(DAY,-1,@D) AND ta.c_fact_da < @D THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_prev_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= DATEADD(DAY,-364,@D) AND ta.c_fact_da < DATEADD(DAY,-363,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS day_year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @month_start AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @prev_month_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(MONTH,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_prev_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @year_month_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS month_year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @year_start AND ta.c_fact_da < DATEADD(DAY,1,@D) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS year_sale,
        ISNULL(SUM(CASE WHEN ta.c_fact_da >= @prev_year_start AND ta.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN ta.c_sale_value-ta.c_disc_value ELSE 0 END),0) AS year_prev_sale
    FROM tbs_rd_store ta (NOLOCK)
    INNER JOIN tb_md_store_ex sx (NOLOCK) ON sx.c_tenant_id = ta.c_tenant_id AND sx.c_id = ta.c_store_id  AND sx.c_class  is not null AND sx.c_class <> ''
    JOIN tb_store ts (NOLOCK) ON ts.c_id = ta.c_store_id
    WHERE ta.c_tenant_id = @c_tenant_id
      AND ta.c_fact_da >= @prev_year_start
      AND ta.c_fact_da < DATEADD(DAY,1,@D)
    GROUP BY sx.c_id, sx.c_class, ts.c_name,ts.c_sname
),
retail_agg AS (
    SELECT
        ISNULL(b.c_retail, '') AS c_group_id,
        CASE WHEN b.c_retail IS NULL THEN N'其他' ELSE ISNULL(d.c_name, b.c_retail) END AS c_group_name,
        CASE WHEN b.c_retail IS NULL THEN 9999 ELSE ISNULL(d.c_sort, 999) END AS c_group_sort,
        2 AS c_group_type,
        '' AS c_group_type_name,
        COUNT(b.c_store_id) AS c_store_count,
        SUM(b.day_sale) AS day_sale,
        SUM(b.day_prev_sale) AS day_prev_sale,
        SUM(b.day_year_sale) AS day_year_sale,
        SUM(b.month_sale) AS month_sale,
        SUM(b.month_prev_sale) AS month_prev_sale,
        SUM(b.month_year_sale) AS month_year_sale,
        SUM(b.year_sale) AS year_sale,
        SUM(b.year_prev_sale) AS year_prev_sale
    FROM base b
    JOIN tb_id_sys d ON d.c_class = 's_md_retail' AND d.c_id = b.c_retail AND d.c_status = '1'
    WHERE b.c_retail IS NULL OR b.c_retail <> '30'
    GROUP BY b.c_retail, d.c_name, d.c_sort
),
store_agg AS (
    SELECT
        b.c_store_id AS c_group_id,
        ISNULL(b.c_store_name, b.c_store_id) AS c_group_name,
        b.c_store_id AS c_group_sort,
        1 AS c_group_type,
        d.c_name   AS c_group_type_name,
        1 AS c_store_count,
        b.day_sale, b.day_prev_sale, b.day_year_sale,
        b.month_sale, b.month_prev_sale, b.month_year_sale,
        b.year_sale, b.year_prev_sale
    FROM base b
    JOIN tb_id_sys d ON d.c_class = 's_md_retail' AND d.c_id = b.c_retail AND d.c_status = '1'
    WHERE b.c_retail = '30'
)
SELECT
    c_group_type,
    c_group_type_name,
    c_group_id,
    c_group_name,
    c_group_sort,
    c_store_count,
    day_sale,
    day_prev_sale,
    day_year_sale,
    month_sale,
    month_prev_sale,
    month_year_sale,
    year_sale,
    year_prev_sale,
    day_sale / NULLIF(ABS(SUM(day_sale) OVER()), 0) AS day_sale_rate,
    (day_sale - day_prev_sale) / NULLIF(ABS(day_prev_sale), 0) AS day_chain_rate,
    (day_sale - day_year_sale) / NULLIF(ABS(day_year_sale), 0) AS day_year_rate,
    (month_sale - month_prev_sale) / NULLIF(ABS(month_prev_sale), 0) AS month_chain_rate,
    (month_sale - month_year_sale) / NULLIF(ABS(month_year_sale), 0) AS month_year_rate,
    (year_sale - year_prev_sale) / NULLIF(ABS(year_prev_sale), 0) AS year_year_rate
FROM (
    SELECT * FROM retail_agg
    UNION ALL
    SELECT * FROM store_agg
) u
ORDER BY c_group_type ASC, c_group_sort ASC;
