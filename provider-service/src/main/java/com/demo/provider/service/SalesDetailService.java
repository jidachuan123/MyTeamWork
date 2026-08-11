package com.demo.provider.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 销售详情报表 Service — 连接 RDS_SC 库
 *
 * 执行用户提供的完整销售详情 SQL（含临时表 #ttb_dept_level/#ttb_cat_level、
 * #TEMP_REP_SALE_PRO/#TEMP_REP_TRADE、存储过程 dm.up_GetFine_Run、
 * up_rep_cover_dept_level/up_rep_cover_cat_level 等）。
 *
 * 由于 SQL 包含临时表和多条语句，必须用同一个 Connection 一次性执行整个批处理，
 * 再从多个结果集中取最后一个（即最终汇总 SELECT）。
 */
@Service
public class SalesDetailService {

    @Autowired
    @Qualifier("scDataSource")
    private DataSource scDataSource;

    /**
     * 销售详情查询
     *
     * @param tenantId        租户ID
     * @param lang            语言 L/E
     * @param userNo          用户编号
     * @param dateType        日期类型（记账日期/发生日期）
     * @param startDate       查询开始日期 yyyy-MM-dd
     * @param endDate         查询结束日期 yyyy-MM-dd
     * @param cmpStartDate    对比开始日期 yyyy-MM-dd
     * @param cmpEndDate      对比结束日期 yyyy-MM-dd
     * @param showStore       是否显示门店
     * @param deptLevels      部门显示层级（逗号分隔，如 "3"）
     * @param catLevels       品类显示层级
     * @param showBrand       是否显示品牌
     * @param orgCode         机构编码
     * @param department      部门
     * @param category        品类
     * @param brand           品牌
     * @param channel         渠道
     * @param comparableStore 可比门店
     * @param showPlan        是否展示计划
     * @param showDate        是否显示日期
     * @return 销售详情结果列表
     */
    public List<Map<String, Object>> getSalesDetail(
            String tenantId, String lang, String userNo,
            String dateType, String startDate, String endDate,
            String cmpStartDate, String cmpEndDate,
            String showStore, String deptLevels, String catLevels, String showBrand,
            String orgCode, String department, String category, String brand, String channel,
            String comparableStore, String showPlan, String showDate) {

        String sql = buildSql(tenantId, lang, userNo, dateType, startDate, endDate,
                cmpStartDate, cmpEndDate, showStore, deptLevels, catLevels, showBrand,
                orgCode, department, category, brand, channel,
                comparableStore, showPlan, showDate);

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = scDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 执行整个批处理（SET NOCOUNT ON + DECLARE + 临时表 + 存储过程 + UPDATE + 最终SELECT）
            boolean isResultSet = stmt.execute(sql);

            // 遍历所有结果，只保留最后一个 ResultSet（即最终汇总查询）
            while (isResultSet || stmt.getUpdateCount() != -1) {
                if (isResultSet) {
                    results.clear();
                    ResultSet rs = stmt.getResultSet();
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                }
                isResultSet = stmt.getMoreResults();
            }

        } catch (SQLException e) {
            throw new RuntimeException("销售详情查询失败: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * 构建完整 SQL（参数值替换 DECLARE 变量）
     */
    private String buildSql(
            String tenantId, String lang, String userNo,
            String dateType, String startDate, String endDate,
            String cmpStartDate, String cmpEndDate,
            String showStore, String deptLevels, String catLevels, String showBrand,
            String orgCode, String department, String category, String brand, String channel,
            String comparableStore, String showPlan, String showDate) {

        StringBuilder sb = new StringBuilder(32768);

        // ===== SET NOCOUNT ON =====
        sb.append("SET NOCOUNT ON\n\n");

        // ===== DECLARE 变量 =====
        sb.append("DECLARE @tenantid VARCHAR(100) = '").append(esc(tenantId)).append("'\n");
        sb.append("DECLARE @lang VARCHAR(100) = '").append(esc(lang)).append("'\n");
        sb.append("DECLARE @userNo VARCHAR(100) = '").append(esc(userNo)).append("'\n");
        sb.append("DECLARE @日期类型 VARCHAR(100) = '").append(esc(dateType)).append("'\n");
        sb.append("DECLARE @查询日期__sdt VARCHAR(100) = '").append(esc(startDate)).append("'\n");
        sb.append("DECLARE @查询日期__edt VARCHAR(100) = '").append(esc(endDate)).append("'\n");
        sb.append("DECLARE @对比日期__sdt VARCHAR(100) = '").append(esc(cmpStartDate)).append("'\n");
        sb.append("DECLARE @对比日期__edt VARCHAR(100) = '").append(esc(cmpEndDate)).append("'\n");
        sb.append("DECLARE @是否显示门店 VARCHAR(100) = '").append(esc(showStore)).append("'\n");
        sb.append("DECLARE @部门显示层级 VARCHAR(max) = '").append(esc(deptLevels)).append("'\n");
        sb.append("DECLARE @品类显示层级 VARCHAR(max) = '").append(esc(catLevels)).append("'\n");
        sb.append("DECLARE @是否显示品牌 VARCHAR(100) = '").append(esc(showBrand)).append("'\n");
        sb.append("DECLARE @机构 VARCHAR(100) = '").append(esc(orgCode)).append("'\n");
        sb.append("DECLARE @部门 VARCHAR(100) = '").append(esc(department)).append("'\n");
        sb.append("DECLARE @品类 VARCHAR(100) = '").append(esc(category)).append("'\n");
        sb.append("DECLARE @品牌 VARCHAR(100) = '").append(esc(brand)).append("'\n");
        sb.append("DECLARE @渠道 VARCHAR(100) = '").append(esc(channel)).append("'\n");
        sb.append("DECLARE @可比门店 VARCHAR(100) = '").append(esc(comparableStore)).append("'\n");
        sb.append("DECLARE @是否展示计划 VARCHAR(100) = '").append(esc(showPlan)).append("'\n");
        sb.append("DECLARE @是否显示日期 VARCHAR(100) = '").append(esc(showDate)).append("'\n\n");

        // ===== 以下为用户原始 SQL，原样保留 =====
        sb.append("DECLARE @var__部门维度 VARCHAR(max)\n");
        sb.append("DECLARE @var__品类维度 VARCHAR(max)\n");
        sb.append("DECLARE @var__显示门店 VARCHAR(100)\n");
        sb.append("DECLARE @var__显示品牌 VARCHAR(100)\n");
        sb.append("DECLARE @var__显示日期 VARCHAR(100)\n");
        sb.append("DECLARE @var__cols     VARCHAR(4000)\n\n");

        sb.append("IF OBJECT_ID('tempdb..#ttb_dept_level') IS NOT NULL DROP TABLE #ttb_dept_level\n");
        sb.append("CREATE TABLE #ttb_dept_level (C_ROWNO INT IDENTITY(1,1),c_level INT NOT NULL)\n");
        sb.append("INSERT INTO #ttb_dept_level ( c_level )\n");
        sb.append("SELECT DISTINCT CAST(value AS INT)\n");
        sb.append("FROM STRING_SPLIT( ISNULL(@部门显示层级, ''), ',')\n");
        sb.append("WHERE ISNULL(value, '') <> ''\n\n");

        sb.append("DECLARE @var__cnt_dept INT\n");
        sb.append("SELECT @var__cnt_dept = COUNT(1) FROM #ttb_dept_level\n\n");

        sb.append("IF @var__cnt_dept = 0\n");
        sb.append("    BEGIN\n");
        sb.append("        SET @var__部门维度 = ''\n");
        sb.append("    END\n");
        sb.append("ELSE IF EXISTS (SELECT 1 FROM #ttb_dept_level WHERE c_level = 9)\n");
        sb.append("    BEGIN\n");
        sb.append("        SELECT @var__部门维度 = CONCAT('C_ADNO;C_DEPART_NAME;', ISNULL((SELECT STRING_AGG(CONCAT('C_DEPTID', c_level, ';C_DEPTNAME', c_level, ';'), '') WITHIN GROUP (ORDER BY c_level) FROM #ttb_dept_level WHERE c_level <> 9), ''))\n");
        sb.append("    END\n");
        sb.append("ELSE\n");
        sb.append("    BEGIN\n");
        sb.append("        SELECT @var__部门维度 = STRING_AGG(CONCAT('C_DEPTID', c_level, ';C_DEPTNAME', c_level, ';'), '') WITHIN GROUP (ORDER BY c_level) FROM #ttb_dept_level\n");
        sb.append("    END\n\n");

        sb.append("IF OBJECT_ID('tempdb..#ttb_cat_level') IS NOT NULL DROP TABLE #ttb_cat_level\n");
        sb.append("CREATE TABLE #ttb_cat_level (C_ROWNO INT IDENTITY(1,1),c_level INT NOT NULL)\n");
        sb.append("INSERT INTO #ttb_cat_level ( c_level )\n");
        sb.append("SELECT DISTINCT CAST(value AS INT)\n");
        sb.append("FROM STRING_SPLIT( ISNULL(@品类显示层级, ''), ',')\n");
        sb.append("WHERE ISNULL(value, '') <> ''\n\n");

        sb.append("DECLARE @var__cnt_cat INT\n");
        sb.append("SELECT @var__cnt_cat = COUNT(1) FROM #ttb_cat_level\n\n");

        sb.append("IF @var__cnt_cat = 0\n");
        sb.append("    BEGIN\n");
        sb.append("        SET @var__品类维度 = ''\n");
        sb.append("    END\n");
        sb.append("ELSE IF EXISTS (SELECT 1 FROM #ttb_cat_level WHERE c_level = 9)\n");
        sb.append("    BEGIN\n");
        sb.append("        SELECT @var__品类维度 = CONCAT('C_CCODE;C_CATNAME;', ISNULL((SELECT STRING_AGG(CONCAT('C_CATID', c_level, ';C_CATNAME', c_level, ';'), '') WITHIN GROUP (ORDER BY c_level) FROM #ttb_cat_level WHERE c_level <> 9), ''))\n");
        sb.append("    END\n");
        sb.append("ELSE\n");
        sb.append("    BEGIN\n");
        sb.append("        SELECT @var__品类维度 = STRING_AGG(CONCAT('C_CATID', c_level, ';C_CATNAME', c_level, ';'), '') WITHIN GROUP (ORDER BY c_level) FROM #ttb_cat_level\n");
        sb.append("    END\n\n");

        sb.append("IF ISNULL(@是否显示门店, '') = ''\n");
        sb.append("   BEGIN\n");
        sb.append("      SET @var__显示门店 = ''\n");
        sb.append("   END\n");
        sb.append("ELSE IF ISNULL(@是否显示门店, '') = '显示门店'\n");
        sb.append("   BEGIN\n");
        sb.append("      SET @var__显示门店 = 'C_STORE_ID;C_STORE_NAME;'\n");
        sb.append("   END\n");
        sb.append("ELSE\n");
        sb.append("   BEGIN\n");
        sb.append("      SET @var__显示门店 = ''\n");
        sb.append("   END\n\n");

        sb.append("IF ISNULL(@是否显示品牌, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@是否显示品牌, '') = '显示品牌'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = 'C_GDS_BCODE;'\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = ''\n");
        sb.append("END\n\n");

        sb.append("IF ISNULL(@是否显示日期, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示日期 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@是否显示日期, '') = '显示日期'\n");
        sb.append("BEGIN\n");
        sb.append("    IF @日期类型 = '记账日期'\n");
        sb.append("    BEGIN\n");
        sb.append("        SET @var__显示日期 = 'C_HSDATE;'\n");
        sb.append("    END\n");
        sb.append("    ELSE\n");
        sb.append("    BEGIN\n");
        sb.append("        SET @var__显示日期 = 'C_FSDATE;'\n");
        sb.append("    END\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示日期 = ''\n");
        sb.append("END\n\n");

        // ===== #TEMP_REP_SALE_PRO 临时表 =====
        sb.append("IF OBJECT_ID('tempdb..#TEMP_REP_SALE_PRO') IS NOT NULL DROP TABLE #TEMP_REP_SALE_PRO\n");
        sb.append("CREATE TABLE #TEMP_REP_SALE_PRO (\n");
        sb.append("C_HSDATE    DATE,\n");
        sb.append("C_FSDATE    DATE,\n");
        sb.append("C_STORE_ID    VARCHAR(200),\n");
        sb.append("C_STORE_NAME    VARCHAR(200),\n");
        sb.append("C_ADNO       VARCHAR(200),\n");
        sb.append("C_DEPTID1    VARCHAR(200),\n");
        sb.append("C_DEPTID2    VARCHAR(200),\n");
        sb.append("C_DEPTID3    VARCHAR(200),\n");
        sb.append("C_DEPTID4    VARCHAR(200),\n");
        sb.append("C_DEPTID5    VARCHAR(200),\n");
        sb.append("C_DEPTID6    VARCHAR(200),\n");
        sb.append("C_DEPTID7    VARCHAR(200),\n");
        sb.append("C_DEPTID8    VARCHAR(200),\n");
        sb.append("C_DEPTNAME1    VARCHAR(200),\n");
        sb.append("C_DEPTNAME2    VARCHAR(200),\n");
        sb.append("C_DEPTNAME3    VARCHAR(200),\n");
        sb.append("C_DEPTNAME4    VARCHAR(200),\n");
        sb.append("C_DEPTNAME5    VARCHAR(200),\n");
        sb.append("C_DEPTNAME6    VARCHAR(200),\n");
        sb.append("C_DEPTNAME7    VARCHAR(200),\n");
        sb.append("C_DEPTNAME8    VARCHAR(200),\n");
        sb.append("C_DEPART_NAME    VARCHAR(200),\n");
        sb.append("C_CCODE     VARCHAR(200),\n");
        sb.append("C_CATID1    VARCHAR(200),\n");
        sb.append("C_CATID2    VARCHAR(200),\n");
        sb.append("C_CATID3    VARCHAR(200),\n");
        sb.append("C_CATID4    VARCHAR(200),\n");
        sb.append("C_CATID5    VARCHAR(200),\n");
        sb.append("C_CATID6    VARCHAR(200),\n");
        sb.append("C_CATID7    VARCHAR(200),\n");
        sb.append("C_CATID8    VARCHAR(200),\n");
        sb.append("C_CATNAME1    VARCHAR(200),\n");
        sb.append("C_CATNAME2    VARCHAR(200),\n");
        sb.append("C_CATNAME3    VARCHAR(200),\n");
        sb.append("C_CATNAME4    VARCHAR(200),\n");
        sb.append("C_CATNAME5    VARCHAR(200),\n");
        sb.append("C_CATNAME6    VARCHAR(200),\n");
        sb.append("C_CATNAME7    VARCHAR(200),\n");
        sb.append("C_CATNAME8    VARCHAR(200),\n");
        sb.append("C_CATNAME   VARCHAR(200),\n");
        sb.append("C_GDS_BCODE    VARCHAR(63),\n");
        sb.append("C_SALE_QTY NUMERIC(14, 4),\n");
        sb.append("C_SALE_QTY_FPL NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_WSALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_AT_COST NUMERIC(14, 4),\n");
        sb.append("C_AET_COST NUMERIC(14, 4),\n");
        sb.append("C_DISC_VALUE NUMERIC(14, 4),\n");
        sb.append("C_AT_DISC NUMERIC(14, 4),\n");
        sb.append("C_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_WSALE_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_NET_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_SALE_MEM NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_MEM NUMERIC(14, 4),\n");
        sb.append("C_SALE_MEM_RATE NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_PRO NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_REVENUE_PRO NUMERIC(14, 4),\n");
        sb.append("C_AT_COST_PRO NUMERIC(14, 4),\n");
        sb.append("C_AET_COST_PRO NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_PRO NUMERIC(14, 4),\n");
        sb.append("C_NET_PROFIT_PRO NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_PRO_RATE NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_PRO_RATE NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_WSALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_AT_COST_DQ NUMERIC(14, 4),\n");
        sb.append("C_AET_COST_DQ NUMERIC(14, 4),\n");
        sb.append("C_DISC_VALUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_AT_DISC_DQ NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_WSALE_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_NET_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_SALE_MEM_DQ NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_MEM_DQ NUMERIC(14, 4),\n");
        sb.append("C_SALE_MEM_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_NET_SALE_REVENUE_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_AT_COST_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_AET_COST_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_NET_PROFIT_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_PRO_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_PRO_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_COUNT_TRADE NUMERIC(14, 4),\n");
        sb.append("C_COUNT_TRADE_DQ NUMERIC(14, 4),\n");
        sb.append("C_CUST_PRICE NUMERIC(14, 4),\n");
        sb.append("C_CUST_PRICE_DQ NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_DBC NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_DBC NUMERIC(14, 4),\n");
        sb.append("C_SALE_REVENUE_PM NUMERIC(14, 4),\n");
        sb.append("C_PROFIT_PM NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_QTY NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_QTY_FPL NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_ACC_WSALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_REVENUE NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_COST NUMERIC(14, 4),\n");
        sb.append("C_ACC_AET_COST NUMERIC(14, 4),\n");
        sb.append("C_ACC_DISC_VALUE NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_DISC NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_ACC_WSALE_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_MEM NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_MEM NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_MEM_RATE NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_REVENUE_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_COST_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_AET_COST_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_PROFIT_PRO NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_PRO_RATE NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_PRO_RATE NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_WSALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_REVENUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_COST_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_AET_COST_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_DISC_VALUE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_DISC_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_WSALE_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_PROFIT_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_MEM_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_MEM_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_MEM_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_SALE_REVENUE_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_AT_COST_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_AET_COST_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_NET_PROFIT_PRO_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_PRO_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_PRO_RATE_DQ NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_DBC NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_DBC NUMERIC(14, 4),\n");
        sb.append("C_ACC_SALE_REVENUE_PM NUMERIC(14, 4),\n");
        sb.append("C_ACC_PROFIT_PM NUMERIC(14, 4),\n");
        sb.append("C_PAY_FREE NUMERIC(14, 2),\n");
        sb.append("C_ACC_PAY_FREE NUMERIC(14, 2),\n");
        sb.append("C_PAY_FREE_DQ NUMERIC(14, 2),\n");
        sb.append("C_ACC_PAY_FREE_DQ NUMERIC(14, 2),\n");
        sb.append("C_ORD INT)\n\n");

        // ===== 第一次调用 up_GetFine_Run =====
        sb.append("DECLARE @var__where  VARCHAR(4000)\n");
        sb.append("DECLARE @var__value  VARCHAR(4000)\n");
        sb.append("SET @var__where =  'C_CHANNEL;C_GDS_BCODE'\n");
        sb.append("SET @var__value =  @渠道+';'+@品牌\n");
        sb.append("IF @日期类型 = '记账日期'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__cols = CONCAT(@var__显示日期,@var__显示门店,@var__部门维度,@var__品类维度,@var__显示品牌,'C_ACC_SALE_QTY;C_ACC_SALE_QTY_FPL;C_ACC_SALE_REVENUE;C_ACC_WSALE_REVENUE;C_ACC_NET_SALE_REVENUE;C_ACC_AT_COST;C_ACC_AET_COST;C_ACC_DISC_VALUE;C_ACC_AT_DISC;C_ACC_PROFIT;C_ACC_WSALE_PROFIT;C_ACC_NET_PROFIT;C_ACC_SALE_MEM;C_ACC_NET_SALE_MEM;C_ACC_SALE_REVENUE_PRO;C_ACC_NET_SALE_REVENUE_PRO;C_ACC_AT_COST_PRO;C_ACC_AET_COST_PRO;C_ACC_PROFIT_PRO;C_ACC_NET_PROFIT_PRO;C_ACC_SALE_REVENUE_DQ;C_ACC_WSALE_REVENUE_DQ;C_ACC_NET_SALE_REVENUE_DQ;C_ACC_AT_COST_DQ;C_ACC_AET_COST_DQ;C_ACC_DISC_VALUE_DQ;C_ACC_AT_DISC_DQ;C_ACC_PROFIT_DQ;C_ACC_NET_PROFIT_DQ;C_ACC_SALE_MEM_DQ;C_ACC_NET_SALE_MEM_DQ;C_ACC_SALE_REVENUE_PRO_DQ;C_ACC_NET_SALE_REVENUE_PRO_DQ;C_ACC_AT_COST_PRO_DQ;C_ACC_AET_COST_PRO_DQ;C_ACC_PROFIT_PRO_DQ;C_ACC_WSALE_PROFIT_DQ;C_ACC_NET_PROFIT_PRO_DQ;C_ACC_SALE_REVENUE_DBC;C_ACC_PROFIT_DBC;C_ACC_SALE_REVENUE_PM;C_ACC_PROFIT_PM;C_ACC_PAY_FREE;C_ACC_PAY_FREE_DQ')\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__cols = CONCAT(@var__显示日期,@var__显示门店,@var__部门维度,@var__品类维度,@var__显示品牌,'C_SALE_QTY;C_SALE_QTY_FPL;C_SALE_REVENUE;C_WSALE_REVENUE;C_NET_SALE_REVENUE;C_AT_COST;C_AET_COST;C_DISC_VALUE;C_AT_DISC;C_PROFIT;C_WSALE_PROFIT;C_NET_PROFIT;C_SALE_MEM;C_NET_SALE_MEM;C_SALE_REVENUE_PRO;C_NET_SALE_REVENUE_PRO;C_AT_COST_PRO;C_AET_COST_PRO;C_PROFIT_PRO;C_NET_PROFIT_PRO;C_SALE_REVENUE_DQ;C_WSALE_REVENUE_DQ;C_NET_SALE_REVENUE_DQ;C_AT_COST_DQ;C_AET_COST_DQ;C_DISC_VALUE_DQ;C_AT_DISC_DQ;C_PROFIT_DQ;C_NET_PROFIT_DQ;C_SALE_MEM_DQ;C_NET_SALE_MEM_DQ;C_SALE_REVENUE_PRO_DQ;C_NET_SALE_REVENUE_PRO_DQ;C_AT_COST_PRO_DQ;C_AET_COST_PRO_DQ;C_PROFIT_PRO_DQ;C_WSALE_PROFIT_DQ;C_NET_PROFIT_PRO_DQ;C_SALE_REVENUE_DBC;C_PROFIT_DBC;C_SALE_REVENUE_PM;C_PROFIT_PM;C_PAY_FREE;C_PAY_FREE_DQ')\n");
        sb.append("END\n");
        sb.append("EXEC [dm].[up_GetFine_Run]\n");
        sb.append("     @tenantid,\n");
        sb.append("     @lang,\n");
        sb.append("     '',\n");
        sb.append("     'REP_SALE_PRO',\n");
        sb.append("     @userNo,\n");
        sb.append("     @查询日期__sdt,\n");
        sb.append("     @查询日期__edt,\n");
        sb.append("     @对比日期__sdt,\n");
        sb.append("     @对比日期__edt,\n");
        sb.append("     @var__cols,\n");
        sb.append("     @机构,\n");
        sb.append("     @部门,\n");
        sb.append("     @品类,\n");
        sb.append("     '',\n");
        sb.append("     @var__where,\n");
        sb.append("     @var__value,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     'N',\n");
        sb.append("     '',\n");
        sb.append("     @可比门店\n\n");

        // ===== UPDATE #TEMP_REP_SALE_PRO =====
        sb.append("IF @日期类型 = '记账日期'\n");
        sb.append("BEGIN\n");
        sb.append("    UPDATE #TEMP_REP_SALE_PRO SET\n");
        sb.append("            C_FSDATE = C_HSDATE,\n");
        sb.append("            C_SALE_QTY = C_ACC_SALE_QTY,\n");
        sb.append("            C_SALE_QTY_FPL = C_ACC_SALE_QTY_FPL,\n");
        sb.append("            C_SALE_REVENUE = C_ACC_SALE_REVENUE,\n");
        sb.append("            C_WSALE_REVENUE = C_ACC_WSALE_REVENUE,\n");
        sb.append("            C_NET_SALE_REVENUE = C_ACC_NET_SALE_REVENUE,\n");
        sb.append("            C_AT_COST =  C_ACC_AT_COST,\n");
        sb.append("            C_AET_COST = C_ACC_AET_COST,\n");
        sb.append("            C_DISC_VALUE =   C_ACC_DISC_VALUE,\n");
        sb.append("            C_AT_DISC =  C_ACC_AT_DISC,\n");
        sb.append("            C_PROFIT =   C_ACC_PROFIT,\n");
        sb.append("            C_WSALE_PROFIT = C_ACC_WSALE_PROFIT,\n");
        sb.append("            C_NET_PROFIT =   C_ACC_NET_PROFIT,\n");
        sb.append("            C_SALE_MEM = C_ACC_SALE_MEM,\n");
        sb.append("            C_NET_SALE_MEM = C_ACC_NET_SALE_MEM,\n");
        sb.append("            C_SALE_MEM_RATE = C_ACC_SALE_MEM_RATE,\n");
        sb.append("            C_SALE_REVENUE_PRO = C_ACC_SALE_REVENUE_PRO,\n");
        sb.append("            C_NET_SALE_REVENUE_PRO = C_ACC_NET_SALE_REVENUE_PRO,\n");
        sb.append("            C_AT_COST_PRO =  C_ACC_AT_COST_PRO,\n");
        sb.append("            C_AET_COST_PRO = C_ACC_AET_COST_PRO,\n");
        sb.append("            C_PROFIT_PRO =   C_ACC_PROFIT_PRO,\n");
        sb.append("            C_NET_PROFIT_PRO =   C_ACC_NET_PROFIT_PRO,\n");
        sb.append("            C_SALE_REVENUE_PRO_RATE = C_ACC_SALE_REVENUE_PRO_RATE,\n");
        sb.append("            C_PROFIT_PRO_RATE =  C_ACC_PROFIT_PRO_RATE,\n");
        sb.append("            C_SALE_REVENUE_DQ =  C_ACC_SALE_REVENUE_DQ,\n");
        sb.append("            C_WSALE_REVENUE_DQ = C_ACC_WSALE_REVENUE_DQ,\n");
        sb.append("            C_NET_SALE_REVENUE_DQ =  C_ACC_NET_SALE_REVENUE_DQ,\n");
        sb.append("            C_AT_COST_DQ =   C_ACC_AT_COST_DQ,\n");
        sb.append("            C_AET_COST_DQ =  C_ACC_AET_COST_DQ,\n");
        sb.append("            C_DISC_VALUE_DQ = C_ACC_DISC_VALUE_DQ,\n");
        sb.append("            C_AT_DISC_DQ =   C_ACC_AT_DISC_DQ,\n");
        sb.append("            C_PROFIT_DQ = C_ACC_PROFIT_DQ,\n");
        sb.append("            C_WSALE_PROFIT_DQ =  C_ACC_WSALE_PROFIT_DQ,\n");
        sb.append("            C_NET_PROFIT_DQ = C_ACC_NET_PROFIT_DQ,\n");
        sb.append("            C_SALE_MEM_DQ =  C_ACC_SALE_MEM_DQ,\n");
        sb.append("            C_NET_SALE_MEM_DQ =  C_ACC_NET_SALE_MEM_DQ,\n");
        sb.append("            C_SALE_MEM_RATE_DQ = C_ACC_SALE_MEM_RATE_DQ,\n");
        sb.append("            C_SALE_REVENUE_PRO_DQ =  C_ACC_SALE_REVENUE_PRO_DQ,\n");
        sb.append("            C_NET_SALE_REVENUE_PRO_DQ =  C_ACC_NET_SALE_REVENUE_PRO_DQ,\n");
        sb.append("            C_AT_COST_PRO_DQ =   C_ACC_AT_COST_PRO_DQ,\n");
        sb.append("            C_AET_COST_PRO_DQ =  C_ACC_AET_COST_PRO_DQ,\n");
        sb.append("            C_PROFIT_PRO_DQ = C_ACC_PROFIT_PRO_DQ,\n");
        sb.append("            C_NET_PROFIT_PRO_DQ = C_ACC_NET_PROFIT_PRO_DQ,\n");
        sb.append("            C_SALE_REVENUE_PRO_RATE_DQ = C_ACC_SALE_REVENUE_PRO_RATE_DQ,\n");
        sb.append("            C_PROFIT_PRO_RATE_DQ =   C_ACC_PROFIT_PRO_RATE_DQ,\n");
        sb.append("            C_SALE_REVENUE_DBC =  C_ACC_SALE_REVENUE_DBC,\n");
        sb.append("            C_PROFIT_DBC = C_ACC_PROFIT_DBC,\n");
        sb.append("            C_SALE_REVENUE_PM =   C_ACC_SALE_REVENUE_PM,\n");
        sb.append("            C_PROFIT_PM = C_ACC_PROFIT_PM,\n");
        sb.append("            C_PAY_FREE = C_ACC_PAY_FREE,\n");
        sb.append("            C_PAY_FREE_DQ = C_ACC_PAY_FREE_DQ\n");
        sb.append("END\n\n");

        // ===== 第二次调用 up_GetFine_Run (REP_TRADE) =====
        sb.append("DECLARE @VAR__Error           VARCHAR(500)\n");
        sb.append("DECLARE @var__max_dept_level VARCHAR(100),@var__max_cat_level VARCHAR(100)\n");
        sb.append("SELECT @var__max_dept_level = MAX(c_level) FROM #ttb_dept_level\n");
        sb.append("SELECT @var__max_cat_level = MAX(c_level) FROM #ttb_cat_level\n\n");

        sb.append("IF ISNULL(@var__max_dept_level, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__部门维度 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@var__max_dept_level, '') = '9'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__部门维度 = 'C_ADNO;'\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__部门维度 = CONCAT('C_ADNO', @var__max_dept_level, ';')\n");
        sb.append("END\n\n");

        sb.append("IF ISNULL(@var__max_cat_level, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__品类维度 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@var__max_cat_level, '') = '9'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__品类维度 = 'C_CCODE;'\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__品类维度 = CONCAT('C_CCODE', @var__max_cat_level, ';')\n");
        sb.append("END\n\n");

        sb.append("IF ISNULL(@是否显示门店, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示门店 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@是否显示门店, '') = '显示门店'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示门店 = 'C_STORE_ID;'\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示门店 = ''\n");
        sb.append("END\n\n");

        sb.append("IF ISNULL(@是否显示品牌, '') = ''\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = ''\n");
        sb.append("END\n");
        sb.append("ELSE IF ISNULL(@是否显示品牌, '') = '显示品牌'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = 'C_BCODE;'\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示品牌 = ''\n");
        sb.append("END\n\n");

        sb.append("IF @var__显示日期 = 'C_HSDATE;'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__显示日期 = 'C_FSDATE;'\n");
        sb.append("END\n\n");

        sb.append("IF @var__部门维度 <> '' AND @var__品类维度 <> '' OR @var__显示品牌 <> '' OR IIF(@是否展示计划='','否',@是否展示计划)='否'\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__cols = CONCAT(@var__显示日期,@var__显示门店,@var__部门维度,@var__品类维度,@var__显示品牌,'C_COUNT_TRADE;C_COUNT_TRADE_DQ;C_COUNT_MEM;C_COUNT_MEM_DQ')\n");
        sb.append("END\n");
        sb.append("ELSE\n");
        sb.append("BEGIN\n");
        sb.append("    SET @var__cols = CONCAT(@var__显示日期,@var__显示门店,@var__部门维度,@var__品类维度,@var__显示品牌,'C_COUNT_TRADE;C_COUNT_TRADE_DQ;C_COUNT_MEM;C_COUNT_MEM_DQ;C_PLAN_SALE;C_PLAN_PROFIT')\n");
        sb.append("END\n\n");

        sb.append("IF OBJECT_ID('tempdb..#TEMP_REP_TRADE') IS NOT NULL DROP TABLE #TEMP_REP_TRADE\n");
        sb.append("CREATE TABLE #TEMP_REP_TRADE (\n");
        sb.append("C_FSDATE      DATE,\n");
        sb.append("C_STORE_ID    VARCHAR(200),\n");
        sb.append("C_STORE_NAME    VARCHAR(200),\n");
        sb.append("C_ADNO       VARCHAR(200),\n");
        sb.append("C_ADNO1        VARCHAR(63),\n");
        sb.append("C_ADNO2        VARCHAR(63),\n");
        sb.append("C_ADNO3        VARCHAR(63),\n");
        sb.append("C_ADNO4        VARCHAR(63),\n");
        sb.append("C_ADNO5        VARCHAR(63),\n");
        sb.append("C_ADNO6        VARCHAR(63),\n");
        sb.append("C_ADNO7        VARCHAR(63),\n");
        sb.append("C_ADNO8        VARCHAR(63),\n");
        sb.append("C_DEPART_NAME    VARCHAR(200),\n");
        sb.append("C_CCODE       VARCHAR(63),\n");
        sb.append("C_CCODE1       VARCHAR(63),\n");
        sb.append("C_CCODE2       VARCHAR(63),\n");
        sb.append("C_CCODE3       VARCHAR(63),\n");
        sb.append("C_CCODE4       VARCHAR(63),\n");
        sb.append("C_CCODE5       VARCHAR(63),\n");
        sb.append("C_CCODE6       VARCHAR(63),\n");
        sb.append("C_CCODE7       VARCHAR(63),\n");
        sb.append("C_CCODE8       VARCHAR(63),\n");
        sb.append("C_CATNAME     VARCHAR(63),\n");
        sb.append("C_BCODE    VARCHAR(63),\n");
        sb.append("C_COUNT_TRADE NUMERIC(14, 4),\n");
        sb.append("C_COUNT_TRADE_DQ NUMERIC(14, 4),\n");
        sb.append("C_COUNT_MEM NUMERIC(14, 4),\n");
        sb.append("C_COUNT_MEM_DQ NUMERIC(14, 4),\n");
        sb.append("C_PLAN_SALE NUMERIC(14, 4),\n");
        sb.append("C_PLAN_PROFIT NUMERIC(14, 4),\n");
        sb.append("C_ORD INT)\n\n");

        sb.append("SET @var__where =  'C_CHANNEL;C_BCODE'\n\n");

        sb.append("EXEC [dm].[up_GetFine_Run]\n");
        sb.append("     @tenantid,\n");
        sb.append("     @lang,\n");
        sb.append("     '',\n");
        sb.append("     'REP_TRADE',\n");
        sb.append("     @userNo,\n");
        sb.append("     @查询日期__sdt,\n");
        sb.append("     @查询日期__edt,\n");
        sb.append("     @对比日期__sdt,\n");
        sb.append("     @对比日期__edt,\n");
        sb.append("     @var__cols,\n");
        sb.append("     @机构,\n");
        sb.append("     @部门,\n");
        sb.append("     @品类,\n");
        sb.append("     '',\n");
        sb.append("     @var__where,\n");
        sb.append("     @var__value,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     NULL,\n");
        sb.append("     'N',\n");
        sb.append("     '',\n");
        sb.append("     @可比门店\n\n");

        sb.append("DECLARE @var__sql NVARCHAR(4000)\n");
        sb.append("DECLARE @VAR__ROWNO int\n");
        sb.append("DECLARE @var__level VARCHAR(100)\n\n");

        sb.append("IF @var__cnt_dept > 0\n");
        sb.append("EXEC up_rep_cover_dept_level @var__cnt_dept,@var__max_dept_level,'#TEMP_REP_TRADE'\n\n");

        sb.append("IF @var__cnt_cat > 0\n");
        sb.append("EXEC up_rep_cover_cat_level @var__cnt_cat,@var__max_cat_level,'#TEMP_REP_TRADE'\n\n");

        // ===== 最终 SELECT =====
        sb.append("/*----++++*/\n\n");

        sb.append("SELECT  A.C_FSDATE AS 日期,\n");
        sb.append("        A.C_STORE_ID AS 机构编码,\n");
        sb.append("        A.C_STORE_NAME AS 机构名称,\n");
        sb.append("        A.C_ADNO AS 部门编码,\n");
        sb.append("        A.C_DEPTID1 AS 部门编码1,\n");
        sb.append("        A.C_DEPTID2 AS 部门编码2,\n");
        sb.append("        A.C_DEPTID3 AS 部门编码3,\n");
        sb.append("        A.C_DEPTID4 AS 部门编码4,\n");
        sb.append("        A.C_DEPTID5 AS 部门编码5,\n");
        sb.append("        A.C_DEPTID6 AS 部门编码6,\n");
        sb.append("        A.C_DEPTID7 AS 部门编码7,\n");
        sb.append("        A.C_DEPTID8 AS 部门编码8,\n");
        sb.append("        A.C_DEPTNAME1  AS 部门名称1,\n");
        sb.append("        A.C_DEPTNAME2  AS 部门名称2,\n");
        sb.append("        A.C_DEPTNAME3  AS 部门名称3,\n");
        sb.append("        A.C_DEPTNAME4  AS 部门名称4,\n");
        sb.append("        A.C_DEPTNAME5  AS 部门名称5,\n");
        sb.append("        A.C_DEPTNAME6  AS 部门名称6,\n");
        sb.append("        A.C_DEPTNAME7  AS 部门名称7,\n");
        sb.append("        A.C_DEPTNAME8  AS 部门名称8,\n");
        sb.append("        A.C_DEPART_NAME AS 部门名称,\n");
        sb.append("        A.C_CCODE AS 品类编码,\n");
        sb.append("        A.C_CATID1 AS 品类编码1,\n");
        sb.append("        A.C_CATID2 AS 品类编码2,\n");
        sb.append("        A.C_CATID3 AS 品类编码3,\n");
        sb.append("        A.C_CATID4 AS 品类编码4,\n");
        sb.append("        A.C_CATID5 AS 品类编码5,\n");
        sb.append("        A.C_CATID6 AS 品类编码6,\n");
        sb.append("        A.C_CATID7 AS 品类编码7,\n");
        sb.append("        A.C_CATID8 AS 品类编码8,\n");
        sb.append("        A.C_CATNAME1 AS 品类名称1,\n");
        sb.append("        A.C_CATNAME2 AS 品类名称2,\n");
        sb.append("        A.C_CATNAME3 AS 品类名称3,\n");
        sb.append("        A.C_CATNAME4 AS 品类名称4,\n");
        sb.append("        A.C_CATNAME5 AS 品类名称5,\n");
        sb.append("        A.C_CATNAME6 AS 品类名称6,\n");
        sb.append("        A.C_CATNAME7 AS 品类名称7,\n");
        sb.append("        A.C_CATNAME8 AS 品类名称8,\n");
        sb.append("        A.C_CATNAME AS 品类名称,\n");
        sb.append("        A.C_GDS_BCODE AS 品牌编码,\n");
        sb.append("        IIF(@lang='L', PP.c_name, PP.c_name_en) AS 品牌名称,\n");
        sb.append("        A.C_SALE_QTY AS 销售数量,\n");
        sb.append("        A.C_SALE_QTY_FPL AS 生鲜盘点损溢数量,\n");
        sb.append("        A.C_SALE_REVENUE AS 销售金额,\n");
        sb.append("        A.C_NET_SALE_REVENUE AS 不含税销售额,\n");
        sb.append("        A.C_AT_COST AS 含税成本,\n");
        sb.append("        A.C_AET_COST AS 不含税成本,\n");
        sb.append("        A.C_DISC_VALUE AS 总折扣额,\n");
        sb.append("        A.C_AT_DISC AS 供应商折扣承担金额,\n");
        sb.append("        A.C_PROFIT AS 含税毛利,\n");
        sb.append("        A.C_NET_PROFIT AS 不含税毛利,\n");
        sb.append("        ROUND(A.C_PROFIT/IIF(A.C_SALE_REVENUE=0,NULL,A.C_SALE_REVENUE),4)*100 AS 毛利率,\n");
        sb.append("        A.C_SALE_REVENUE - A.C_PAY_FREE AS 销售净额,\n");
        sb.append("        A.C_PROFIT - A.C_PAY_FREE AS 净毛利额,\n");
        sb.append("        ROUND((A.C_PROFIT - A.C_PAY_FREE)/IIF((A.C_SALE_REVENUE - A.C_PAY_FREE)=0,NULL,A.C_SALE_REVENUE - A.C_PAY_FREE),4)*100 AS 净毛利率,\n");
        sb.append("        A.C_WSALE_REVENUE AS 批发销售金额,\n");
        sb.append("        ROUND(A.C_WSALE_REVENUE/IIF(A.C_SALE_REVENUE=0,NULL,A.C_SALE_REVENUE),4)*100 AS 批发销售占比,\n");
        sb.append("        A.C_WSALE_PROFIT AS 批发销售毛利,\n");
        sb.append("        ROUND(A.C_WSALE_PROFIT/IIF(A.C_PROFIT=0,NULL,A.C_PROFIT),4)*100 AS 批发毛利占比,\n");
        sb.append("        A.C_SALE_MEM AS 含税会员销售,\n");
        sb.append("        A.C_NET_SALE_MEM AS 不含税会员销售,\n");
        sb.append("        ROUND(A.C_SALE_MEM/IIF(A.C_SALE_REVENUE=0,NULL,A.C_SALE_REVENUE),4)*100 AS 会员销售占比,\n");
        sb.append("        A.C_SALE_REVENUE_PRO AS 促销销售,\n");
        sb.append("        A.C_NET_SALE_REVENUE_PRO AS 不含税促销销售,\n");
        sb.append("        A.C_AT_COST_PRO AS 含税促销成本,\n");
        sb.append("        A.C_AET_COST_PRO AS 不含税促销成本,\n");
        sb.append("        A.C_PROFIT_PRO AS 促销毛利,\n");
        sb.append("        A.C_NET_PROFIT_PRO AS 不含税促销毛利,\n");
        sb.append("        ROUND(A.C_SALE_REVENUE_PRO/IIF(A.C_SALE_REVENUE=0,NULL,A.C_SALE_REVENUE),4)*100 AS 促销销售占比,\n");
        sb.append("        ROUND(A.C_PROFIT_PRO/IIF(A.C_PROFIT=0,NULL,A.C_PROFIT),4)*100 AS 促销毛利占比,\n");
        sb.append("        ISNULL(B.C_COUNT_TRADE,0) AS 交易笔数,\n");
        sb.append("        ROUND(A.C_SALE_REVENUE/IIF(B.C_COUNT_TRADE=0,NULL,B.C_COUNT_TRADE),2) AS 客单价,\n");
        sb.append("        ISNULL(B.C_COUNT_MEM,0) AS 会员交易笔数,\n");
        sb.append("        ROUND(A.C_SALE_MEM/IIF(B.C_COUNT_MEM=0,NULL,B.C_COUNT_MEM),2) AS 会员客单价,\n");
        sb.append("        ROUND(B.C_COUNT_MEM/IIF(B.C_COUNT_TRADE=0,NULL,B.C_COUNT_TRADE),4)*100 AS 会员交易占比,\n");
        sb.append("        B.C_PLAN_SALE AS 销售计划,\n");
        sb.append("        ROUND(A.C_SALE_REVENUE/IIF(B.C_PLAN_SALE=0,NULL,B.C_PLAN_SALE),4)*100 AS 销售计划达成率,\n");
        sb.append("        B.C_PLAN_PROFIT AS 毛利计划,\n");
        sb.append("        ROUND(A.C_PROFIT/IIF(B.C_PLAN_PROFIT=0,NULL,B.C_PLAN_PROFIT),4)*100 AS 毛利计划达成率,\n");
        sb.append("        A.C_SALE_REVENUE_DQ  AS 对期销售金额,\n");
        sb.append("        A.C_NET_SALE_REVENUE_DQ  AS 对期不含税销售额,\n");
        sb.append("        A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ AS 对期销售净额,\n");
        sb.append("        A.C_AT_COST_DQ  AS 对期含税成本,\n");
        sb.append("        A.C_AET_COST_DQ  AS 对期不含税成本,\n");
        sb.append("        A.C_DISC_VALUE_DQ  AS 对期总折扣额,\n");
        sb.append("        A.C_AT_DISC_DQ  AS 对期供应商折扣承担金额,\n");
        sb.append("        A.C_PROFIT_DQ  AS 对期含税毛利,\n");
        sb.append("        A.C_NET_PROFIT_DQ  AS 对期不含税毛利,\n");
        sb.append("        A.C_PROFIT_DQ - A.C_PAY_FREE_DQ AS 对期净毛利额,\n");
        sb.append("        A.C_WSALE_REVENUE_DQ AS 对期批发销售金额,\n");
        sb.append("        ROUND(A.C_WSALE_REVENUE_DQ/IIF(A.C_SALE_REVENUE_DQ=0,NULL,A.C_SALE_REVENUE_DQ),4)*100 AS 对期批发销售占比,\n");
        sb.append("        A.C_WSALE_PROFIT_DQ AS 对期批发销售毛利,\n");
        sb.append("        ROUND(A.C_WSALE_PROFIT_DQ/IIF(A.C_PROFIT_DQ=0,NULL,A.C_PROFIT_DQ),4)*100 AS 对期批发毛利占比,\n");
        sb.append("        A.C_SALE_MEM_DQ  AS 对期含税会员销售,\n");
        sb.append("        A.C_NET_SALE_MEM_DQ  AS 对期不含税会员销售,\n");
        sb.append("        ROUND(A.C_SALE_MEM_DQ/IIF(A.C_SALE_REVENUE_DQ=0,NULL,A.C_SALE_REVENUE_DQ),4)*100 AS 对期会员销售占比,\n");
        sb.append("        A.C_SALE_REVENUE_PRO_DQ  AS 对期促销销售,\n");
        sb.append("        A.C_NET_SALE_REVENUE_PRO_DQ  AS 对期不含税促销销售,\n");
        sb.append("        A.C_AT_COST_PRO_DQ  AS 对期含税促销成本,\n");
        sb.append("        A.C_AET_COST_PRO_DQ  AS 对期不含税促销成本,\n");
        sb.append("        A.C_PROFIT_PRO_DQ  AS 对期促销毛利,\n");
        sb.append("        A.C_NET_PROFIT_PRO_DQ  AS 对期不含税促销毛利,\n");
        sb.append("        ROUND(A.C_PROFIT_DQ/IIF(A.C_SALE_REVENUE_DQ=0,NULL,A.C_SALE_REVENUE_DQ),4)*100 AS 对期毛利率,\n");
        sb.append("        ROUND((A.C_PROFIT_DQ - A.C_PAY_FREE_DQ)/IIF((A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ)=0,NULL,A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ),4)*100 AS 对期净毛利率,\n");
        sb.append("        ROUND(A.C_SALE_REVENUE_PRO_DQ/IIF(A.C_SALE_REVENUE_DQ=0,NULL,A.C_SALE_REVENUE_DQ),4)*100 AS 对期促销销售占比,\n");
        sb.append("        ROUND(A.C_PROFIT_PRO_DQ/IIF(A.C_PROFIT_DQ=0,NULL,A.C_PROFIT_DQ),4)*100 AS 对期促销毛利占比,\n");
        sb.append("        ISNULL(B.C_COUNT_TRADE_DQ,0) AS 对期交易笔数,\n");
        sb.append("        ROUND(A.C_SALE_REVENUE_DQ/IIF(B.C_COUNT_TRADE_DQ=0,NULL,B.C_COUNT_TRADE_DQ),2) AS 对期客单价,\n");
        sb.append("        ISNULL(B.C_COUNT_MEM_DQ,0) AS 对期会员交易笔数,\n");
        sb.append("        ROUND(A.C_SALE_MEM_DQ/IIF(B.C_COUNT_MEM_DQ=0,NULL,B.C_COUNT_MEM_DQ),2) AS 对期会员客单价,\n");
        sb.append("        ROUND(B.C_COUNT_MEM_DQ/IIF(B.C_COUNT_TRADE_DQ=0,NULL,B.C_COUNT_TRADE_DQ),4)*100 AS 对期会员交易占比,\n");
        sb.append("        A.C_SALE_REVENUE_DBC*100 AS 销售额增长率,\n");
        sb.append("        A.C_PROFIT_DBC*100 AS 毛利额增长率,\n");
        sb.append("       ROUND(A.C_PROFIT/IIF(A.C_SALE_REVENUE=0,NULL,A.C_SALE_REVENUE),4)*100 -\n");
        sb.append("       ROUND(A.C_PROFIT_DQ/IIF(A.C_SALE_REVENUE_DQ=0,NULL,A.C_SALE_REVENUE_DQ),4)*100 AS 毛利率对比增长,\n");
        sb.append("       ROUND((A.C_SALE_REVENUE - A.C_PAY_FREE)/IIF((A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ)=0,NULL,A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ),4)*100 -1 AS 销售净额对比增长,\n");
        sb.append("       ROUND((A.C_PROFIT - A.C_PAY_FREE)/IIF((A.C_PROFIT_DQ - A.C_PAY_FREE_DQ)=0,NULL,A.C_PROFIT_DQ - A.C_PAY_FREE_DQ),4)*100 -1 AS 净毛利额对比增长,\n");
        sb.append("       ROUND((A.C_PROFIT - A.C_PAY_FREE)/IIF((A.C_SALE_REVENUE - A.C_PAY_FREE)=0,NULL,A.C_SALE_REVENUE - A.C_PAY_FREE),4)*100 -\n");
        sb.append("       ROUND((A.C_PROFIT_DQ - A.C_PAY_FREE_DQ)/IIF((A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ)=0,NULL,A.C_SALE_REVENUE_DQ - A.C_PAY_FREE_DQ),4)*100 AS 净毛利率对比增长,\n");
        sb.append("        A.C_SALE_REVENUE_PM AS 销售额排名,\n");
        sb.append("        A.C_PROFIT_PM AS 毛利额排名\n");
        sb.append("   FROM #TEMP_REP_SALE_PRO A\n");
        sb.append("   LEFT JOIN TB_MD_BRAND (NOLOCK) PP ON ISNULL(A.C_GDS_BCODE,'') = PP.C_BCODE AND C_TENANT_ID = @tenantid\n");
        sb.append("   LEFT JOIN #TEMP_REP_TRADE B ON ISNULL(A.C_STORE_ID,'') = ISNULL(B.C_STORE_ID,'')\n");
        sb.append("                              AND ISNULL(A.C_FSDATE,'') = ISNULL(B.C_FSDATE,'')\n");
        sb.append("                              AND ISNULL(A.C_ADNO,'') = ISNULL(B.C_ADNO,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID1,'') = ISNULL(B.C_ADNO1,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID2,'') = ISNULL(B.C_ADNO2,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID3,'') = ISNULL(B.C_ADNO3,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID4,'') = ISNULL(B.C_ADNO4,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID5,'') = ISNULL(B.C_ADNO5,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID6,'') = ISNULL(B.C_ADNO6,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID7,'') = ISNULL(B.C_ADNO7,'')\n");
        sb.append("                              AND ISNULL(A.C_DEPTID8,'') = ISNULL(B.C_ADNO8,'')\n");
        sb.append("                              AND ISNULL(A.C_CCODE,'') = ISNULL(B.C_CCODE,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID1,'') = ISNULL(B.C_CCODE1,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID2,'') = ISNULL(B.C_CCODE2,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID3,'') = ISNULL(B.C_CCODE3,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID4,'') = ISNULL(B.C_CCODE4,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID5,'') = ISNULL(B.C_CCODE5,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID6,'') = ISNULL(B.C_CCODE6,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID7,'') = ISNULL(B.C_CCODE7,'')\n");
        sb.append("                              AND ISNULL(A.C_CATID8,'') = ISNULL(B.C_CCODE8,'')\n");
        sb.append("                              AND ISNULL(A.C_GDS_BCODE,'') = ISNULL(B.C_BCODE,'')\n");
        sb.append("ORDER BY A.C_FSDATE,\n");
        sb.append("        A.C_STORE_ID,\n");
        sb.append("        A.C_DEPTID1,\n");
        sb.append("        A.C_DEPTID2,\n");
        sb.append("        A.C_DEPTID3,\n");
        sb.append("        A.C_DEPTID4,\n");
        sb.append("        A.C_DEPTID5,\n");
        sb.append("        A.C_DEPTID6,\n");
        sb.append("        A.C_DEPTID7,\n");
        sb.append("        A.C_DEPTID8,\n");
        sb.append("        A.C_ADNO,\n");
        sb.append("        A.C_CATID1,\n");
        sb.append("        A.C_CATID2,\n");
        sb.append("        A.C_CATID3,\n");
        sb.append("        A.C_CATID4,\n");
        sb.append("        A.C_CATID5,\n");
        sb.append("        A.C_CATID6,\n");
        sb.append("        A.C_CATID7,\n");
        sb.append("        A.C_CATID8,\n");
        sb.append("        A.C_CCODE\n");

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
