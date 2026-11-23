-- ============================================================
-- 集团驾驶舱 · 门店下钻汇总（单店 / 机构集合）
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
--
-- 参数：
--   ${tenantId}   租户号，固定 8
--   ${storeId}    门店/机构编码，逗号分隔；空串 = 全部
--   ${date}       业务日期，默认今天的前一天（T+1 表）
--
-- 说明：指定机构（购物中心）时，成员机构（c_entity_store_id 指向该机构）的销售一并计入。
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @c_store_id varchar(500) = '${storeId}';
DECLARE @c_sale_da DATE = '${date}';
DECLARE @D DATE = @c_sale_da;

DECLARE @month_start DATE = DATEFROMPARTS(YEAR(@D), MONTH(@D), 1);
DECLARE @prev_month_start DATE = DATEFROMPARTS(YEAR(DATEADD(MONTH,-1,@D)), MONTH(DATEADD(MONTH,-1,@D)), 1);
DECLARE @year_month_start DATE = DATEFROMPARTS(YEAR(@D)-1, MONTH(@D), 1);
DECLARE @year_start DATE = DATEFROMPARTS(YEAR(@D), 1, 1);
DECLARE @prev_year_start DATE = DATEFROMPARTS(YEAR(@D)-1, 1, 1);

SELECT
  ISNULL(SUM(CASE WHEN c_fact_da >= @D AND c_fact_da < DATEADD(DAY,1,@D) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS day_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= DATEADD(DAY,-1,@D) AND c_fact_da < @D THEN c_sale_value-c_disc_value ELSE 0 END),0) AS day_prev_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= DATEADD(DAY,-364,@D) AND c_fact_da < DATEADD(DAY,-363,@D) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS day_year_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= @month_start AND c_fact_da < DATEADD(DAY,1,@D) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS month_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= @prev_month_start AND c_fact_da < DATEADD(DAY,1,DATEADD(MONTH,-1,@D)) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS month_prev_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= @year_month_start AND c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS month_year_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= @year_start AND c_fact_da < DATEADD(DAY,1,@D) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS year_sale,
  ISNULL(SUM(CASE WHEN c_fact_da >= @prev_year_start AND c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN c_sale_value-c_disc_value ELSE 0 END),0) AS year_prev_sale
FROM tbs_rd_store ta (NOLOCK)
WHERE ta.c_tenant_id = @c_tenant_id
  AND (@c_store_id = '' OR ta.c_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ','))
       -- 指定机构（购物中心）下钻时：成员机构（c_entity_store_id 指向该机构）的销售一并计入
       OR ta.c_store_id IN (SELECT sx.c_id FROM tb_md_store_ex sx (NOLOCK)
                            WHERE sx.c_tenant_id = @c_tenant_id
                              AND sx.c_entity_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ','))))
  AND ta.c_fact_da >= @prev_year_start
  AND ta.c_fact_da < DATEADD(DAY,1,@D);
