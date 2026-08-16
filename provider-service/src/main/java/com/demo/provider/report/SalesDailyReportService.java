package com.demo.provider.report;

import com.demo.provider.service.SalesDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 销售详情日报服务
 *
 * 每天早晨定时取「部门销售详情」数据（逻辑与前端 SalesDetail.vue 完全一致：
 * 明细 deptLevels=3 + 部门合计 deptLevels=2 + 超市总计 deptLevels 不传，环比/同比各查一遍），
 * 组装成 HTML 报表 → 用本机 Chrome/Edge headless 截图 PNG。邮件由调度器统一发送。
 */
@Service
public class SalesDailyReportService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 固定部组顺序（与前端一致） */
    private static final String[] DEPT_GROUPS = {"生鲜一部", "生鲜二部", "食品部", "非食部"};

    @Autowired
    private SalesDetailService salesDetailService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${report.daily.org-code:1101001}")
    private String orgCode;

    @Value("${report.daily.mail-to:}")
    private String mailTo;

    @Value("${report.daily.mail-from:}")
    private String mailFrom;

    @Value("${report.daily.chrome-path:}")
    private String chromePath;

    @Value("${report.daily.output-dir:target/report}")
    private String outputDir;

    @Value("${spring.mail.password:}")
    private String mailAuthCode;

    private static final Logger log = LoggerFactory.getLogger(SalesDailyReportService.class);

    /** 查询参数（用户要求：查询=前一天、环比=前天、同比=去年同日，在 generateDailyReport 中计算） */
    private static final String TENANT_ID = "8";
    private static final String LANG = "L";
    private static final String USER_NO = "SYSTEM";
    private static final String DATE_TYPE = "记账日期";
    private static final String SHOW_STORE = "显示门店";
    private static final String SHOW_BRAND = "不显示品牌";

    /**
     * 生成某天的销售日报（SalesDetail 页面）：取数 → HTML → 截图
     * 邮件发送由调度器统一处理（两个报表一起发）。
     *
     * 入参日期规则（date 为发送日）：
     *   - 查询日期（开始/结束）    = 前一天
     *   - 环比对比日期（开始/结束）= 前两天
     *   - 同比对比日期（开始/结束）= 去年的今天
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(LocalDate date) {
        // 发送日（用于文件名/邮件主题）
        String today = date.format(DTF);
        // 入参日期：查询=前一天，环比对比=前天，同比对比=去年同日
        LocalDate queryDate = date.minusDays(1);
        LocalDate momDate   = date.minusDays(2);
        LocalDate yoyDate   = date.minusYears(1);
        String q = queryDate.format(DTF);   // 查询日期（开始=结束=前一天）
        String m = momDate.format(DTF);     // 环比对比日期（前天）
        String y = yoyDate.format(DTF);     // 同比对比日期（去年同日）

        // ===== 1. 取数（6 次查询，与前端页面一致）=====
        // 前 3 次：对比日期=前天 → 环比增长率
        // 后 3 次：对比日期=去年同日 → 同比增长率
        List<Map<String, Object>> detail    = query(q, q, m, m, "3");
        List<Map<String, Object>> detailYoY = query(q, q, y, y, "3");
        List<Map<String, Object>> lv2       = query(q, q, m, m, "2");
        List<Map<String, Object>> lv1       = query(q, q, m, m, "");
        List<Map<String, Object>> lv2YoY    = query(q, q, y, y, "2");
        List<Map<String, Object>> lv1YoY    = query(q, q, y, y, "");

        // ===== 2. 组装表格行（与前端 tableData 一致）=====
        List<Row> rows = buildRows(detail, detailYoY, lv2, lv1, lv2YoY, lv1YoY);

        log.info("[销售日报] 开始生成 {} 报表（查询 {} / 环比 {} / 同比 {}），输出目录: {}",
                today, q, m, y, new File(outputDir).getAbsolutePath());

        // ===== 3. 生成 HTML =====
        try {
            ensureOutputDir();
        } catch (Exception e) {
            return "创建输出目录失败: " + e.getMessage();
        }
        String htmlPath = outputDir + "/sales-detail-" + today + ".html";
        try {
            Files.write(Paths.get(htmlPath), buildHtml(q, m, y, rows).getBytes(StandardCharsets.UTF_8));
            log.info("[销售日报] HTML 已生成: {}", new File(htmlPath).getAbsolutePath());
        } catch (Exception e) {
            return "生成 HTML 报表失败: " + e.getMessage();
        }

        // ===== 4. 截图 =====
        String pngPath = outputDir + "/sales-detail-" + today + ".png";
        boolean shotOk = screenshot(htmlPath, pngPath, rows.size());

        log.info("[销售日报] 报表生成完成，共 {} 行数据，截图: {} ({})",
                rows.size(), pngPath, shotOk ? "成功" : "失败");
        return shotOk ? pngPath : null;
    }

    // ==================== 查询 ====================

    private List<Map<String, Object>> query(String startDate, String endDate,
                                            String cmpStartDate, String cmpEndDate,
                                            String deptLevels) {
        return salesDetailService.getSalesDetail(
                TENANT_ID, LANG, USER_NO,
                DATE_TYPE, startDate, endDate,
                cmpStartDate, cmpEndDate,
                SHOW_STORE, deptLevels, "", SHOW_BRAND,
                orgCode, "", "", "", "",
                "0", "否", "显示日期");
    }

    // ==================== 行数据组装（与前端 SalesDetail.vue 一致）====================

    private List<Row> buildRows(List<Map<String, Object>> detail, List<Map<String, Object>> detailYoY,
                                List<Map<String, Object>> lv2, List<Map<String, Object>> lv1,
                                List<Map<String, Object>> lv2YoY, List<Map<String, Object>> lv1YoY) {
        // 同比数据按「部门编码3」映射（与前端 yoyByCode 一致）
        Map<String, Map<String, Object>> yoyByCode = new HashMap<>();
        for (Map<String, Object> r : detailYoY) {
            Object code = r.get("部门编码3");
            if (code != null && !yoyByCode.containsKey(String.valueOf(code))) {
                yoyByCode.put(String.valueOf(code), r);
            }
        }

        List<Row> result = new ArrayList<>();
        List<Row> allDetail = new ArrayList<>();

        for (String g : DEPT_GROUPS) {
            List<Row> groupRows = new ArrayList<>();
            for (Map<String, Object> ar : detail) {
                Object deptCodeObj = ar.get("部门编码3");
                String deptCode = deptCodeObj == null ? null : String.valueOf(deptCodeObj);
                if (!g.equals(getDeptGroup(deptCode))) {
                    continue;
                }
                Map<String, Object> y = deptCode != null ? yoyByCode.get(deptCode) : null;
                Row row = new Row();
                row.orgName = str(ar.get("机构名称"));
                row.deptGroup = g;
                row.deptCode = deptCode;
                row.deptName = str(ar.get("部门名称3"));
                row.salesAmount = num(ar.get("销售金额"));
                row.salesMoM = pct2(ar.get("销售额增长率"));
                row.profitAmount = num(ar.get("含税毛利"));
                row.profitMoM = pct2(ar.get("毛利额增长率"));
                row.profitRate = pct2(ar.get("毛利率"));
                row.customers = num(ar.get("交易笔数"));
                row.customerMoM = momRate(ar.get("交易笔数"), ar.get("对期交易笔数"));
                row.avgPrice = num(ar.get("客单价"));
                row.avgPriceMoM = momRate(ar.get("客单价"), ar.get("对期客单价"));
                if (y != null) {
                    row.salesYoY = pct2(y.get("销售额增长率"));
                    row.profitYoY = pct2(y.get("毛利额增长率"));
                    row.customerYoY = momRate(y.get("交易笔数"), y.get("对期交易笔数"));
                    row.avgPriceYoY = momRate(y.get("客单价"), y.get("对期客单价"));
                }
                groupRows.add(row);
                allDetail.add(row);
            }
            if (groupRows.isEmpty()) {
                continue;
            }
            result.addAll(groupRows);

            // 部门合计：优先取 deptLevels=2 同名部门行，找不到则累加回退
            Map<String, Object> src = findRow(lv2, "部门名称2", g);
            Map<String, Object> yoySrc = findRow(lv2YoY, "部门名称2", g);
            Row subtotal = src != null ? buildRowFromApi(src, g + " 合计", yoySrc)
                                       : buildSubtotalBySum(groupRows, g);
            subtotal.subtotal = true;
            result.add(subtotal);
        }

        // 超市总计：优先取 deptLevels=1 中「超市」行，找不到则取第一行/累加回退
        Map<String, Object> src = findRow(lv1, "部门名称1", "超市");
        if (src == null && lv1 != null && !lv1.isEmpty()) {
            src = lv1.get(0);
        }
        Map<String, Object> yoySrc = findRow(lv1YoY, "部门名称1", "超市");
        if (yoySrc == null && lv1YoY != null && !lv1YoY.isEmpty()) {
            yoySrc = lv1YoY.get(0);
        }
        Row total = src != null ? buildRowFromApi(src, "超市总计", yoySrc)
                                : buildTotalBySum(allDetail);
        total.total = true;
        result.add(total);
        return result;
    }

    /** 按部门编码前2位推导部组名称；未知前缀返回 null（该行不展示） */
    private String getDeptGroup(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        String prefix = code.substring(0, Math.min(2, code.length()));
        switch (prefix) {
            case "11": return "生鲜一部";
            case "12": return "生鲜二部";
            case "13": return "食品部";
            case "15": return "非食部";
            default: return null;
        }
    }

    /** 从接口行构建展示行（前端 buildRowFromApi） */
    private Row buildRowFromApi(Map<String, Object> ar, String deptName, Map<String, Object> yoyAr) {
        Row row = new Row();
        row.deptName = deptName;
        row.salesAmount = num(ar.get("销售金额"));
        row.salesYoY = yoyAr != null ? pct2(yoyAr.get("销售额增长率")) : null;
        row.salesMoM = pct2(ar.get("销售额增长率"));
        row.profitAmount = num(ar.get("含税毛利"));
        row.profitYoY = yoyAr != null ? pct2(yoyAr.get("毛利额增长率")) : null;
        row.profitMoM = pct2(ar.get("毛利额增长率"));
        row.profitRate = pct2(ar.get("毛利率"));
        row.customers = num(ar.get("交易笔数"));
        row.customerYoY = yoyAr != null ? momRate(yoyAr.get("交易笔数"), yoyAr.get("对期交易笔数")) : null;
        row.customerMoM = momRate(ar.get("交易笔数"), ar.get("对期交易笔数"));
        row.avgPrice = num(ar.get("客单价"));
        row.avgPriceYoY = yoyAr != null ? momRate(yoyAr.get("客单价"), yoyAr.get("对期客单价")) : null;
        row.avgPriceMoM = momRate(ar.get("客单价"), ar.get("对期客单价"));
        return row;
    }

    /** 明细行累加计算部门小计（前端 buildSubtotalBySum） */
    private Row buildSubtotalBySum(List<Row> rows, String g) {
        double curSales = sum(rows, "salesAmount");
        double curProfit = sum(rows, "profitAmount");
        double curCustomers = sum(rows, "customers");

        double priorYoY_Sales = 0, priorYoY_Profit = 0, priorYoY_Customers = 0;
        double priorMoM_Sales = 0, priorMoM_Profit = 0, priorMoM_Customers = 0;
        for (Row r : rows) {
            priorYoY_Sales    += priorValue(r.salesAmount, r.salesYoY);
            priorYoY_Profit   += priorValue(r.profitAmount, r.profitYoY);
            priorYoY_Customers += priorValue(r.customers, r.customerYoY);
            priorMoM_Sales    += priorValue(r.salesAmount, r.salesMoM);
            priorMoM_Profit   += priorValue(r.profitAmount, r.profitMoM);
            priorMoM_Customers += priorValue(r.customers, r.customerMoM);
        }

        Row row = new Row();
        row.deptName = g + " 合计";
        row.salesAmount = curSales;
        row.salesYoY = hasAny(rows, "salesYoY") ? calcRate(curSales, priorYoY_Sales) : null;
        row.salesMoM = calcRate(curSales, priorMoM_Sales);
        row.profitAmount = curProfit;
        row.profitYoY = hasAny(rows, "profitYoY") ? calcRate(curProfit, priorYoY_Profit) : null;
        row.profitMoM = calcRate(curProfit, priorMoM_Profit);
        row.profitRate = curSales > 0 ? round2(curProfit / curSales * 100) : 0.0;
        row.customers = curCustomers;
        row.customerYoY = hasAny(rows, "customerYoY") ? calcRate(curCustomers, priorYoY_Customers) : null;
        row.customerMoM = calcRate(curCustomers, priorMoM_Customers);
        double avgPrice = curCustomers > 0 ? curSales / curCustomers : 0;
        row.avgPrice = avgPrice;
        double priorAvgPriceYoY = priorYoY_Customers > 0 ? priorYoY_Sales / priorYoY_Customers : 0;
        double priorAvgPriceMoM = priorMoM_Customers > 0 ? priorMoM_Sales / priorMoM_Customers : 0;
        row.avgPriceYoY = hasAny(rows, "avgPriceYoY") && priorAvgPriceYoY > 0 ? calcRate(avgPrice, priorAvgPriceYoY) : null;
        row.avgPriceMoM = priorAvgPriceMoM > 0 ? calcRate(avgPrice, priorAvgPriceMoM) : 0.0;
        return row;
    }

    /** 明细行累加计算超市总计（前端 buildTotalBySum） */
    private Row buildTotalBySum(List<Row> detailRows) {
        List<Row> rows = detailRows == null ? new ArrayList<>() : detailRows;
        Row row = buildSubtotalBySum(rows, "超市总计");
        row.subtotal = false;
        row.total = true;
        return row;
    }

    // ==================== 数值工具（与前端一致）====================

    private Double num(Object v) {
        if (v == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(String.valueOf(v).trim());
            return Double.isNaN(d) ? null : d;
        } catch (Exception e) {
            return null;
        }
    }

    /** pct2：保留两位小数的百分比数值 */
    private Double pct2(Object v) {
        Double n = num(v);
        return n == null ? null : round2(n);
    }

    /** momRate：(本期 - 对期) ÷ |对期| × 100 */
    private Double momRate(Object cur, Object prior) {
        Double c = num(cur);
        Double p = num(prior);
        if (c == null || p == null) {
            return null;
        }
        return calcRate(c, p);
    }

    private Double calcRate(double cur, double prior) {
        if (prior == 0) {
            return 0.0;
        }
        return round2((cur - prior) / Math.abs(prior) * 100);
    }

    private Double priorValue(Double current, Double rate) {
        if (rate == null) {
            return current;
        }
        return current / (1 + rate / 100);
    }

    private boolean hasAny(List<Row> rows, String field) {
        for (Row r : rows) {
            Object v = r.get(field);
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    private double sum(List<Row> rows, String field) {
        double s = 0;
        for (Row r : rows) {
            Double v = (Double) r.get(field);
            if (v != null) {
                s += v;
            }
        }
        return s;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Map<String, Object> findRow(List<Map<String, Object>> list, String field, String value) {
        if (list == null) {
            return null;
        }
        for (Map<String, Object> r : list) {
            Object v = r.get(field);
            if (v != null && String.valueOf(v).equals(value)) {
                return r;
            }
        }
        return null;
    }

    // ==================== 行模型 ====================

    /** 表格行（与前端 tableData 行结构一致） */
    public static class Row {
        String orgName;
        String deptGroup;
        String deptCode;
        String deptName;
        Double salesAmount, salesYoY, salesMoM;
        Double profitAmount, profitYoY, profitMoM;
        Double profitRate;
        Double customers, customerYoY, customerMoM;
        Double avgPrice, avgPriceYoY, avgPriceMoM;
        boolean subtotal;
        boolean total;

        Object get(String field) {
            switch (field) {
                case "salesAmount": return salesAmount;
                case "salesYoY": return salesYoY;
                case "salesMoM": return salesMoM;
                case "profitAmount": return profitAmount;
                case "profitYoY": return profitYoY;
                case "profitMoM": return profitMoM;
                case "profitRate": return profitRate;
                case "customers": return customers;
                case "customerYoY": return customerYoY;
                case "customerMoM": return customerMoM;
                case "avgPrice": return avgPrice;
                case "avgPriceYoY": return avgPriceYoY;
                case "avgPriceMoM": return avgPriceMoM;
                default: return null;
            }
        }
    }

    // ==================== HTML 生成 ====================

    private String buildHtml(String queryDate, String momDate, String yoyDate, List<Row> rows) {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
          .append("<title>部门销售详情</title>\n<style>\n")
          .append("body{margin:0;padding:0;background:#fff;font-family:'Microsoft YaHei','Segoe UI',sans-serif;font-size:12px;color:#333;}\n")
          .append(".page{padding:16px 20px 24px;}\n")
          .append(".header{text-align:center;padding:14px 0 12px;border-bottom:1px solid #e8e8e8;background:linear-gradient(135deg,#fff8e1,#fffde7);}\n")
          .append(".header h2{margin:0 0 8px;font-size:18px;font-weight:700;color:#b71c1c;letter-spacing:1px;}\n")
          .append(".date{display:inline-block;font-size:12px;padding:3px 10px;border-radius:4px;margin:0 6px;background:#e3f2fd;color:#1565c0;font-weight:500;}\n")
          .append(".date.mom{background:#fff3e0;color:#e65100;}\n")
          .append(".date.yoy{background:#e8f5e9;color:#2e7d32;}\n")
          .append("table{width:100%;border-collapse:collapse;margin-top:12px;white-space:nowrap;}\n")
          .append("th{padding:8px 6px;border:1px solid #d0d0d0;text-align:center;background:#f5f5f5;color:#333;font-weight:600;}\n")
          .append("td{padding:7px 6px;border:1px solid #e8e8e8;text-align:center;}\n")
          .append("tr.odd td{background:#fafafa;}\n")
          .append(".col-name{text-align:left !important;padding-left:10px !important;}\n")
          .append(".col-merge{text-align:center !important;font-weight:700;font-size:14px;letter-spacing:2px;}\n")
          .append(".c-sales{background:#fff3e0 !important;}\n")
          .append(".c-profit{background:#e8f5e9 !important;}\n")
          .append(".c-customer{background:#e3f2fd !important;}\n")
          .append(".c-price{background:#fce4ec !important;}\n")
          .append("tr.subtotal td{background:linear-gradient(90deg,#fff59d,#fff9c4);border-top:2px solid #f57f17;border-bottom:2px solid #f57f17;color:#4e342e;font-weight:700;}\n")
          .append("tr.total td{background:linear-gradient(90deg,#ffd54f,#ffca28);border-top:2px solid #e65100;border-bottom:3px double #bf360c;color:#3e2723;font-weight:800;}\n")
          .append(".rate-up{color:#d32f2f;font-weight:500;}\n")
          .append(".rate-down{color:#388e3c;font-weight:500;}\n")
          .append("</style>\n</head>\n<body>\n<div class=\"page\">\n")
          .append("<div class=\"header\"><h2>部门销售详情</h2><div>\n")
          .append("<span class=\"date\">查询日期：").append(queryDate).append(" ~ ").append(queryDate).append("</span>\n")
          .append("<span class=\"date mom\">环比对比：").append(momDate).append(" ~ ").append(momDate).append("</span>\n")
          .append("<span class=\"date yoy\">同比对比：").append(yoyDate).append(" ~ ").append(yoyDate).append("</span>\n")
          .append("</div></div>\n");

        // 表头（与前端页面 17 列一致）
        sb.append("<table>\n<thead><tr>")
          .append("<th>机构名称</th><th>部组名称</th><th>部门编码</th><th>部门名称</th>")
          .append("<th class=\"c-sales\">销售额/元</th><th class=\"c-sales\">同比销售额增长率</th><th class=\"c-sales\">环比销售额增长率</th>")
          .append("<th class=\"c-profit\">毛利额/元</th><th class=\"c-profit\">同比毛利额增长率</th><th class=\"c-profit\">环比毛利额增长率</th>")
          .append("<th class=\"c-profit\">毛利率</th><th class=\"c-customer\">来客数</th><th class=\"c-customer\">同比来客数增长率</th><th class=\"c-customer\">环比来客数增长率</th>")
          .append("<th class=\"c-price\">客单价/元</th><th class=\"c-price\">同比客单价增长率</th><th class=\"c-price\">环比客单价增长率</th>")
          .append("</tr></thead>\n<tbody>\n");

        int idx = 0;
        for (Row r : rows) {
            idx++;
            if (r.subtotal) {
                sb.append("<tr class=\"subtotal\"><td colspan=\"4\" class=\"col-merge\">").append(esc(r.deptName)).append("</td>");
            } else if (r.total) {
                sb.append("<tr class=\"total\"><td colspan=\"4\" class=\"col-merge\">").append(esc(r.deptName)).append("</td>");
            } else {
                sb.append(idx % 2 == 0 ? "<tr class=\"odd\">" : "<tr>")
                  .append("<td>").append(esc(r.orgName)).append("</td>")
                  .append("<td>").append(esc(r.deptGroup)).append("</td>")
                  .append("<td>").append(esc(r.deptCode)).append("</td>")
                  .append("<td class=\"col-name\">").append(esc(r.deptName)).append("</td>");
            }
            appendNumTd(sb, "c-sales", r.salesAmount);
            appendRateTd(sb, "c-sales", r.salesYoY);
            appendRateTd(sb, "c-sales", r.salesMoM);
            appendNumTd(sb, "c-profit", r.profitAmount);
            appendRateTd(sb, "c-profit", r.profitYoY);
            appendRateTd(sb, "c-profit", r.profitMoM);
            appendPctTd(sb, "c-profit", r.profitRate);
            appendNumTd(sb, "c-customer", r.customers);
            appendRateTd(sb, "c-customer", r.customerYoY);
            appendRateTd(sb, "c-customer", r.customerMoM);
            appendNumTd(sb, "c-price", r.avgPrice);
            appendRateTd(sb, "c-price", r.avgPriceYoY);
            appendRateTd(sb, "c-price", r.avgPriceMoM);
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

    private void appendRateTd(StringBuilder sb, String cls, Double v) {
        sb.append("<td class=\"").append(cls).append(' ').append(rateClass(v)).append("\">")
          .append(fmtRate(v))
          .append("</td>");
    }

    private void appendPctTd(StringBuilder sb, String cls, Double v) {
        sb.append("<td class=\"").append(cls).append(' ').append(rateClass(v)).append("\">")
          .append(v == null ? "" : v + "%")
          .append("</td>");
    }

    private String fmtRate(Double v) {
        if (v == null) {
            return "";
        }
        return (v > 0 ? "+" : "") + String.format(Locale.US, "%.2f", v) + "%";
    }

    private String rateClass(Double v) {
        if (v == null) {
            return "";
        }
        if (v > 0) {
            return "rate-up";
        }
        if (v < 0) {
            return "rate-down";
        }
        return "";
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ==================== 截图 ====================

    boolean screenshot(String htmlPath, String pngPath, int rowCount) {
        String browser = resolveBrowser();
        if (browser == null) {
            return false;
        }
        // 动态计算截图高度：标题区（含三组日期）+ 表头 + 行数×行高，保证整页截全
        int height = Math.max(640, 300 + rowCount * 30);
        // 每次用全新的临时 user-data-dir：既避免 profile 锁定，也避免与用户桌面已开
        // 的 Chrome 主实例冲突（否则 headless 会把任务委托给主实例然后立即退出）
        Path profileDir = null;
        List<String> chromeArgs = new ArrayList<>();
        chromeArgs.add(browser);
        chromeArgs.add("--headless");
        chromeArgs.add("--disable-gpu");
        chromeArgs.add("--hide-scrollbars");
        // 受限/无控制台环境（如后台 Java 服务）中，Chrome 沙箱可能无法启动，关闭沙箱以保证截图稳定
        chromeArgs.add("--no-sandbox");
        chromeArgs.add("--disable-setuid-sandbox");
        chromeArgs.add("--disable-dev-shm-usage");
        chromeArgs.add("--no-first-run");
        chromeArgs.add("--disable-extensions");
        try {
            profileDir = Files.createTempDirectory("chrome-profile-");
            chromeArgs.add("--user-data-dir=" + profileDir.toAbsolutePath().toString());
        } catch (Exception e) {
            log.warn("[销售日报] 创建临时 user-data-dir 失败，使用默认目录: {}", e.getMessage());
            chromeArgs.add("--user-data-dir=" + new File(outputDir, "chrome-profile").getAbsolutePath());
        }
        chromeArgs.add("--window-size=1900," + height);
        chromeArgs.add("--screenshot=" + new File(pngPath).getAbsolutePath());
        chromeArgs.add("file:///" + new File(htmlPath).getAbsolutePath().replace("\\", "/"));
        log.info("[销售日报] 截图命令: {}", String.join(" ", chromeArgs));
        Process process = null;
        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(chromeArgs);
            // 丢弃子进程输出，避免管道缓冲阻塞（渲染日志不影响截图结果）
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            process = pb.start();
            // Windows 下 chrome.exe 是 GUI 启动器：启动器进程会立即退出（exit 0），
            // 真正的渲染发生在它派生的子进程里，PNG 异步写入。
            // 因此不依赖进程退出码，改为轮询 PNG 文件是否生成，最长等待 90 秒。
            File png = new File(pngPath);
            boolean ok = false;
            long deadline = System.currentTimeMillis() + 90_000;
            while (System.currentTimeMillis() < deadline) {
                if (png.exists() && png.length() > 0) {
                    ok = true;
                    break;
                }
                Thread.sleep(500);
            }
            long elapsed = System.currentTimeMillis() - start;
            log.info("[销售日报] 截图结果: exists={}, size={}, 耗时: {}ms",
                    png.exists(), png.length(), elapsed);
            if (!ok) {
                log.warn("[销售日报] 截图超时（90 秒内未生成 PNG）");
            }
            return ok;
        } catch (Exception e) {
            log.error("[销售日报] 截图异常", e);
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (profileDir != null) {
                try {
                    deleteDir(profileDir.toFile());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    private String resolveBrowser() {
        if (chromePath != null && !chromePath.trim().isEmpty()) {
            log.info("[销售日报] 使用配置浏览器: {}", chromePath.trim());
            return chromePath.trim();
        }
        String[] candidates = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        };
        for (String c : candidates) {
            boolean exists = new File(c).exists();
            log.info("[销售日报] 探测浏览器 {} 是否存在: {}", c, exists);
            if (exists) {
                return c;
            }
        }
        // 备用：通过 where 命令在 PATH 中查找
        String where = findByWhere("chrome.exe", "msedge.exe");
        if (where != null) {
            log.info("[销售日报] 通过 where 命令找到浏览器: {}", where);
            return where;
        }
        log.warn("[销售日报] 未找到 Chrome/Edge，请在 application.yml 配置 report.daily.chrome-path");
        return null;
    }

    private String findByWhere(String... names) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "where " + String.join(" ", names));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.toLowerCase().endsWith(".exe") && new File(line).exists()) {
                        return line;
                    }
                }
            }
            p.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[销售日报] where 命令查找浏览器失败: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 邮件发送 ====================

    public String sendMail(String today, List<String> pngPaths) {
        if (mailTo == null || mailTo.trim().isEmpty()) {
            return "未配置收件人（report.daily.mail-to），跳过邮件发送。\n";
        }
        if (mailSender == null || mailAuthCode == null || mailAuthCode.trim().isEmpty()) {
            return "未配置 SMTP 授权码（环境变量 MAIL_AUTH_CODE 或 spring.mail.password），跳过邮件发送。\n";
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(mailTo);
            helper.setSubject("销售详情日报 " + today);
            helper.setText("附件为 " + today + " 的销售详情报表截图（销售详情1 + 销售详情2），请查收。", false);
            for (String pngPath : pngPaths) {
                File f = new File(pngPath);
                if (f.exists()) {
                    helper.addAttachment(f.getName(), f);
                }
            }
            mailSender.send(msg);
            return "邮件已发送至 " + mailTo + "，附件 " + pngPaths.size() + " 张。\n";
        } catch (Exception e) {
            return "邮件发送失败: " + e.getMessage() + "\n";
        }
    }

    void ensureOutputDir() throws Exception {
        File dir = new File(outputDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + dir.getAbsolutePath());
        }
    }
}
