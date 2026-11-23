-- ============================================================
-- 集团驾驶舱 · 门店下钻 · 三级品类销售明细
-- 数据源：192.168.0.120 / RDS_SC（只读账号 readonly_jdc）
--
-- 参数：
--   ${tenantId}    租户号，固定 8
--   ${storeId}     门店/机构编码，逗号分隔；空串 = 全部
--   ${brandLevel}  品类层级，默认 '3'
--   ${date}        业务日期，默认今天的前一天（T+1 表）
-- ============================================================
DECLARE @c_tenant_id int = ${tenantId};
DECLARE @c_store_id varchar(500) = '${storeId}';
DECLARE @c_brand_level VARCHAR(12) = '${brandLevel}';
DECLARE @c_sale_da DATE = '${date}';
DECLARE @D DATE = @c_sale_da;

DECLARE @month_start DATE = DATEFROMPARTS(YEAR(@D), MONTH(@D), 1);
DECLARE @prev_month_start DATE = DATEFROMPARTS(YEAR(DATEADD(MONTH,-1,@D)), MONTH(DATEADD(MONTH,-1,@D)), 1);
DECLARE @year_month_start DATE = DATEFROMPARTS(YEAR(@D)-1, MONTH(@D), 1);
DECLARE @year_start DATE = DATEFROMPARTS(YEAR(@D), 1, 1);
DECLARE @prev_year_start DATE = DATEFROMPARTS(YEAR(@D)-1, 1, 1);

;WITH target_cls AS (
    SELECT @c_tenant_id AS c_tenant_id, c_ccode, c_name, c_pccode
    FROM tb_md_gdsclass
    WHERE c_tenant_id = @c_tenant_id AND c_level = @c_brand_level
),
class_path AS (
    SELECT @c_tenant_id AS c_tenant_id, g.c_ccode AS c_leaf, g.c_pccode, CAST(ISNULL(g.c_name,'') AS NVARCHAR(1000)) AS path_name, 1 AS lvl
    FROM tb_md_gdsclass g
    INNER JOIN target_cls t ON g.c_tenant_id = t.c_tenant_id AND g.c_ccode = t.c_ccode
    UNION ALL
    SELECT t.c_tenant_id, t.c_leaf, p.c_pccode, CAST(ISNULL(p.c_name,'') + N'/' + t.path_name AS NVARCHAR(1000)), t.lvl + 1
    FROM tb_md_gdsclass p
    INNER JOIN class_path t ON p.c_tenant_id = t.c_tenant_id AND p.c_ccode = t.c_pccode
),
target_names AS (
    SELECT c_leaf, path_name AS c_ccode_name
    FROM (SELECT c_leaf, path_name, ROW_NUMBER() OVER (PARTITION BY c_leaf ORDER BY lvl DESC) AS rn FROM class_path) x
    WHERE rn = 1
),
down_tree AS (
    -- 从 target_cls 出发，沿 c_pccode 父子关系向下递归展开所有下级品类
    SELECT c_tenant_id, c_ccode, c_pccode, c_last_flag, c_ccode AS root_ccode
    FROM tb_md_gdsclass
    WHERE c_tenant_id = @c_tenant_id AND c_ccode IN (SELECT c_ccode FROM target_cls)
    UNION ALL
    SELECT c.c_tenant_id, c.c_ccode, c.c_pccode, c.c_last_flag, d.root_ccode
    FROM tb_md_gdsclass c
    INNER JOIN down_tree d ON c.c_tenant_id = d.c_tenant_id AND c.c_pccode = d.c_ccode
),
target_leaf AS (
    SELECT root_ccode AS target_ccode, c_ccode AS leaf_ccode
    FROM down_tree
    WHERE c_last_flag = '1'
),
sale_agg AS (
    SELECT s.c_ccode,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @D AND s.c_fact_da < DATEADD(DAY,1,@D) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS day_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= DATEADD(DAY,-1,@D) AND s.c_fact_da < @D THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS day_prev_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= DATEADD(DAY,-364,@D) AND s.c_fact_da < DATEADD(DAY,-363,@D) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS day_year_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @month_start AND s.c_fact_da < DATEADD(DAY,1,@D) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS month_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @prev_month_start AND s.c_fact_da < DATEADD(DAY,1,DATEADD(MONTH,-1,@D)) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS month_prev_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @year_month_start AND s.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS month_year_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @year_start AND s.c_fact_da < DATEADD(DAY,1,@D) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS year_sale,
        ISNULL(SUM(CASE WHEN s.c_fact_da >= @prev_year_start AND s.c_fact_da < DATEADD(DAY,1,DATEADD(YEAR,-1,@D)) THEN s.c_sale_value-s.c_disc_value ELSE 0 END),0) AS year_prev_sale
    FROM tbs_sd_brand s (NOLOCK)
    WHERE s.c_tenant_id = @c_tenant_id
      AND (@c_store_id = '' OR s.c_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ',')))
      AND s.c_fact_da >= @prev_year_start
      AND s.c_fact_da < DATEADD(DAY,1,@D)
    GROUP BY s.c_ccode
),
sale_target AS (
    SELECT tl.target_ccode,
        SUM(ISNULL(sl.day_sale, 0)) AS day_sale,
        SUM(ISNULL(sl.day_prev_sale, 0)) AS day_prev_sale,
        SUM(ISNULL(sl.day_year_sale, 0)) AS day_year_sale,
        SUM(ISNULL(sl.month_sale, 0)) AS month_sale,
        SUM(ISNULL(sl.month_prev_sale, 0)) AS month_prev_sale,
        SUM(ISNULL(sl.month_year_sale, 0)) AS month_year_sale,
        SUM(ISNULL(sl.year_sale, 0)) AS year_sale,
        SUM(ISNULL(sl.year_prev_sale, 0)) AS year_prev_sale
    FROM target_leaf tl
     JOIN sale_agg sl ON sl.c_ccode = tl.leaf_ccode
    GROUP BY tl.target_ccode
),
scope_total AS (
    -- 销售占比分母：与页面顶部汇总同口径（指定机构=本部+成员机构；未指定=全部机构）
    SELECT ISNULL(SUM(s.c_sale_value - s.c_disc_value), 0) AS day_sale
    FROM tbs_sd_brand s (NOLOCK)
    WHERE s.c_tenant_id = @c_tenant_id
      AND (@c_store_id = '' OR s.c_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ','))
           OR s.c_store_id IN (SELECT sx.c_id FROM tb_md_store_ex sx (NOLOCK)
                               WHERE sx.c_tenant_id = @c_tenant_id
                                 AND sx.c_entity_store_id IN (SELECT value FROM STRING_SPLIT(@c_store_id, ','))))
      AND s.c_fact_da >= @D
      AND s.c_fact_da < DATEADD(DAY,1,@D)
)
SELECT
    st.target_ccode AS c_ccode,
    tn.c_ccode_name,
    st.day_sale,
    st.month_sale,
    st.year_sale,
    (st.day_sale - st.day_prev_sale) / NULLIF(ABS(st.day_prev_sale), 0) AS day_chain_rate,
    (st.day_sale - st.day_year_sale) / NULLIF(ABS(st.day_year_sale), 0) AS day_year_rate,
    (st.month_sale - st.month_prev_sale) / NULLIF(ABS(st.month_prev_sale), 0) AS month_chain_rate,
    (st.month_sale - st.month_year_sale) / NULLIF(ABS(st.month_year_sale), 0) AS month_year_rate,
    (st.year_sale - st.year_prev_sale) / NULLIF(ABS(st.year_prev_sale), 0) AS year_year_rate,
    st.day_sale / NULLIF(t.day_sale, 0) AS sale_rate
FROM sale_target st
CROSS JOIN scope_total t
INNER JOIN target_names tn ON tn.c_leaf = st.target_ccode
ORDER BY st.day_sale DESC;
