-- ============================================================
-- 配送调拨汇总表（销售详情4 / SD4）—— 2026-10-16 新建
--
-- ★ 数据源：SQL Server RDS_SC（tb_io_om、tb_io_om_g 等表在 RDS_SC 库，实测 RDS_BC 无此表）
-- ★ 本文件是「页面查询」与「截图」共用的唯一取数口径，改这里两边同时生效。
-- ★ 本文件是【模板】：DECLARE 的值用「${参数名}」占位符，由 SalesDetail4Service 在执行前替换。
--   占位符清单（对应 application-sd4.yml）：
--     后端固定参数：${tenantId} ${lang} ${outWnoCodes} ${inOrgCodes} ${inWnoCodes}
--                   ${departCodes} ${categoryCodes} ${billType} ${accType}
--     页面/定时参数：${accStartDate} ${accEndDate} ${cmpStartDate} ${cmpEndDate}
--                   ${yoyStartDate} ${yoyEndDate} ${outOrgCodes}
-- ★ 用户原 SQL 的分组/过滤逻辑保持不变，仅将 DECLARE 的字面值改为占位符。
-- ============================================================

DECLARE @tenantId       varchar(20)='${tenantId}'
DECLARE @lang           char(1)='${lang}'
DECLARE @记账日期__sdt   datetime='${accStartDate}'
DECLARE @记账日期__edt   datetime='${accEndDate}'
DECLARE @环比日期__sdt   datetime='${cmpStartDate}'
DECLARE @环比日期__edt   datetime='${cmpEndDate}'
DECLARE @同比日期__sdt   datetime='${yoyStartDate}'
DECLARE @同比日期__edt   datetime='${yoyEndDate}'
DECLARE @发货机构        varchar(200)='${outOrgCodes}'
DECLARE @发货仓库        varchar(200)='${outWnoCodes}'
DECLARE @收货机构        varchar(8000)='${inOrgCodes}'
DECLARE @收货仓库        varchar(200)='${inWnoCodes}'
DECLARE @部门            varchar(8000)='${departCodes}'
DECLARE @品类            varchar(200)='${categoryCodes}'
DECLARE @类型            varchar(200)='${billType}'
DECLARE @记账类型        varchar(200)='${accType}'



IF OBJECT_ID('tempdb..#ttb_store_out') IS NOT NULL DROP TABLE #ttb_store_out
IF OBJECT_ID('tempdb..#ttb_store_in') IS NOT NULL DROP TABLE #ttb_store_in
IF OBJECT_ID('tempdb..#ttb_depart') IS NOT NULL DROP TABLE #ttb_depart
IF OBJECT_ID('tempdb..#ttb_catgory') IS NOT NULL DROP TABLE #ttb_catgory


CREATE TABLE #ttb_store_out (c_store_id VARCHAR(20) NOT NULL, c_id_level VARCHAR(20) NOT NULL)
CREATE TABLE #ttb_store_in (c_store_id VARCHAR(20) NOT NULL, c_id_level VARCHAR(20) NOT NULL)
CREATE TABLE #ttb_depart (c_adno VARCHAR(20) NOT NULL, c_id_level VARCHAR(20) NOT NULL)
CREATE TABLE #ttb_catgory (c_ccode VARCHAR(30) NOT NULL)

--发货机构
INSERT INTO #ttb_store_out ( c_store_id, c_id_level )
SELECT c_get_store_id, c_id_level
FROM dbo.uf_get_stores( @tenantId, @发货机构)
--收货机构
INSERT INTO #ttb_store_in ( c_store_id, c_id_level )
SELECT c_get_store_id, c_id_level
FROM dbo.uf_get_stores( @tenantId, @收货机构)
--部门
INSERT INTO #ttb_depart ( c_adno, c_id_level )
SELECT d.c_adno, d.c_id_level
FROM STRING_SPLIT( @部门, ',' ) pd JOIN dbo.tb_depart d ON d.c_adno = pd.value AND d.c_tenant_id = @tenantId
--品类
INSERT INTO #ttb_catgory ( c_ccode )
SELECT  c.c_ccode
FROM STRING_SPLIT( @品类, ',') pc JOIN dbo.tb_md_gdsclass c ON c.c_ccode = pc.value AND c.c_tenant_id = @tenantId

/*----++++*/

SELECT 
       c_out_store_id AS 发货机构编码,
    c_out_store_name AS 发货机构名称,
    mykucun.hanshuichengbenjine AS 当日库存金额,
    SUM(TA.c_at_pay_out) AS 发货结算金额,
    SUM(TA.c_at_pay_out_dqhb) AS 环比发货结算金额,
    SUM(TA.c_at_pay_out_dqtb) AS 同比发货结算金额,
    
    CASE WHEN SUM(TA.c_at_pay_out_dqhb) = 0 THEN NULL
         ELSE (SUM(TA.c_at_pay_out) - SUM(TA.c_at_pay_out_dqhb))  / SUM(TA.c_at_pay_out_dqhb)
    END AS 环比增长率,
    -- 同比增长率 = (本期-同比)/同比 * 100%
    CASE WHEN SUM(TA.c_at_pay_out_dqtb) = 0 THEN NULL
         ELSE (SUM(TA.c_at_pay_out) - SUM(TA.c_at_pay_out_dqtb))  / SUM(TA.c_at_pay_out_dqtb)
    END AS 同比增长率
    
FROM (
SELECT 
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en ) c_out_store_name,
    ISNULL( SUM ( IIF(om.c_bill_type LIKE '81%',-1,1) *  g.c_at_pay_out ), 0 ) c_at_pay_out,
    0 c_at_pay_out_dqhb,
    0 c_at_pay_out_dqtb
FROM tb_io_om om ( NOLOCK )
    INNER join tb_io_om_g g ( NOLOCK ) ON om.c_id = g.c_id AND g.c_tenant_id = @tenantId
    INNER JOIN tb_store st_out ( NOLOCK ) ON om.c_out_store_id = st_out.c_id AND st_out.c_tenant_id = @tenantId
    INNER JOIN tb_store st_in ( NOLOCK ) ON om.c_in_store_id = st_in.c_id AND st_in.c_tenant_id = @tenantId
	INNER JOIN tb_md_store_ex stx_out ( NOLOCK ) ON om.c_out_store_id = stx_out.c_id AND stx_out.c_tenant_id = @tenantId
    INNER JOIN tb_md_store_ex stx_in ( NOLOCK ) ON om.c_in_store_id = stx_in.c_id AND stx_in.c_tenant_id = @tenantId
    INNER JOIN tb_depart depart ( NOLOCK ) ON g.c_out_adno = depart.c_adno AND depart.c_tenant_id = @tenantId
	LEFT JOIN tb_md_wno w_out ( NOLOCK ) ON w_out.c_wno = om.c_out_wno AND w_out.c_tenant_id = @tenantId
    LEFT JOIN tb_md_wno w_in ( NOLOCK ) ON w_in.c_wno = om.c_in_wno AND w_in.c_tenant_id = @tenantId
WHERE
    om.c_tenant_id = @tenantId
    AND ( @发货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_out s WHERE s.c_store_id = om.c_out_store_id ) ) 
    AND ( @收货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_in s WHERE s.c_store_id = om.c_in_store_id ) ) 
    AND ( @部门 = '' OR EXISTS ( SELECT 1 FROM #ttb_depart pd WHERE depart.c_id_level LIKE pd.c_id_level+ '%' OR pd.c_id_level LIKE depart.c_id_level + '%' ) ) 
    AND ( @品类= '' OR EXISTS ( SELECT 1 FROM #ttb_catgory cat WHERE g.c_out_ccode LIKE cat.c_ccode + '%') )
	AND ( EXISTS ( SELECT VALUE FROM string_split ( @发货仓库, ',' ) WHERE om.c_out_wno LIKE VALUE + '%' ) OR ISNULL( @发货仓库, '' ) = '' ) 
    AND ( EXISTS ( SELECT VALUE FROM string_split ( @收货仓库, ',' ) WHERE om.c_in_wno LIKE VALUE + '%' ) OR ISNULL( @收货仓库, '' ) = '' )
    AND (@类型 = '' or om.c_bill_type LIKE CASE WHEN @类型 = '配送' THEN '80%' WHEN @类型= '退配' THEN '81%' ELSE '82%' END)
	AND (@记账类型 = '' OR @记账类型 = '内部配送' AND stx_out.c_settle_region = stx_in.c_settle_region 
	     OR @记账类型 = '内部批发' AND stx_out.c_settle_region <> stx_in.c_settle_region) 
    AND om.c_acc_da >= @记账日期__sdt AND om.c_acc_da < dateadd ( dd, 1, @记账日期__edt )
GROUP BY
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en )
  
union all

SELECT 
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en ) c_out_store_name,
    0 ,
    ISNULL( SUM ( IIF(om.c_bill_type LIKE '81%',-1,1) *  g.c_at_pay_out ), 0 ) c_at_pay_out_dqhb,
    0 
FROM tb_io_om om ( NOLOCK )
    INNER join tb_io_om_g g ( NOLOCK ) ON om.c_id = g.c_id AND g.c_tenant_id = @tenantId
    INNER JOIN tb_store st_out ( NOLOCK ) ON om.c_out_store_id = st_out.c_id AND st_out.c_tenant_id = @tenantId
    INNER JOIN tb_store st_in ( NOLOCK ) ON om.c_in_store_id = st_in.c_id AND st_in.c_tenant_id = @tenantId
	INNER JOIN tb_md_store_ex stx_out ( NOLOCK ) ON om.c_out_store_id = stx_out.c_id AND stx_out.c_tenant_id = @tenantId
    INNER JOIN tb_md_store_ex stx_in ( NOLOCK ) ON om.c_in_store_id = stx_in.c_id AND stx_in.c_tenant_id = @tenantId
    INNER JOIN tb_depart depart ( NOLOCK ) ON g.c_out_adno = depart.c_adno AND depart.c_tenant_id = @tenantId
	LEFT JOIN tb_md_wno w_out ( NOLOCK ) ON w_out.c_wno = om.c_out_wno AND w_out.c_tenant_id = @tenantId
    LEFT JOIN tb_md_wno w_in ( NOLOCK ) ON w_in.c_wno = om.c_in_wno AND w_in.c_tenant_id = @tenantId
WHERE
    om.c_tenant_id = @tenantId
    AND ( @发货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_out s WHERE s.c_store_id = om.c_out_store_id ) ) 
    AND ( @收货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_in s WHERE s.c_store_id = om.c_in_store_id ) ) 
    AND ( @部门 = '' OR EXISTS ( SELECT 1 FROM #ttb_depart pd WHERE depart.c_id_level LIKE pd.c_id_level+ '%' OR pd.c_id_level LIKE depart.c_id_level + '%' ) ) 
    AND ( @品类= '' OR EXISTS ( SELECT 1 FROM #ttb_catgory cat WHERE g.c_out_ccode LIKE cat.c_ccode + '%') )
	AND ( EXISTS ( SELECT VALUE FROM string_split ( @发货仓库, ',' ) WHERE om.c_out_wno LIKE VALUE + '%' ) OR ISNULL( @发货仓库, '' ) = '' ) 
    AND ( EXISTS ( SELECT VALUE FROM string_split ( @收货仓库, ',' ) WHERE om.c_in_wno LIKE VALUE + '%' ) OR ISNULL( @收货仓库, '' ) = '' )
    AND (@类型 = '' or om.c_bill_type LIKE CASE WHEN @类型 = '配送' THEN '80%' WHEN @类型= '退配' THEN '81%' ELSE '82%' END)
	AND (@记账类型 = '' OR @记账类型 = '内部配送' AND stx_out.c_settle_region = stx_in.c_settle_region 
	     OR @记账类型 = '内部批发' AND stx_out.c_settle_region <> stx_in.c_settle_region) 
    AND om.c_acc_da >= @环比日期__sdt AND om.c_acc_da < dateadd ( dd, 1, @环比日期__edt )
GROUP BY
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en )
union all

SELECT 
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en ) c_out_store_name,
    0 ,
    0 ,
    ISNULL( SUM ( IIF(om.c_bill_type LIKE '81%',-1,1) *  g.c_at_pay_out ), 0 ) c_at_pay_out_dqtb

FROM tb_io_om om ( NOLOCK )
    INNER join tb_io_om_g g ( NOLOCK ) ON om.c_id = g.c_id AND g.c_tenant_id = @tenantId
    INNER JOIN tb_store st_out ( NOLOCK ) ON om.c_out_store_id = st_out.c_id AND st_out.c_tenant_id = @tenantId
    INNER JOIN tb_store st_in ( NOLOCK ) ON om.c_in_store_id = st_in.c_id AND st_in.c_tenant_id = @tenantId
	INNER JOIN tb_md_store_ex stx_out ( NOLOCK ) ON om.c_out_store_id = stx_out.c_id AND stx_out.c_tenant_id = @tenantId
    INNER JOIN tb_md_store_ex stx_in ( NOLOCK ) ON om.c_in_store_id = stx_in.c_id AND stx_in.c_tenant_id = @tenantId
    INNER JOIN tb_depart depart ( NOLOCK ) ON g.c_out_adno = depart.c_adno AND depart.c_tenant_id = @tenantId
	LEFT JOIN tb_md_wno w_out ( NOLOCK ) ON w_out.c_wno = om.c_out_wno AND w_out.c_tenant_id = @tenantId
    LEFT JOIN tb_md_wno w_in ( NOLOCK ) ON w_in.c_wno = om.c_in_wno AND w_in.c_tenant_id = @tenantId
WHERE
    om.c_tenant_id = @tenantId
    AND ( @发货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_out s WHERE s.c_store_id = om.c_out_store_id ) ) 
    AND ( @收货机构 = '' OR EXISTS ( SELECT 1 FROM #ttb_store_in s WHERE s.c_store_id = om.c_in_store_id ) ) 
    AND ( @部门 = '' OR EXISTS ( SELECT 1 FROM #ttb_depart pd WHERE depart.c_id_level LIKE pd.c_id_level+ '%' OR pd.c_id_level LIKE depart.c_id_level + '%' ) ) 
    AND ( @品类= '' OR EXISTS ( SELECT 1 FROM #ttb_catgory cat WHERE g.c_out_ccode LIKE cat.c_ccode + '%') )
	AND ( EXISTS ( SELECT VALUE FROM string_split ( @发货仓库, ',' ) WHERE om.c_out_wno LIKE VALUE + '%' ) OR ISNULL( @发货仓库, '' ) = '' ) 
    AND ( EXISTS ( SELECT VALUE FROM string_split ( @收货仓库, ',' ) WHERE om.c_in_wno LIKE VALUE + '%' ) OR ISNULL( @收货仓库, '' ) = '' )
    AND (@类型 = '' or om.c_bill_type LIKE CASE WHEN @类型 = '配送' THEN '80%' WHEN @类型= '退配' THEN '81%' ELSE '82%' END)
	AND (@记账类型 = '' OR @记账类型 = '内部配送' AND stx_out.c_settle_region = stx_in.c_settle_region 
	     OR @记账类型 = '内部批发' AND stx_out.c_settle_region <> stx_in.c_settle_region) 
    AND om.c_acc_da >= @同比日期__sdt AND om.c_acc_da < dateadd ( dd, 1, @同比日期__edt )
GROUP BY
    om.c_out_store_id,
    IIF ( @lang = 'L', st_out.c_name, st_out.c_name_en )
) AS TA


LEFT JOIN
(
    SELECT   
        sto.c_store_id AS jigoubianma,
        sum(sto.c_at_cost) as hanshuichengbenjine
    FROM
        tb_wb_gdsstock sto ( NOLOCK ) 
    WHERE
        sto.c_store_id not in ('1101951','1103951','1501001','1501031','1101099') 
        and sto.c_store_id not like '1108%'
        and sto.c_store_id not like '13%'
    group by sto.c_store_id
) as mykucun
on  c_out_store_id = mykucun.jigoubianma



GROUP BY TA.c_out_store_id,TA.c_out_store_name,mykucun.hanshuichengbenjine
