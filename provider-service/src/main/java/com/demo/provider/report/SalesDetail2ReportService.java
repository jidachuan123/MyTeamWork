package com.demo.provider.report;

import com.demo.provider.service.SalesDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 销售详情2 报表服务（逻辑与前端 SalesDetail2.vue 完全一致）
 *
 * 调用两遍后端接口：
 *   1. 环比调用：cmpStartDate/cmpEndDate = 环比日期 → 对期* 字段 = 环比值
 *   2. 同比调用：cmpStartDate/cmpEndDate = 同比日期 → 对期* 字段 = 同比值
 *
 * 入参：deptLevels=""（不传）、department=""（不传）、orgCode=1101,1102,1191001
 * 按机构编码分组（巨野中心店组 / 便利组），组内各店求和，组尾出合计行。
 *
 * 日期规则：本期=前一天、环比=前两天、同比=去年的今天
 */
@Service
public class SalesDetail2ReportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetailService salesDetailService;

    @Autowired
    private SalesDailyReportService reportService; // 复用 screenshot 方法

    @Value("${report.daily.org-code2:1101,1102,1191001}")
    private String orgCode2;

    @Value("${report.daily.output-dir:target/report}")
    private String outputDir;

    private static final Logger log = LoggerFactory.getLogger(SalesDetail2ReportService.class);

    // 固定查询参数
    private static final String TENANT_ID = "8";
    private static final String LANG = "L";
    private static final String USER_NO = "SYSTEM";
    private static final String DATE_TYPE = "记账日期";
    private static final String SHOW_STORE = "显示门店";
    private static final String SHOW_BRAND = "不显示品牌";

    /**
     * 生成某天的销售详情2 报表：取数 → HTML → 截图
     *
     * 定时任务：机构编码取配置 report.daily.org-code2（默认 1101,1102,1191001），
     * 日期由 date 推算（本期=前一天、环比=前两天、同比=去年今天），部门层级/部门编码留空走后端默认口径。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(LocalDate date) {
        DailyReportParam req = new DailyReportParam();
        req.setOrgCode(orgCode2);
        req.setDeptLevels("");
        req.setDepartment("");
        req.setStartDate(date.minusDays(1).format(DTF));
        req.setEndDate(date.minusDays(1).format(DTF));
        req.setCmpStartDate(date.minusDays(2).format(DTF));
        req.setCmpEndDate(date.minusDays(2).format(DTF));
        req.setYoyStartDate(date.minusYears(1).format(DTF));
        req.setYoyEndDate(date.minusYears(1).format(DTF));
        return generateDailyReport(req);
    }

    /**
     * 按页面查询参数生成销售详情2 截图（手动触发用）。
     * 日期 / 机构编码 / 部门层级 / 部门编码全部来自前端传入的 queryForm，不再写死。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(DailyReportParam req) {
        // 报表日期标签：取「本期结束日期」；为空时回退今天
        String today = (req.getEndDate() != null && !req.getEndDate().trim().isEmpty())
                ? req.getEndDate().trim() : LocalDate.now().format(DTF);
        String org = (req.getOrgCode() != null && !req.getOrgCode().trim().isEmpty())
                ? req.getOrgCode().trim() : orgCode2;
        String dept = (req.getDepartment() != null) ? req.getDepartment().trim() : "";
        String deptLevels = (req.getDeptLevels() != null) ? req.getDeptLevels().trim() : "";
        String q   = (req.getStartDate() != null) ? req.getStartDate().trim() : "";
        String end = (req.getEndDate()    != null) ? req.getEndDate().trim()    : "";
        String m   = (req.getCmpStartDate() != null) ? req.getCmpStartDate().trim() : "";
        String mEnd = (req.getCmpEndDate()  != null) ? req.getCmpEndDate().trim()  : "";
        String y   = (req.getYoyStartDate() != null) ? req.getYoyStartDate().trim() : "";
        String yEnd = (req.getYoyEndDate()  != null) ? req.getYoyEndDate().trim()  : "";

        log.info("[销售详情2] 开始生成 {} 报表（查询 {}/{} / 环比 {}/{} / 同比 {}/{}），orgCode={}, deptLevels={}, department={}",
                today, q, end, m, mEnd, y, yEnd, org, deptLevels, dept);

        // ===== 1. 取数（2 次查询）=====
        // 环比调用：对比日期 = 环比日期；本期值 + 环比对期值 从此结果取
        List<Map<String, Object>> momData = query(q, end, m, mEnd, org, deptLevels, dept);
        // 同比调用：对比日期 = 同比日期；同比对期值从此结果取
        List<Map<String, Object>> yoyData = query(q, end, y, yEnd, org, deptLevels, dept);

        // ===== 2. 组装表格行 =====
        List<StoreRow> rows = buildRows(momData, yoyData);

        // ===== 3. 生成 HTML =====
        try {
            reportService.ensureOutputDir();
        } catch (Exception e) {
            log.error("[销售详情2] 创建输出目录失败", e);
            return null;
        }
        String htmlPath = outputDir + "/sales-detail2-" + today + ".html";
        try {
            Files.write(Paths.get(htmlPath), buildHtml(q, m, y, rows).getBytes(StandardCharsets.UTF_8));
            log.info("[销售详情2] HTML 已生成: {}", new File(htmlPath).getAbsolutePath());
        } catch (Exception e) {
            log.error("[销售详情2] 生成 HTML 失败", e);
            return null;
        }

        // ===== 4. 截图 =====
        String pngPath = outputDir + "/sales-detail2-" + today + ".png";
        boolean shotOk = reportService.screenshot(htmlPath, pngPath, rows.size());
        log.info("[销售详情2] 报表生成完成，共 {} 行数据，截图: {} ({})",
                rows.size(), pngPath, shotOk ? "成功" : "失败");
        return shotOk ? pngPath : null;
    }

    // ==================== 查询 ====================

    private List<Map<String, Object>> query(String startDate, String endDate,
                                            String cmpStartDate, String cmpEndDate,
                                            String orgCode, String deptLevels, String department) {
        // deptLevels/department 留空时后端走默认口径（与页面一致）
        return salesDetailService.getSalesDetail(
                TENANT_ID, LANG, USER_NO,
                DATE_TYPE, startDate, endDate,
                cmpStartDate, cmpEndDate,
                SHOW_STORE, deptLevels, "", SHOW_BRAND,
                orgCode, department, "", "", "",
                "0", "否", "显示日期");
    }

    // ==================== 行数据组装（与前端 SalesDetail2.vue 一致）====================

    private List<StoreRow> buildRows(List<Map<String, Object>> momData, List<Map<String, Object>> yoyData) {
        // 按机构编码汇总（同一机构可能返回多行部门级明细）
        Map<String, StoreRow> map = new LinkedHashMap<>();

        // 环比调用：本期值 + 环比对期值
        for (Map<String, Object> r : momData) {
            String code = str(r.get("机构编码"));
            if (code == null || code.isEmpty()) continue;
            StoreRow row = map.computeIfAbsent(code, k -> new StoreRow(code));
            if (row.orgName.isEmpty()) row.orgName = str(r.get("机构名称"));
            row.sales += num(r.get("销售金额"));
            row.profit += num(r.get("含税毛利"));
            row.customers += num(r.get("交易笔数"));
            row.stockAmount += num(r.get("当日库存金额"));
            row.hasStock = true;
            row.momSales += num(r.get("对期销售金额"));
            row.momProfit += num(r.get("对期含税毛利"));
            row.momCustomers += num(r.get("对期交易笔数"));
            row.hasData = true;
        }

        // 同比调用：同比对期值
        // 与前端 SalesDetail2.vue 一致：同比有、本期无的机构也要创建并展示（全 0 占位行）
        for (Map<String, Object> r : yoyData) {
            String code = str(r.get("机构编码"));
            if (code == null || code.isEmpty()) continue;
            StoreRow row = map.computeIfAbsent(code, k -> new StoreRow(code));
            if (row.orgName.isEmpty()) row.orgName = str(r.get("机构名称"));
            row.yoySales += num(r.get("对期销售金额"));
            row.yoyProfit += num(r.get("对期含税毛利"));
            row.yoyCustomers += num(r.get("对期交易笔数"));
            // 当日库存金额 = 本期当天的实时库存快照（与 cmp 对比期参数无关）：
            // 环比调用未返回该店（本期无销售、仅同比有，如 1102027）时，用同比调用返回的库存补上；
            // 已有库存（环比调用返回过）则跳过，避免两个接口同一库存重复累加翻倍。
            if (!row.hasStock) {
                row.stockAmount += num(r.get("当日库存金额"));
                row.hasStock = true;
            }
        }

        // 派生指标（与前端一致：全 0 门店也占位显示，不因 hasData=false 跳过）
        List<StoreRow> storeRows = new ArrayList<>();
        for (StoreRow row : map.values()) {
            row.group = getGroup(row.orgCode);
            row.avgPrice = avgPriceOf(row.sales, row.customers);
            row.momAvgPrice = avgPriceOf(row.momSales, row.momCustomers);
            row.yoyAvgPrice = avgPriceOf(row.yoySales, row.yoyCustomers);
            row.profitRate = profitRateOf(row.profit, row.sales);
            row.yoySalesRate = rate(row.sales, row.yoySales);
            row.momSalesRate = rate(row.sales, row.momSales);
            row.yoyProfitRate = rate(row.profit, row.yoyProfit);
            row.momProfitRate = rate(row.profit, row.momProfit);
            row.yoyCustomerRate = rate(row.customers, row.yoyCustomers);
            row.momCustomerRate = rate(row.customers, row.momCustomers);
            row.yoyAvgPriceRate = rate(row.avgPrice, row.yoyAvgPrice);
            row.momAvgPriceRate = rate(row.avgPrice, row.momAvgPrice);
            storeRows.add(row);
        }
        storeRows.sort(Comparator.comparing(r -> String.valueOf(r.orgCode)));

        // 按组分段 + 组尾合计行
        List<StoreRow> result = new ArrayList<>();
        String[] groups = {"巨野中心店组", "便利组"};
        for (String g : groups) {
            List<StoreRow> grp = new ArrayList<>();
            for (StoreRow r : storeRows) {
                if (g.equals(r.group)) grp.add(r);
            }
            if (!grp.isEmpty()) {
                result.addAll(grp);
                result.add(buildSubtotal(grp, g));
            }
        }
        // 其他
        List<StoreRow> others = new ArrayList<>();
        for (StoreRow r : storeRows) {
            if (!"巨野中心店组".equals(r.group) && !"便利组".equals(r.group)) others.add(r);
        }
        if (!others.isEmpty()) {
            result.addAll(others);
            result.add(buildSubtotal(others, "其他"));
        }
        return result;
    }

    /** 机构分组：1101xxx/1191xxx=巨野中心店组；1102xxx=便利组；其余=其他 */
    private String getGroup(String code) {
        if (code == null) return "其他";
        if (code.startsWith("1101") || code.startsWith("1191")) return "巨野中心店组";
        if (code.startsWith("1102")) return "便利组";
        return "其他";
    }

    /** 合计行：金额求和，派生指标按合计值公式计算 */
    private StoreRow buildSubtotal(List<StoreRow> rows, String groupName) {
        StoreRow s = new StoreRow("");
        s.isSubtotal = true;
        s.orgName = groupName + " 合计";
        for (StoreRow r : rows) {
            if ("1102911".equals(r.orgCode)) continue;  // 巨野便利店配送中心为配送中心，不当门店，整行不进门店合计
            s.sales += r.sales;
            s.profit += r.profit;
            s.customers += r.customers;
            s.stockAmount += r.stockAmount;
            s.momSales += r.momSales;
            s.momProfit += r.momProfit;
            s.momCustomers += r.momCustomers;
            s.yoySales += r.yoySales;
            s.yoyProfit += r.yoyProfit;
            s.yoyCustomers += r.yoyCustomers;
        }
        s.avgPrice = avgPriceOf(s.sales, s.customers);
        s.momAvgPrice = avgPriceOf(s.momSales, s.momCustomers);
        s.yoyAvgPrice = avgPriceOf(s.yoySales, s.yoyCustomers);
        s.profitRate = profitRateOf(s.profit, s.sales);
        s.yoySalesRate = rate(s.sales, s.yoySales);
        s.momSalesRate = rate(s.sales, s.momSales);
        s.yoyProfitRate = rate(s.profit, s.yoyProfit);
        s.momProfitRate = rate(s.profit, s.momProfit);
        s.yoyCustomerRate = rate(s.customers, s.yoyCustomers);
        s.momCustomerRate = rate(s.customers, s.momCustomers);
        s.yoyAvgPriceRate = rate(s.avgPrice, s.yoyAvgPrice);
        s.momAvgPriceRate = rate(s.avgPrice, s.momAvgPrice);
        return s;
    }

    // ==================== 数值工具（与前端一致）====================

    private double num(Object v) {
        if (v == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private Double numOrNull(Object v) {
        if (v == null) return null;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 客单价 = 销售额 / 来客数 */
    private Double avgPriceOf(double sales, double customers) {
        if (customers == 0) return null;
        return Math.round(sales / customers * 100) / 100.0;
    }

    /** 毛利率 = 毛利额 / 销售额 * 100 */
    private Double profitRateOf(double profit, double sales) {
        if (sales == 0) return null;
        return Math.round(profit / sales * 10000) / 100.0;
    }

    /** 增长率 = (本期 - 对期) / 对期 * 100；null 值安全 */
    private Double rate(Double cur, Double prior) {
        if (cur == null || prior == null || prior == 0) return null;
        return Math.round((cur - prior) / prior * 10000) / 100.0;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    // ==================== 行模型 ====================

    public static class StoreRow {
        String orgCode;
        String orgName = "";
        String group = "";
        boolean isSubtotal;
        boolean hasData;
        boolean hasStock;  // 库存是否已赋值（防 MOM/YOY 两个接口重复累加当日库存翻倍）

        // 本期
        double sales, profit, customers, stockAmount;
        // 环比对期
        double momSales, momProfit, momCustomers;
        // 同比对期
        double yoySales, yoyProfit, yoyCustomers;
        // 派生
        Double avgPrice, momAvgPrice, yoyAvgPrice;
        Double profitRate;
        Double yoySalesRate, momSalesRate;
        Double yoyProfitRate, momProfitRate;
        Double yoyCustomerRate, momCustomerRate;
        Double yoyAvgPriceRate, momAvgPriceRate;

        StoreRow(String orgCode) { this.orgCode = orgCode; }
    }

    // ==================== HTML 生成 ====================

    private String buildHtml(String queryDate, String momDate, String yoyDate, List<StoreRow> rows) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
          .append("<title>各店每月销售详情</title>\n<style>\n")
          .append("body{margin:0;padding:0;background:#fff;font-family:'Microsoft YaHei','Segoe UI',sans-serif;font-size:12px;color:#333;}\n")
          .append(".page{padding:16px 20px 24px;}\n")
          .append(".header{text-align:center;padding:14px 0 12px;border-bottom:1px solid #e8e8e8;background:linear-gradient(135deg,#fff8e1,#fffde7);}\n")
          .append(".header h2{margin:0 0 8px;font-size:18px;font-weight:700;color:#b71c1c;letter-spacing:1px;}\n")
          .append(".date{display:inline-block;font-size:12px;padding:3px 10px;border-radius:4px;margin:0 6px;background:#e3f2fd;color:#1565c0;font-weight:500;}\n")
          .append(".date.mom{background:#fff3e0;color:#e65100;}\n")
          .append(".date.yoy{background:#e8f5e9;color:#2e7d32;}\n")
          .append("table{width:100%;border-collapse:collapse;margin-top:12px;white-space:nowrap;}\n")
          .append("th{padding:8px 6px;border:2px solid #8c8c8c;text-align:center;background:#f5f5f5;color:#333;font-weight:600;white-space:nowrap;}\n")
          .append("td{padding:7px 6px;border:1.5px solid #bfbfbf;text-align:center;}\n")
          .append("tr.odd td{background:#fafafa;}\n")
          .append(".col-code{text-align:center !important;}\n")
          .append(".col-org{text-align:left !important;padding-left:10px !important;}\n")
          .append(".col-num{text-align:center !important;font-family:'Segoe UI','Microsoft YaHei',sans-serif;}\n")
          .append(".c-sales{background:#fff3e0 !important;}\n")
          .append(".c-profit{background:#e8f5e9 !important;}\n")
          .append(".c-customer{background:#e3f2fd !important;}\n")
          .append(".c-price{background:#fce4ec !important;}\n")
          .append(".c-stock{background:#f3e5f5 !important;}\n")
          .append("tr.subtotal td{background:linear-gradient(90deg,#fff59d,#fff9c4);border-top:2px solid #f57f17;border-bottom:2px solid #f57f17;color:#4e342e;font-weight:700;}\n")
          .append(".rate-up{color:#d32f2f;font-weight:500;}\n")
          .append(".rate-down{color:#388e3c;font-weight:500;}\n")
          .append("</style>\n</head>\n<body>\n<div class=\"page\">\n")
          .append("<div class=\"header\"><h2>各店每月销售详情</h2><div>\n")
          .append("<span class=\"date\">本期：").append(queryDate).append(" ~ ").append(queryDate).append("</span>\n")
          .append("<span class=\"date mom\">环比：").append(momDate).append(" ~ ").append(momDate).append("</span>\n")
          .append("<span class=\"date yoy\">同比：").append(yoyDate).append(" ~ ").append(yoyDate).append("</span>\n")
          .append("</div></div>\n");

        // 表头（16 列，与前端 SalesDetail2.vue 一致：含当日库存金额）
        sb.append("<table>\n<thead><tr>")
          .append("<th>机构代码</th><th>机构名称</th><th class=\"c-stock\">当日库存金额</th>")
          .append("<th class=\"c-sales\">销售额/元</th><th class=\"c-sales\">同比<br>销售额增长率</th><th class=\"c-sales\">环比<br>销售额增长率</th>")
          .append("<th class=\"c-profit\">毛利额/元</th><th class=\"c-profit\">同比<br>毛利额增长率</th><th class=\"c-profit\">环比<br>毛利额增长率</th>")
          .append("<th class=\"c-profit\">毛利率</th>")
          .append("<th class=\"c-customer\">来客数</th><th class=\"c-customer\">同比<br>来客数增长率</th><th class=\"c-customer\">环比<br>来客数增长率</th>")
          .append("<th class=\"c-price\">客单价/元</th><th class=\"c-price\">同比<br>客单价增长率</th><th class=\"c-price\">环比<br>客单价增长率</th>")
          .append("</tr></thead>\n<tbody>\n");

        int idx = 0;
        for (StoreRow r : rows) {
            idx++;
            if (r.isSubtotal) {
                sb.append("<tr class=\"subtotal\">");
            } else {
                sb.append(idx % 2 == 0 ? "<tr class=\"odd\">" : "<tr>");
            }
            // 机构代码
            sb.append("<td class=\"col-code\">").append(esc(r.orgCode)).append("</td>");
            // 机构名称
            sb.append("<td class=\"col-org\">").append(esc(r.orgName)).append("</td>");
            // 当日库存金额
            appendNumTd(sb, "c-stock", r.stockAmount);
            // 销售额
            appendNumTd(sb, "c-sales", r.sales);
            // 同比/环比 销售额增长率
            appendRateTd(sb, "c-sales", r.yoySalesRate);
            appendRateTd(sb, "c-sales", r.momSalesRate);
            // 毛利额
            appendNumTd(sb, "c-profit", r.profit);
            // 同比/环比 毛利额增长率
            appendRateTd(sb, "c-profit", r.yoyProfitRate);
            appendRateTd(sb, "c-profit", r.momProfitRate);
            // 毛利率
            appendPctTd(sb, "c-profit", r.profitRate);
            // 来客数
            appendIntTd(sb, "c-customer", r.customers);
            // 同比/环比 来客数增长率
            appendRateTd(sb, "c-customer", r.yoyCustomerRate);
            appendRateTd(sb, "c-customer", r.momCustomerRate);
            // 客单价
            appendNumTd(sb, "c-price", r.avgPrice);
            // 同比/环比 客单价增长率
            appendRateTd(sb, "c-price", r.yoyAvgPriceRate);
            appendRateTd(sb, "c-price", r.momAvgPriceRate);
            sb.append("</tr>\n");
        }
        sb.append("</tbody>\n</table>\n</div>\n</body>\n</html>");
        return sb.toString();
    }

    private void appendNumTd(StringBuilder sb, String cls, Double v) {
        sb.append("<td class=\"").append(cls).append("\">")
          .append(v == null ? "" : String.format(Locale.US, "%,.2f", v))
          .append("</td>");
    }

    private void appendIntTd(StringBuilder sb, String cls, double v) {
        sb.append("<td class=\"").append(cls).append("\">")
          .append(String.format(Locale.US, "%,.0f", v))
          .append("</td>");
    }

    private void appendRateTd(StringBuilder sb, String cls, Double v) {
        sb.append("<td class=\"").append(cls).append(' ').append(rateClass(v)).append("\">")
          .append(fmtRate(v))
          .append("</td>");
    }

    private void appendPctTd(StringBuilder sb, String cls, Double v) {
        sb.append("<td class=\"").append(cls).append(' ').append(rateClass(v)).append("\">")
          .append(v == null ? "" : String.format(Locale.US, "%.2f", v) + "%")
          .append("</td>");
    }

    private String fmtRate(Double v) {
        if (v == null) return "";
        return (v > 0 ? "+" : "") + String.format(Locale.US, "%.2f", v) + "%";
    }

    private String rateClass(Double v) {
        if (v == null) return "";
        if (v > 0) return "rate-up";
        if (v < 0) return "rate-down";
        return "";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
