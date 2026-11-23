-- ============================================================
-- 集团驾驶舱 · 近 7 日销售趋势（截止到 ${date} 当天，含当天）
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
-- 口径与 overview.sql 的 base 保持一致：租户 8 + tb_md_store_ex 有业态分类的门店。
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @D DATE = '${date}';

SELECT
    CONVERT(varchar(5), ta.c_fact_da, 110) AS c_date,
    ISNULL(SUM(ta.c_sale_value - ta.c_disc_value), 0) AS c_sale
FROM tbs_rd_store ta (NOLOCK)
INNER JOIN tb_md_store_ex sx (NOLOCK)
        ON sx.c_tenant_id = ta.c_tenant_id
       AND sx.c_id = ta.c_store_id
       AND sx.c_class IS NOT NULL
       AND sx.c_class <> ''
WHERE ta.c_tenant_id = @c_tenant_id
  AND ta.c_fact_da >= DATEADD(DAY, -6, @D)
  AND ta.c_fact_da < DATEADD(DAY, 1, @D)
GROUP BY ta.c_fact_da
ORDER BY ta.c_fact_da;
