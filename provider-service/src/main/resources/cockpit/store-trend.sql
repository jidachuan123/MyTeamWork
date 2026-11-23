-- ============================================================
-- 集团驾驶舱 · 门店下钻 · 近 7 日销售（截止 ${date} 当天，含当天）
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
--
-- 参数：
--   ${tenantId}   租户号，固定 8
--   ${storeId}    门店/机构编码，逗号分隔；空串 = 不按门店过滤
--   ${storeClass} 业态编码（c_class）；空串 = 不按业态过滤
--   ${date}       业务日期（默认今天前一天）
--
-- 口径与 store-detail.sql / class-detail.sql 一致：
--   指定机构时成员机构（c_entity_store_id）一并计入；指定业态时按 c_class 圈定门店。
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @c_store_id varchar(500) = '${storeId}';
DECLARE @c_class varchar(50) = '${storeClass}';
DECLARE @c_sale_da DATE = '${date}';
DECLARE @D DATE = @c_sale_da;

SELECT
    CONVERT(varchar(5), ta.c_fact_da, 110) AS c_date,
    ISNULL(SUM(ta.c_sale_value - ta.c_disc_value), 0) AS c_sale
FROM tbs_rd_store ta (NOLOCK)
WHERE ta.c_tenant_id = @c_tenant_id
  AND (
        (@c_store_id = '' AND @c_class = '')
     OR (@c_store_id <> '' AND (
            ta.c_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ','))
         OR ta.c_store_id IN (SELECT sx.c_id FROM tb_md_store_ex sx (NOLOCK)
                              WHERE sx.c_tenant_id = @c_tenant_id
                                AND sx.c_entity_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ',')))
        ))
     OR (@c_class <> '' AND ta.c_store_id IN (SELECT sx.c_id FROM tb_md_store_ex sx (NOLOCK)
                                              WHERE sx.c_tenant_id = @c_tenant_id AND sx.c_class = @c_class))
  )
  AND ta.c_fact_da >= DATEADD(DAY, -6, @D)
  AND ta.c_fact_da < DATEADD(DAY, 1, @D)
GROUP BY ta.c_fact_da
ORDER BY ta.c_fact_da;
