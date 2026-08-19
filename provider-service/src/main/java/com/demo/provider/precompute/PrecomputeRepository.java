package com.demo.provider.precompute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 销售报表预计算表数据访问层（RDS_SC · dw schema）
 *
 * 🔴 安全红线（用户反复强调）：
 *   - 本类只读写 dw. 自有对象：dw.rpt_sale_detail_precompute / dw.etl_batch_log
 *   - 幂等删除 DELETE 仅限预计算表自身，且必须带 query_date + report_type + comparison_type 条件
 *   - 绝不 DELETE / UPDATE / DDL 任何生产表（dbo / dm / mem / tb_* 等）
 *
 * 关键点（销售报表2 预计算 2026-08-19）：
 *   - org_code 列存**行内机构编码**（报表2 多机构整串一次调用，行自带机构编码，查询按 IN 过滤）；
 *     报表1 单行单列，org_code 与传入一致，取行内值无副作用。
 *   - 删除分两套：报表1 带 org_code（多机构各自幂等）；报表2 按 dept_levels=0 维度（与报表1 的 3/2/1 天然隔离）。
 *   - 查询 org_code 用 IN 子句（支持报表2 多机构子集、报表1 单机构）。
 */
@Repository
public class PrecomputeRepository {

    private static final Logger log = LoggerFactory.getLogger(PrecomputeRepository.class);

    @Autowired
    @Qualifier("scDataSource")
    private DataSource scDataSource;

    /** 幂等删除（报表1）：仅删预计算表自身、带全条件（MOM 失败不影响 YOY；多机构互不影响） */
    private static final String DELETE_SQL_R1 =
            "DELETE FROM dw.rpt_sale_detail_precompute " +
            "WHERE query_date = ? AND report_type = ? AND org_code = ? AND comparison_type = ? AND dept_levels = ?";

    /** 幂等删除（报表2）：多机构整体串一次调用，行内 org_code 各异，故按 dept_levels=0 维度整批删除 */
    private static final String DELETE_SQL_R2 =
            "DELETE FROM dw.rpt_sale_detail_precompute " +
            "WHERE query_date = ? AND report_type = ? AND comparison_type = ? AND dept_levels = ?";

    /** INSERT（207 列，id 自增跳过；列名由 COLS 常量拼接，与建表 SQL 顺序一致） */
    private static final String INSERT_SQL = buildInsertSql();

    /** 日志表 INSERT */
    private static final String INSERT_LOG_SQL =
            "INSERT INTO dw.etl_batch_log " +
            "(batch_id, etl_time, query_date, comparison_type, org_code, report_type, row_count, status, message, trigger_type) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** 最近跑批记录查询（按 report_type 过滤） */
    private static final String LAST_LOG_SQL =
            "SELECT TOP (?) id, batch_id, etl_time, query_date, comparison_type, org_code, report_type, row_count, status, message, trigger_type " +
            "FROM dw.etl_batch_log WHERE report_type = ? ORDER BY id DESC";

    /**
     * ===== 统一查询（模板，org_code 用 __ORGS__ 占位，运行时替换为 IN (?,?,...)）=====
     * 三档/报表2 均为凌晨引擎直查结果落库，查询零聚合、零重算，与引擎 100% 一致。
     */
    private static final String SQL_QUERY_TEMPLATE =
            "SELECT " +
            "fs_date AS 日期, store_id AS 机构编码, store_name AS 机构名称, adno AS 部门编码, " +
            "dept_id1 AS 部门编码1, dept_id2 AS 部门编码2, dept_id3 AS 部门编码3, " +
            "dept_id4 AS 部门编码4, dept_id5 AS 部门编码5, dept_id6 AS 部门编码6, " +
            "dept_id7 AS 部门编码7, dept_id8 AS 部门编码8, " +
            "dept_name1 AS 部门名称1, dept_name2 AS 部门名称2, dept_name3 AS 部门名称3, " +
            "dept_name4 AS 部门名称4, dept_name5 AS 部门名称5, dept_name6 AS 部门名称6, " +
            "dept_name7 AS 部门名称7, dept_name8 AS 部门名称8, depart_name AS 部门名称, " +
            "ccode AS 品类编码, cat_id1 AS 品类编码1, cat_id2 AS 品类编码2, cat_id3 AS 品类编码3, " +
            "cat_id4 AS 品类编码4, cat_id5 AS 品类编码5, cat_id6 AS 品类编码6, " +
            "cat_id7 AS 品类编码7, cat_id8 AS 品类编码8, " +
            "cat_name1 AS 品类名称1, cat_name2 AS 品类名称2, cat_name3 AS 品类名称3, " +
            "cat_name4 AS 品类名称4, cat_name5 AS 品类名称5, cat_name6 AS 品类名称6, " +
            "cat_name7 AS 品类名称7, cat_name8 AS 品类名称8, cat_name AS 品类名称, " +
            "gds_bcode AS 品牌编码, brand_name AS 品牌名称, " +
            "sale_qty AS 销售数量, sale_qty_fpl AS 生鲜盘点损溢数量, sale_revenue AS 销售金额, " +
            "net_sale_revenue AS 不含税销售额, at_cost AS 含税成本, aet_cost AS 不含税成本, " +
            "disc_value AS 总折扣额, at_disc AS 供应商折扣承担金额, profit AS 含税毛利, net_profit AS 不含税毛利, " +
            "profit_rate AS 毛利率, net_sale_amount AS 销售净额, net_profit_amount AS 净毛利额, net_profit_rate AS 净毛利率, " +
            "wsale_revenue AS 批发销售金额, wsale_revenue_rate AS 批发销售占比, wsale_profit AS 批发销售毛利, wsale_profit_rate AS 批发毛利占比, " +
            "sale_mem AS 含税会员销售, net_sale_mem AS 不含税会员销售, sale_mem_rate AS 会员销售占比, " +
            "sale_revenue_pro AS 促销销售, net_sale_revenue_pro AS 不含税促销销售, " +
            "at_cost_pro AS 含税促销成本, aet_cost_pro AS 不含税促销成本, " +
            "profit_pro AS 促销毛利, net_profit_pro AS 不含税促销毛利, " +
            "sale_revenue_pro_rate AS 促销销售占比, profit_pro_rate AS 促销毛利占比, " +
            "count_trade AS 交易笔数, cust_price AS 客单价, count_mem AS 会员交易笔数, " +
            "mem_cust_price AS 会员客单价, mem_trade_rate AS 会员交易占比, " +
            "plan_sale AS 销售计划, plan_sale_rate AS 销售计划达成率, plan_profit AS 毛利计划, plan_profit_rate AS 毛利计划达成率, " +
            "sale_revenue_dq AS 对期销售金额, net_sale_revenue_dq AS 对期不含税销售额, dq_net_sale_amount AS 对期销售净额, " +
            "at_cost_dq AS 对期含税成本, aet_cost_dq AS 对期不含税成本, disc_value_dq AS 对期总折扣额, at_disc_dq AS 对期供应商折扣承担金额, " +
            "profit_dq AS 对期含税毛利, net_profit_dq AS 对期不含税毛利, dq_net_profit_amount AS 对期净毛利额, " +
            "wsale_revenue_dq AS 对期批发销售金额, dq_wsale_revenue_rate AS 对期批发销售占比, " +
            "wsale_profit_dq AS 对期批发销售毛利, dq_wsale_profit_rate AS 对期批发毛利占比, " +
            "sale_mem_dq AS 对期含税会员销售, net_sale_mem_dq AS 对期不含税会员销售, dq_sale_mem_rate AS 对期会员销售占比, " +
            "sale_revenue_pro_dq AS 对期促销销售, net_sale_revenue_pro_dq AS 对期不含税促销销售, " +
            "at_cost_pro_dq AS 对期含税促销成本, aet_cost_pro_dq AS 对期促销不含税成本, " +
            "profit_pro_dq AS 对期促销毛利, net_profit_pro_dq AS 对期不含税促销毛利, " +
            "dq_profit_rate AS 对期毛利率, dq_net_profit_rate AS 对期净毛利率, " +
            "dq_sale_revenue_pro_rate AS 对期促销销售占比, dq_profit_pro_rate AS 对期促销毛利占比, " +
            "count_trade_dq AS 对期交易笔数, dq_cust_price AS 对期客单价, " +
            "count_mem_dq AS 对期会员交易笔数, dq_mem_cust_price AS 对期会员客单价, dq_mem_trade_rate AS 对期会员交易占比, " +
            "sale_revenue_growth AS 销售额增长率, profit_growth AS 毛利额增长率, profit_rate_growth AS 毛利率对比增长, " +
            "net_sale_growth AS 销售净额对比增长, net_profit_growth AS 净毛利额对比增长, net_profit_rate_growth AS 净毛利率对比增长, " +
            "sale_revenue_pm AS 销售额排名, profit_pm AS 毛利额排名, stock_amount AS 当日库存金额 " +
            "FROM dw.rpt_sale_detail_precompute " +
            "WHERE query_date = ? AND comparison_type = ? AND (__ORGS__) AND report_type = ? AND dept_levels = ? " +
            "ORDER BY store_id, dept_id1, dept_id2, dept_id3, dept_id4, dept_id5, dept_id6, dept_id7, dept_id8, adno, " +
            "cat_id1, cat_id2, cat_id3, cat_id4, cat_id5, cat_id6, cat_id7, cat_id8, ccode";

    private static String buildInsertSql() {
        StringBuilder cols = new StringBuilder();
        StringBuilder ph = new StringBuilder();
        for (int i = 1; i < PrecomputeCols.COLS.size(); i++) {   // 跳过 id（自增）
            if (i > 1) {
                cols.append(',');
                ph.append(',');
            }
            cols.append(PrecomputeCols.COLS.get(i));
            ph.append('?');
        }
        return "INSERT INTO dw.rpt_sale_detail_precompute (" + cols + ") VALUES (" + ph + ")";
    }

    // =====================================================================
    // 写库
    // =====================================================================

    /**
     * 批量写入一次跑批结果（幂等：事务内先删该日旧数据再插入；MOM/YOY 各调一次）
     *
     * @param rows 引擎返回的结果行（key=中文别名）
     * @param ctx  批次上下文（batchId / queryDate / comparisonType / 对期日期 / orgCode / reportType / triggerType）
     * @return 写入行数
     */
    public int batchInsert(List<Map<String, Object>> rows, BatchContext ctx) {
        if (rows == null || rows.isEmpty()) {
            log.warn("[预计算] {} {} 引擎返回 0 行，跳过写入（仍记日志）", ctx.queryDate, ctx.comparisonType);
            rows = new ArrayList<>();
        }
        // 报表2（dept_levels=0）用整批删除维度；报表1（3/2/1）带 org_code
        final boolean isReport2 = SaleDetailPrecomputeService.REPORT_TYPE_2.equals(ctx.reportType) && ctx.deptLevels == SaleDetailPrecomputeService.DEPT_LEVELS_R2;
        final String deleteSql = isReport2 ? DELETE_SQL_R2 : DELETE_SQL_R1;
        try (Connection conn = scDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. 幂等删除（仅预计算表自身，带全条件）
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setDate(1, Date.valueOf(ctx.queryDate));
                    del.setString(2, ctx.reportType);
                    if (isReport2) {
                        del.setString(3, ctx.comparisonType);
                        del.setInt(4, ctx.deptLevels);
                    } else {
                        del.setString(3, ctx.orgCode);
                        del.setString(4, ctx.comparisonType);
                        del.setInt(5, ctx.deptLevels);
                    }
                    int deleted = del.executeUpdate();
                    if (deleted > 0) {
                        log.info("[预计算] 幂等清理 {} {} 旧数据 {} 行", ctx.queryDate, ctx.comparisonType, deleted);
                    }
                }
                // 2. 批量插入（207 列）
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    for (Map<String, Object> row : rows) {
                        int idx = 1;
                        for (int i = 1; i < PrecomputeCols.COLS.size(); i++) {
                            Object v = resolveValue(PrecomputeCols.COLS.get(i), row, ctx);
                            if (v == null) {
                                // mssql-jdbc 对 Types.NULL 会发送 varbinary(8000) 类型的 NULL，
                                // 插入 DATE 列会报「不允许从 varbinary 到 date 的隐式转换」（错误257），
                                // 因此 setNull 必须按目标列类型指定（见 PrecomputeCols.SQL_TYPES）
                                Integer jdbcType = PrecomputeCols.SQL_TYPES.get(PrecomputeCols.COLS.get(i));
                                ps.setNull(idx++, jdbcType != null ? jdbcType : java.sql.Types.NULL);
                            } else {
                                ps.setObject(idx++, v);
                            }
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // 3. 跑批日志（与数据同一事务，保证一致性）
                insertLog(conn, ctx, rows.size(), "SUCCESS", null);
                conn.commit();
                return rows.size();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("预计算写入失败(" + ctx.queryDate + "/" + ctx.comparisonType + "): " + e.getMessage(), e);
        }
    }

    /**
     * 记录一次跑批日志（独立连接调用，用于插入失败时也能留下 FAILED 记录）
     */
    public void logFailure(BatchContext ctx, String message) {
        try (Connection conn = scDataSource.getConnection()) {
            insertLog(conn, ctx, 0, "FAILED", truncate(message));
        } catch (SQLException e) {
            log.error("[预计算] 写入失败日志出错: {}", e.getMessage());
        }
    }

    private void insertLog(Connection conn, BatchContext ctx, int rowCount, String status, String message) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_LOG_SQL)) {
            ps.setString(1, ctx.batchId);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setDate(3, Date.valueOf(ctx.queryDate));
            ps.setString(4, ctx.comparisonType);
            ps.setString(5, ctx.orgCode);
            ps.setString(6, ctx.reportType);
            ps.setInt(7, rowCount);
            ps.setString(8, status);
            ps.setString(9, truncate(message));
            ps.setString(10, ctx.triggerType);
            ps.executeUpdate();
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 490 ? s.substring(0, 490) : s;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    /**
     * 按机构编码 + 档位查询预计算表（返回中文 key，与引擎直查一致）
     *
     * ⚠️ 机构编码匹配用前缀 LIKE（org_code LIKE ? + '%'）：
     *   - 报表2 落库 org_code 是引擎返回的完整门店编码（1101001 / 1102xxx / 1191001 等）；
     *     前端传短码「1101」「1102」时需前缀匹配其下所有门店；1191001 精确命中。
     *   - 报表1 落库 org_code 为完整单机构编码（如 1101001），传完整码时 LIKE 退化为精确匹配，无副作用。
     * 多个机构以 OR 连接。
     */
    public List<Map<String, Object>> queryByReportType(LocalDate queryDate, String comparisonType, List<String> orgs, String reportType, int deptLevels) {
        if (orgs == null || orgs.isEmpty()) {
            orgs = java.util.Collections.singletonList(SaleDetailPrecomputeService.DEFAULT_ORG);
        }
        String placeholders = orgs.stream().map(o -> "org_code LIKE ? + '%'").collect(Collectors.joining(" OR "));
        String sql = SQL_QUERY_TEMPLATE.replace("__ORGS__", placeholders);
        return query(sql, queryDate, comparisonType, orgs, reportType, deptLevels);
    }

    private List<Map<String, Object>> query(String sql, LocalDate queryDate, String comparisonType, List<String> orgs, String reportType, int deptLevels) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = scDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(queryDate));
            ps.setString(2, comparisonType);
            int idx = 3;
            for (String o : orgs) {
                ps.setString(idx++, o);
            }
            ps.setString(idx++, reportType);
            ps.setInt(idx, deptLevels);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int n = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("预计算查询失败: " + e.getMessage(), e);
        }
        return result;
    }

    /** 最近跑批记录（前端「预计算管理」面板用，按 reportType 过滤） */
    public List<Map<String, Object>> lastBatchLogs(int limit, String reportType) {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = scDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(LAST_LOG_SQL)) {
            ps.setInt(1, limit);
            ps.setString(2, reportType);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int n = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= n; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("跑批日志查询失败: " + e.getMessage(), e);
        }
        return result;
    }

    // =====================================================================
    // 列值解析（引擎中文 Map -> 预计算表英文列）
    // =====================================================================

    /**
     * 解析单个列的值，优先级：
     * 1. A/B 组元数据列：由批次上下文/固定参数生成
     * 2. hs_date：记账日期模式下与 fs_date 同源
     * 3. NULL_COLS（引擎不返回/预留列）→ NULL
     * 4. CH 命中 → row.get(中文别名)
     * 5. ACC_EQ 命中 → row.get(等价 C_ 列的中文别名)
     * 6. 兜底 NULL
     *
     * ⚠️ org_code 列：优先取行内「机构编码」——报表2 多机构整串一次调用时，行自带机构编码，
     *    查询按 org_code IN 过滤才不会空；报表1 单行单列取行内值等价。
     */
    private Object resolveValue(String col, Map<String, Object> row, BatchContext ctx) {
        switch (col) {
            case "batch_id":      return ctx.batchId;
            case "etl_time":      return Timestamp.valueOf(LocalDateTime.now());
            case "query_date":    return Date.valueOf(ctx.queryDate);
            case "comparison_type": return ctx.comparisonType;
            case "cmp_start_date":  return Date.valueOf(ctx.cmpStartDate);
            case "cmp_end_date":    return Date.valueOf(ctx.cmpEndDate);
            case "report_type":   return ctx.reportType;
            case "org_code": {
                Object oc = row.get("机构编码");
                return (oc != null) ? String.valueOf(oc) : ctx.orgCode;
            }
            case "dept_levels":   return ctx.deptLevels;
            case "tenant_id":     return "8";
            case "date_type":     return "记账日期";
            case "lang":          return "L";
            case "show_store":    return "显示门店";
            case "show_brand":    return "不显示品牌";
            case "show_date":     return "显示日期";
            case "hs_date":       return toDate(row.get("日期"));
            case "fs_date":       return toDate(row.get("日期"));
            default: break;
        }
        if (PrecomputeCols.NULL_COLS.contains(col)) {
            return null;
        }
        String ch = PrecomputeCols.CH.get(col);
        if (ch != null) {
            return row.get(ch);
        }
        String eq = PrecomputeCols.ACC_EQ.get(col);
        if (eq != null) {
            String eqCh = PrecomputeCols.CH.get(eq);
            return eqCh != null ? row.get(eqCh) : null;
        }
        return null;
    }

    /**
     * 日期值安全转换（引擎 DATE 列经 mssql-jdbc 返回 java.sql.Date；
     * 防御处理 Timestamp / String 等类型，避免类型错位写入 DATE 列）
     */
    private Object toDate(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Date) return v;
        if (v instanceof java.util.Date) return new java.sql.Date(((java.util.Date) v).getTime());
        if (v instanceof LocalDate) return Date.valueOf((LocalDate) v);
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return null;
            try {
                return Date.valueOf(s.length() > 10 ? s.substring(0, 10) : s);
            } catch (Exception e) {
                return null;
            }
        }
        return v;
    }

    // =====================================================================
    // 批次上下文
    // =====================================================================

    public static class BatchContext {
        public final String batchId;
        public final LocalDate queryDate;
        public final String comparisonType;
        public final LocalDate cmpStartDate;
        public final LocalDate cmpEndDate;
        public final String orgCode;
        public final String reportType;
        public final String triggerType;   // SCHEDULE=定时 / MANUAL=手动
        public final int deptLevels;       // 报表1: 3=明细/2=部门合计/1=机构汇总；报表2: 0=部门级明细

        public BatchContext(String batchId, LocalDate queryDate, String comparisonType,
                            LocalDate cmpStartDate, LocalDate cmpEndDate,
                            String orgCode, String reportType, String triggerType, int deptLevels) {
            this.batchId = batchId;
            this.queryDate = queryDate;
            this.comparisonType = comparisonType;
            this.cmpStartDate = cmpStartDate;
            this.cmpEndDate = cmpEndDate;
            this.orgCode = orgCode;
            this.reportType = reportType;
            this.triggerType = triggerType;
            this.deptLevels = deptLevels;
        }
    }
}
