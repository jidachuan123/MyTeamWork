package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售详情4（SD4，配送调拨汇总表）取数服务 —— 2026-10-16 新建。
 *
 * ★ 数据源：SQL Server RDS_SC 库（tb_io_om / tb_io_om_g 等表在 RDS_SC）。
 * ★ 口径统一：页面查询和定时截图<strong>共用本类的同一套取数逻辑</strong>，
 *   保证「截图 = 页面」的红线。
 *
 * 实现要点：
 *   1. SQL 是模板（resources/sd4/delivery-transfer-summary.sql），里面 DECLARE 的值用 ${xxx} 占位，
 *      本类执行前用「配置固定参数 + 页面参数」替换后执行（replace 后单引号已做转义）。
 *   2. SQL 是多语句脚本（DECLARE + 临时表 + INSERT + UNION ALL 大查询），必须在同一个 Connection 上
 *      整段执行；用 Statement.execute + 遍历所有结果集，取「最后一个结果集」作为最终数据
 *      （脚本最后一句才是 SELECT 汇总）。
 *   3. 结果列（原 SQL 固定 8 列，顺序见下）：
 *      发货机构编码 / 发货机构名称 / 当日库存金额 / 发货结算金额 / 环比发货结算金额 /
 *      同比发货结算金额 / 环比增长率 / 同比增长率
 *   4. ★排序在应用内存中完成（按发货机构编码升序），绝不写进 SQL：
 *      SQL 内 ORDER BY 会触发全表排序，实测查询速度退化约 4 倍（2026-10-16 用户反馈），
 *      因此排序只在本类返回前做（页面与截图共用，天然一致）。
 */
@Service
public class SalesDetail4Service {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail4Service.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 形如 ${xxx} 的「真实占位符」（方括号内限 ASCII 标识符；注释里的中文示例不会误报） */
    private static final java.util.regex.Pattern PLACEHOLDER =
            java.util.regex.Pattern.compile("\\$\\{[A-Za-z0-9_]+\\}");

    @Autowired
    @Qualifier("sd4JdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private Sd4Properties props;

    // ==================== 对外：查询（供页面 + 截图共用）====================

    /**
     * 按条件查询配送调拨汇总（6 个日期为空时按 SD1 逻辑推算：记账=前一天、环比=前两天、同比=去年同日）。
     *
     * @param accStartDate 记账日期开始（yyyy-MM-dd，空=推算）
     * @param accEndDate   记账日期结束（空=跟随开始）
     * @param cmpStartDate 环比日期开始（空=推算）
     * @param cmpEndDate   环比日期结束（空=跟随开始）
     * @param yoyStartDate 同比日期开始（空=推算）
     * @param yoyEndDate   同比日期结束（空=跟随开始）
     * @param outOrgCodes  发货机构（门店编码，逗号分隔多个，空=不筛选）
     */
    public Sd4Result query(String accStartDate, String accEndDate,
                           String cmpStartDate, String cmpEndDate,
                           String yoyStartDate, String yoyEndDate,
                           String outOrgCodes) {
        // 日期空值兜底（与 SD1 推算逻辑一致）
        LocalDate today = LocalDate.now();
        String defAcc = today.minusDays(1).format(DTF);
        String defCmp = today.minusDays(2).format(DTF);
        String defYoy = today.minusYears(1).format(DTF);

        String accS = firstNonBlank(accStartDate, defAcc);
        String accE = firstNonBlank(accEndDate, accS);
        String cmpS = firstNonBlank(cmpStartDate, defCmp);
        String cmpE = firstNonBlank(cmpEndDate, cmpS);
        String yoyS = firstNonBlank(yoyStartDate, defYoy);
        String yoyE = firstNonBlank(yoyEndDate, yoyS);
        String outOrg = normalizeCodes(outOrgCodes);

        log.info("[销售详情4] 开始取数 记账={}~{} 环比={}~{} 同比={}~{} 发货机构={}",
                accS, accE, cmpS, cmpE, yoyS, yoyE, outOrg.isEmpty() ? "(全部)" : outOrg);

        long t0 = System.currentTimeMillis();

        // 组装占位符参数（固定参数来自 yml，页面参数来自入参）
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenantId", props.getTenantId());
        params.put("lang", props.getLang());
        params.put("accStartDate", accS);
        params.put("accEndDate", accE);
        params.put("cmpStartDate", cmpS);
        params.put("cmpEndDate", cmpE);
        params.put("yoyStartDate", yoyS);
        params.put("yoyEndDate", yoyE);
        params.put("outOrgCodes", outOrg);
        params.put("outWnoCodes", props.getOutWnoCodes());
        params.put("inOrgCodes", props.getInOrgCodes());
        params.put("inWnoCodes", props.getInWnoCodes());
        params.put("departCodes", props.getDepartCodes());
        params.put("categoryCodes", props.getCategoryCodes());
        params.put("billType", props.getBillType());
        params.put("accType", props.getAccType());

        String sql = fillTemplate(loadSql(), params);

        // 多语句脚本整体在同一 Connection 上执行（临时表为连接级，必须同连接）
        List<Map<String, Object>> last = jdbc.execute((ConnectionCallback<List<Map<String, Object>>>) conn -> {
            try (Statement st = conn.createStatement()) {
                boolean hasRs = st.execute(sql);
                List<Map<String, Object>> lastResult = new ArrayList<>();
                while (true) {
                    if (hasRs) {
                        try (ResultSet rs = st.getResultSet()) {
                            // 每个结果集都读出来；最终只保留最后一个（脚本最后一句是汇总 SELECT）
                            lastResult = readAll(rs);
                        }
                    } else if (st.getUpdateCount() == -1) {
                        break;   // 没有更多结果
                    }
                    hasRs = st.getMoreResults();
                }
                return lastResult;
            }
        });

        List<Sd4Row> rows = toRows(last == null ? new ArrayList<>() : last);

        // ★ 排序在应用内存中完成（按发货机构编码升序），不写进 SQL：
        //    SQL 内 ORDER BY 会触发全表排序，实测查询速度退化约 4 倍（2026-10-16 用户反馈）
        rows.sort(Comparator.comparing((Sd4Row r) ->
                r.getOutOrgCode() == null ? "" : r.getOutOrgCode()));
        // 排序后重排序号，保证序号 1..n 与展示顺序一致（页面 + 截图共用此结果）
        int seqNo = 0;
        for (Sd4Row r : rows) {
            r.setSeq(++seqNo);
        }

        log.info("[销售详情4] 取数完成，{} 行，耗时 {} ms", rows.size(), System.currentTimeMillis() - t0);
        return new Sd4Result(rows, accS, accE, cmpS, cmpE, yoyS, yoyE, outOrg);
    }

    // ==================== 模板与执行 ====================

    /** 把 ${xxx} 占位符替换为实际值（值里的单引号翻倍，防破坏 SQL 语法） */
    private String fillTemplate(String tpl, Map<String, String> params) {
        String sql = tpl;
        for (Map.Entry<String, String> e : params.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue().replace("'", "''");
            sql = sql.replace("${" + e.getKey() + "}", v);
        }
        // 只在残留「形如 ${fooBar} 的真实占位符」时告警（并打印出具体是哪一个，便于排查）；
        // 注释里 ${参数名} 这类中文示例不算——避免了 contains("${") 的误报
        java.util.regex.Matcher m = PLACEHOLDER.matcher(sql);
        if (m.find()) {
            log.warn("[销售详情4] SQL 模板存在未替换的占位符 {}，请检查参数名是否与 application-sd4.yml 一致", m.group());
        }
        return sql;
    }

    /** 一个结果集读全部行（列名 → 值） */
    private static List<Map<String, Object>> readAll(ResultSet rs) throws java.sql.SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                row.put(md.getColumnLabel(i), rs.getObject(i));
            }
            list.add(row);
        }
        return list;
    }

    /** 加载 SQL：支持 classpath:xxx 或磁盘绝对路径 */
    private String loadSql() {
        String path = props.getSqlFile();
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        path = path.trim();
        try {
            if (path.startsWith("classpath:")) {
                String cp = path.substring("classpath:".length());
                ClassPathResource res = new ClassPathResource(cp);
                try (InputStream in = res.getInputStream()) {
                    return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                }
            }
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[销售详情4] 读取 SQL 文件失败: {}", path, e);
            return "";
        }
    }

    // ==================== 结果组装 ====================

    /** SQL 结果 → 行对象（列名取原 SQL 的中文别名，取不到的列按 0/null 兜底） */
    private List<Sd4Row> toRows(List<Map<String, Object>> detail) {
        List<Sd4Row> rows = new ArrayList<>();
        for (Map<String, Object> r : detail) {
            String code = str(r.get("发货机构编码"));
            String name = str(r.get("发货机构名称"));
            if (code.isEmpty() && name.isEmpty()) {
                continue;   // 全空行丢弃
            }
            Sd4Row row = new Sd4Row();
            // 序号不在此处赋值：由 query() 排序后统一重排（保证与展示顺序一致）
            row.setOutOrgCode(code);
            row.setOutOrgName(name);
            row.setStockAmt(dec(r.get("当日库存金额")));
            row.setSendAmt(dec(r.get("发货结算金额")));
            row.setCmpAmt(dec(r.get("环比发货结算金额")));
            row.setYoyAmt(dec(r.get("同比发货结算金额")));
            row.setCmpRate(decOrNull(r.get("环比增长率")));
            row.setYoyRate(decOrNull(r.get("同比增长率")));
            rows.add(row);
        }
        return rows;
    }

    // ==================== 工具 ====================

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.trim().isEmpty()) ? a.trim() : b;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 增长率：SQL 可能返回 NULL（分母为 0），保留 null 以便页面显示「-」 */
    private static BigDecimal decOrNull(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 机构编码归一化：去空格、去重（逗号/分号/空白分隔均兼容） */
    private static String normalizeCodes(String codes) {
        String s = codes == null ? "" : codes.trim();
        if (s.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split("[,，;；\\s]+")) {
            String p = part.trim();
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return String.join(",", out);
    }

    // ==================== 数据模型 ====================

    /** 表格一行：一个发货机构一条 */
    public static class Sd4Row {
        /** 序号 */
        private int seq;
        /** 发货机构编码 */
        private String outOrgCode;
        /** 发货机构名称 */
        private String outOrgName;
        /** 当日库存金额 */
        private BigDecimal stockAmt;
        /** 发货结算金额（本期） */
        private BigDecimal sendAmt;
        /** 环比发货结算金额 */
        private BigDecimal cmpAmt;
        /** 同比发货结算金额 */
        private BigDecimal yoyAmt;
        /** 环比增长率（小数，如 -0.159 表示 -15.9%；null=无法计算） */
        private BigDecimal cmpRate;
        /** 同比增长率（小数；null=无法计算） */
        private BigDecimal yoyRate;

        public int getSeq() {
            return seq;
        }

        public void setSeq(int seq) {
            this.seq = seq;
        }

        public String getOutOrgCode() {
            return outOrgCode;
        }

        public void setOutOrgCode(String outOrgCode) {
            this.outOrgCode = outOrgCode;
        }

        public String getOutOrgName() {
            return outOrgName;
        }

        public void setOutOrgName(String outOrgName) {
            this.outOrgName = outOrgName;
        }

        public BigDecimal getStockAmt() {
            return stockAmt;
        }

        public void setStockAmt(BigDecimal stockAmt) {
            this.stockAmt = stockAmt;
        }

        public BigDecimal getSendAmt() {
            return sendAmt;
        }

        public void setSendAmt(BigDecimal sendAmt) {
            this.sendAmt = sendAmt;
        }

        public BigDecimal getCmpAmt() {
            return cmpAmt;
        }

        public void setCmpAmt(BigDecimal cmpAmt) {
            this.cmpAmt = cmpAmt;
        }

        public BigDecimal getYoyAmt() {
            return yoyAmt;
        }

        public void setYoyAmt(BigDecimal yoyAmt) {
            this.yoyAmt = yoyAmt;
        }

        public BigDecimal getCmpRate() {
            return cmpRate;
        }

        public void setCmpRate(BigDecimal cmpRate) {
            this.cmpRate = cmpRate;
        }

        public BigDecimal getYoyRate() {
            return yoyRate;
        }

        public void setYoyRate(BigDecimal yoyRate) {
            this.yoyRate = yoyRate;
        }
    }

    /** 查询结果：行 + 本次实际使用的参数（截图/日志用） */
    public static class Sd4Result {
        private final List<Sd4Row> rows;
        private final String accStartDate;
        private final String accEndDate;
        private final String cmpStartDate;
        private final String cmpEndDate;
        private final String yoyStartDate;
        private final String yoyEndDate;
        private final String outOrgCodes;

        public Sd4Result(List<Sd4Row> rows, String accStartDate, String accEndDate,
                         String cmpStartDate, String cmpEndDate,
                         String yoyStartDate, String yoyEndDate, String outOrgCodes) {
            this.rows = rows;
            this.accStartDate = accStartDate;
            this.accEndDate = accEndDate;
            this.cmpStartDate = cmpStartDate;
            this.cmpEndDate = cmpEndDate;
            this.yoyStartDate = yoyStartDate;
            this.yoyEndDate = yoyEndDate;
            this.outOrgCodes = outOrgCodes;
        }

        public List<Sd4Row> getRows() {
            return rows;
        }

        public String getAccStartDate() {
            return accStartDate;
        }

        public String getAccEndDate() {
            return accEndDate;
        }

        public String getCmpStartDate() {
            return cmpStartDate;
        }

        public String getCmpEndDate() {
            return cmpEndDate;
        }

        public String getYoyStartDate() {
            return yoyStartDate;
        }

        public String getYoyEndDate() {
            return yoyEndDate;
        }

        public String getOutOrgCodes() {
            return outOrgCodes;
        }
    }
}
