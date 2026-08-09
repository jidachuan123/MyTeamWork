package com.demo.provider.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 盘点报表 Service — 连接 RDS_SC 库
 *
 * 执行用户提供的完整盘点报表 SQL（含临时表 #ttb_depart / #ls、CTE、多表关联）。
 * SQL 中的 DECLARE 变量改为由接口参数传入。
 *
 * 由于 SQL 包含临时表和多条语句，必须用同一个 Connection 一次性执行整个批处理，
 * 再从多个结果集中取最后一个（即最终汇总 SELECT）。
 */
@Service
public class ReportService {

    @Autowired
    @Qualifier("scDataSource")
    private DataSource scDataSource;

    /**
     * 盘点报表查询
     *
     * @param orgCode      机构
     * @param warehouse    仓库
     * @param department   部门
     * @param docNo        盘点单据号
     * @param docStatus    单据状态（尚未审核/已审核/已完成）
     * @param tenantId     租户ID
     * @param lang         语言
     * @param location     位置
     * @param checkResult  盘点结果（盘盈/盘亏/盘平/漏盘）
     * @param startDate    开始时间（yyyy-MM-dd）
     * @param endDate      结束时间（yyyy-MM-dd）
     * @return 汇总结果列表
     */
    public List<Map<String, Object>> getInventoryReport(
            String orgCode, String warehouse, String department, String docNo,
            String docStatus, String tenantId, String lang, String location,
            String checkResult, String startDate, String endDate) {

        String sql = buildSql(orgCode, warehouse, department, docNo,
                docStatus, tenantId, lang, location, checkResult, startDate, endDate);

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = scDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 执行整个批处理（DECLARE + 临时表 + CTE + SELECT INTO + 最终SELECT）
            boolean isResultSet = stmt.execute(sql);

            // 遍历所有结果，只保留最后一个 ResultSet（即最终汇总查询）
            while (isResultSet || stmt.getUpdateCount() != -1) {
                if (isResultSet) {
                    results.clear();
                    ResultSet rs = stmt.getResultSet();
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<String, Object>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                }
                isResultSet = stmt.getMoreResults();
            }

        } catch (SQLException e) {
            throw new RuntimeException("盘点报表查询失败: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * 构建完整 SQL：DECLARE 部分用参数值，其余原样保留
     */
    private String buildSql(String orgCode, String warehouse, String department, String docNo,
                            String docStatus, String tenantId, String lang, String location,
                            String checkResult, String startDate, String endDate) {

        StringBuilder sb = new StringBuilder(8192);

        // ===== 1. DECLARE 变量（参数值传入） =====
        sb.append("DECLARE \n");
        sb.append("    @机构 varchar(50) = '").append(esc(orgCode)).append("',\n");
        sb.append("    @仓库 varchar(50) = '").append(esc(warehouse)).append("',\n");
        sb.append("    @部门 varchar(50) = '").append(esc(department)).append("',\n");
        sb.append("    @盘点单据号 varchar(50) = '").append(esc(docNo)).append("',\n");
        sb.append("    @单据状态 varchar(50) = '").append(esc(docStatus)).append("',\n");
        sb.append("    @tenantid varchar(50) = '").append(esc(tenantId)).append("',\n");
        sb.append("    @lang varchar(50) = '").append(esc(lang)).append("',\n");
        sb.append("    @位置 varchar(50) = '").append(esc(location)).append("',\n");
        sb.append("    @盘点结果 varchar(50) = '").append(esc(checkResult)).append("', \n");
        sb.append("    @开始时间 datetime = '").append(esc(startDate)).append("', \n");
        sb.append("    @结束时间 datetime = '").append(esc(endDate)).append("'\n");

        // ===== 2. 以下为用户原始 SQL，原样保留 =====

        sb.append("\n");
        sb.append("IF OBJECT_ID('tempdb..#ttb_depart') IS NOT NULL DROP TABLE #ttb_depart\n\n");
        sb.append("IF OBJECT_ID('tempdb..#ls') IS NOT NULL DROP TABLE #ls\n\n");

        sb.append("CREATE TABLE #ttb_depart (c_adno VARCHAR(20) NOT NULL, c_id_level VARCHAR(20) NOT NULL)\n\n");

        sb.append("INSERT INTO #ttb_depart ( c_adno, c_id_level ) \n");
        sb.append("SELECT d.c_adno, d.c_id_level \n");
        sb.append("FROM STRING_SPLIT( @部门, ',' ) pd JOIN dbo.tb_depart d ON d.c_adno = pd.value AND d.c_tenant_id = @tenantId\n\n");

        sb.append("DECLARE @var__dept_level INT\n");
        sb.append("DECLARE @var__cat_level INT\n\n");

        sb.append("--IF ISNULL(@部门显示层级, '') = ''\n");
        sb.append("--BEGIN\n");
        sb.append("--    SET @var__dept_level = 0\n");
        sb.append("--END\n");
        sb.append("--ELSE\n");
        sb.append("--BEGIN\n");
        sb.append("\t--SET @var__dept_level = CONVERT(INT,@部门显示层级)\n");
        sb.append("--END\n\n\n");

        sb.append("\t\n");
        sb.append("-- 修正后的CTE，明确指定需要的字段而非使用通配符\n");
        sb.append(";WITH CheckQuantity AS (\n");
        sb.append("    SELECT \n");
        sb.append("        -- 明确指定a表需要的字段\n");
        sb.append("        a.c_count_dt,a.c_au_dt, a.c_id, a.c_store_id, td.c_adno, a.c_wno, \n");
        sb.append("        a.c_progress, a.c_mode3, a.c_status, a.c_tenant_id,\n");
        sb.append("        -- 明确指定b表需要的字段\n");
        sb.append("        b.c_ccode, b.c_gcode, b.c_barcode, b.c_unit, b.c_price, b.c_pt_cost,b.c_pet_cost,\n");
        sb.append("        b.c_n_inv, b.c_n, b.c_n2, b.c_n3,\n");
        sb.append("        b.c_at_cost_inv, b.c_at_cost, b.c_aet_cost_inv, b.c_aet_cost,\n");
        sb.append("        -- 明确指定c表和d表需要的字段\n");
        sb.append("        c.c_name AS 品类名称,\n");
        sb.append("        d.c_name AS 商品名称, d.c_model AS 规格,\n");
        sb.append("        -- 计算实盘数量\n");
        sb.append("        CASE \n");
        sb.append("            WHEN a.c_progress = '1' THEN ISNULL(b.c_n, 0) \n");
        sb.append("            WHEN a.c_progress = '2' THEN ISNULL(b.c_n2, ISNULL(b.c_n, 0))\n");
        sb.append("            WHEN a.c_progress = '3' THEN ISNULL(b.c_n3, ISNULL(b.c_n2, ISNULL(b.c_n, 0)))\n");
        sb.append("            ELSE \n");
        sb.append("                CASE \n");
        sb.append("                    WHEN a.c_mode3 = '0' THEN ISNULL(b.c_n2, ISNULL(b.c_n, 0)) \n");
        sb.append("                    ELSE ISNULL(b.c_n3, ISNULL(b.c_n2, ISNULL(b.c_n, 0))) \n");
        sb.append("                END \n");
        sb.append("        END AS 实盘数量\n");
        sb.append("    FROM dbo.tb_wo_count a(NOLOCK)  \n");
        sb.append("\t--left  JOIN dbo.tb_depart td (NOLOCK) ON td.c_tenant_id = @tenantid AND td.c_adno = a.c_adno\n");
        sb.append("    JOIN dbo.tb_wo_count_g b(NOLOCK) ON a.c_tenant_id = b.c_tenant_id AND a.c_id = b.c_id\n");
        sb.append("\t--修改关联部门改成明细部门\n");
        sb.append("    left  JOIN dbo.tb_depart td (NOLOCK) ON td.c_tenant_id = @tenantid AND td.c_adno = b.c_adno\n");
        sb.append("    LEFT JOIN dbo.tb_md_gdsclass c(NOLOCK) ON b.c_tenant_id = c.c_tenant_id AND b.c_ccode = c.c_ccode\n");
        sb.append("    LEFT JOIN dbo.tb_md_gds d(NOLOCK) ON b.c_tenant_id = d.c_tenant_id AND b.c_gcode = d.c_gcode\n");
        sb.append("    WHERE \n");
        sb.append("        (ISNULL(@机构, '') = '' OR a.c_store_id LIKE @机构 + '%')\n");
        sb.append("        AND (ISNULL(@盘点单据号, '') = '' OR a.c_id LIKE @盘点单据号 + '%')\n");
        sb.append("        AND (ISNULL(@部门, '') = '' OR EXISTS (SELECT 1 FROM #ttb_depart td1 WHERE td.c_id_level LIKE CONCAT(td1.c_id_level,'%')))\n");
        sb.append("        AND (ISNULL(@位置, '') = '' OR a.c_wno = @位置)\n");
        sb.append("        AND a.c_count_dt >= @开始时间\n");
        sb.append("        AND a.c_count_dt < DATEADD(day, 1, @结束时间)\n");
        sb.append("        AND (ISNULL(@单据状态, '') = '' OR a.c_status = \n");
        sb.append("            CASE \n");
        sb.append("                WHEN @单据状态 = '尚未审核' THEN 0\n");
        sb.append("                WHEN @单据状态 = '已审核' THEN 3\n");
        sb.append("                WHEN @单据状态 = '已完成' THEN 8\n");
        sb.append("            END\n");
        sb.append("\t\t\t\n");
        sb.append("\t\t\t)\n");
        sb.append(")\n\n\n");

        sb.append(" \n\n");

        // SELECT INTO #ls
        sb.append("-- 生成临时表（不再使用表别名，直接引用CTE中的字段）\n");
        sb.append("SELECT \n");
        sb.append("    c_count_dt AS 盘点日期,\n");
        sb.append("    c_au_dt AS 审核日期, \n");
        sb.append("    c_id AS 盘点单据号,\n");
        sb.append("    c_store_id AS 机构编码,\n");
        sb.append("    c_adno AS 盘点部门,\n");
        sb.append("    c_wno AS 仓库,\n");
        sb.append("    c_ccode AS 品类编码,\n");
        sb.append("    品类名称,\n");
        sb.append("    c_gcode AS 商品编码,\n");
        sb.append("    c_barcode AS 商品条码,\n");
        sb.append("    商品名称,\n");
        sb.append("    规格,\n");
        sb.append("    c_unit AS 单位,\n");
        sb.append("    c_price AS 售价,\n");
        sb.append("    c_n_inv AS 库存数量,\n");
        sb.append("    实盘数量,\n");
        sb.append("    实盘数量 - c_n_inv AS 数量差,\n");
        sb.append("    c_n_inv * c_price AS 系统金额售价,\n");
        sb.append("    实盘数量 * c_price AS 实际金额售价,\n");
        sb.append("    (实盘数量*c_price  - c_n_inv * c_price) AS 金额差售价,\n");
        sb.append("    c_at_cost_inv AS 系统金额含税进价,\n");
        sb.append("   \n");
        sb.append("\tCASE WHEN c_status =0 THEN 实盘数量* c_pt_cost  when c_status =3 then  c_at_cost_inv + c_at_cost  \n");
        sb.append("\twhen c_status =8 then  c_at_cost_inv + c_at_cost  \n");
        sb.append("    END\n");
        sb.append("   AS 实际金额含税进价,\n");
        sb.append("\tCASE WHEN c_status =0 THEN  (实盘数量*c_pt_cost  - c_n_inv* c_pt_cost)  when c_status =3 then  c_at_cost  \n");
        sb.append("\twhen c_status =8 then  c_at_cost  \n");
        sb.append("    END\n");
        sb.append("   AS 金额差含税进价,\n");
        sb.append("    c_aet_cost_inv AS 系统金额不含税进价,\n");
        sb.append("\tCASE WHEN c_status =0 THEN 实盘数量* c_pet_cost  when c_status =3 then   c_aet_cost_inv + c_aet_cost   \n");
        sb.append("\twhen c_status =8 then  c_aet_cost_inv + c_aet_cost  \n");
        sb.append("    END AS 实际金额不含税进价,\n");
        sb.append("  \n");
        sb.append("    c_aet_cost AS 金额差不含税进价,\n");
        sb.append("    c_tenant_id,\n");
        sb.append("    c_adno AS 部门编码  -- 明确命名，避免与盘点部门混淆\n");
        sb.append("INTO #ls\n");
        sb.append("FROM CheckQuantity\n");
        sb.append("WHERE \n");
        sb.append("    (ISNULL(@盘点结果, '') = ''\n");
        sb.append("    OR (@盘点结果 = '盘盈' AND c_n_inv < 实盘数量)\n");
        sb.append("    OR (@盘点结果 = '盘亏' AND c_n_inv > 实盘数量)\n");
        sb.append("    OR (@盘点结果 = '盘平' AND c_n_inv = 实盘数量)\n");
        sb.append("    OR (@盘点结果 = '漏盘' AND c_n_inv > 0 AND 实盘数量 IS NULL)\n");
        sb.append("    )\n");
        sb.append("ORDER BY c_count_dt, c_adno, c_wno, c_gcode\n\n");

        sb.append("/*----++++*/\n\n");

        // 最终汇总 SELECT
        sb.append("-- 汇总查询\n");
        sb.append("SELECT  \n");
        sb.append("    case when @var__dept_level !=0 then '' else 盘点日期 end as 盘点日期,\n");
        sb.append("    case when @var__dept_level !=0 then '' else 审核日期 end as 审核日期,\n");
        sb.append("    case when @var__dept_level !=0 then '' else 盘点单据号 end as 盘点单据号,\n");
        sb.append("    机构编码 as 机构编码,\n");
        sb.append("\tst.c_sname as 机构名称,\n");
        sb.append("    case when @var__dept_level !=0 then '' else 仓库 end as 仓库,\n");
        sb.append("    case when isnull(tdp.c_adno,0)=0 then 盘点部门 ELSE tdp.c_adno END  as 部门编码,\n");
        sb.append("    case when isnull(tdp.c_adno,0)=0 then td.c_name ELSE tdp.c_name END AS 部门名称,\n");
        sb.append("    SUM(库存数量) AS 库存数量,\n");
        sb.append("    SUM(实盘数量) AS 实盘数量,\n");
        sb.append("    SUM(数量差) AS 数量差,\n");
        sb.append("    SUM(系统金额售价) AS 系统金额售价,\n");
        sb.append("    SUM(实际金额售价) AS 实际金额售价,\n");
        sb.append("    SUM(金额差售价) AS 金额差售价,\n");
        sb.append("    SUM(系统金额含税进价) AS 系统金额含税进价,\n");
        sb.append("    SUM(实际金额含税进价) AS 实际金额含税进价,\n");
        sb.append("    SUM(金额差含税进价) AS 金额差含税进价,\n");
        sb.append("    SUM(系统金额不含税进价) AS 系统金额不含税进价,\n");
        sb.append("    SUM(实际金额不含税进价) AS 实际金额不含税进价,\n");
        sb.append("    SUM(金额差不含税进价) AS 金额差不含税进价\n");
        sb.append("FROM #ls \n");
        sb.append("LEFT JOIN tb_depart(NOLOCK) td ON td.c_adno = #ls.部门编码\n");
        sb.append("LEFT JOIN dbo.tb_depart tdp (NOLOCK) ON tdp.c_tenant_id = @tenantid AND td.c_id_level LIKE CONCAT(tdp.c_id_level,'%') and tdp.c_level = @var__dept_level\n");
        sb.append("left join tb_store(nolock) st on  st.c_id = #ls.机构编码\n");
        sb.append("GROUP BY  case when isnull(tdp.c_adno,0)=0 then 盘点部门 ELSE tdp.c_adno END, case when isnull(tdp.c_adno,0)=0 then td.c_name ELSE tdp.c_name END,\n");
        sb.append("case when @var__dept_level !=0 then '' else 盘点日期 end ,\n");
        sb.append("case when @var__dept_level !=0 then '' else 审核日期 end,\n");
        sb.append("    case when @var__dept_level !=0 then '' else 盘点单据号 end,\n");
        sb.append("    机构编码,\n");
        sb.append("\tst.c_sname,\n");
        sb.append("    case when @var__dept_level !=0 then '' else 仓库 end\n");
        sb.append("order by   机构编码,case when isnull(tdp.c_adno,0)=0 then 盘点部门 ELSE tdp.c_adno END;\n");

        return sb.toString();
    }

    /**
     * SQL 转义：单引号 → 两个单引号
     */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("'", "''");
    }
}
