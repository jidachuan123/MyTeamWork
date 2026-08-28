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
 * 按机构编码前四位分组（1101/1102/1103/1104…，前四位相同即同组，升序），组内各店求和，组尾出「合计」行。
 *
 * 日期规则：本期=前一天、环比=前两天、同比=去年本期就近同周几
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
     * 日期由 date 推算（本期=前一天、环比=前两天、同比=去年本期就近同周几），部门层级/部门编码留空走后端默认口径。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(LocalDate date) {
        return generateDailyReport(date, orgCode2, null);
    }

    /**
     * 生成某天销售详情2 截图，机构编码可覆盖、截图文件名可追加标识。
     * 用于「副邮件/多机构」场景：orgCodeOverride 指定机构（如 1103,1104），
     * fileTag 追加到 HTML/PNG 文件名（如 -1103-1104），避免同日不同机构截图文件名冲突。
     * 日期规则与 generateDailyReport(date) 完全一致（本期=前一天、环比=前两天、同比=去年本期就近同周几）。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(LocalDate date, String orgCodeOverride, String fileTag) {
        return generateDailyReport(date, orgCodeOverride, fileTag, null);
    }

    /**
     * 生成某天销售详情2 截图，机构编码可覆盖、截图文件名可追加标识，并可追加「仅库存」门店。
     * 用于「副邮件/多机构」场景：orgCodeOverride 指定机构（如 1103,1104），
     * fileTag 追加到 HTML/PNG 文件名（如 -1103-1104），避免同日不同机构截图文件名冲突。
     * unionStockCodes：逗号分隔机构编码，这些门店即使引擎查询未返回（无销售）也会补进截图，
     * 仅带库存三列（机构编码/机构名称/当日库存金额），其余指标按 0 处理（UNION ALL 语义，见 mergeStockOnlyRows）。
     * 日期规则与 generateDailyReport(date) 完全一致（本期=前一天、环比=前两天、同比=去年本期就近同周几）。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(LocalDate date, String orgCodeOverride, String fileTag, String unionStockCodes) {
        DailyReportParam req = new DailyReportParam();
        req.setOrgCode(orgCodeOverride);
        req.setDeptLevels("");
        req.setDepartment("");
        req.setStartDate(date.minusDays(1).format(DTF));
        req.setEndDate(date.minusDays(1).format(DTF));
        req.setCmpStartDate(date.minusDays(2).format(DTF));
        req.setCmpEndDate(date.minusDays(2).format(DTF));
        LocalDate yoyDate = calcYoySameDow(date.minusDays(1));  // 同比 = 去年本期就近同周几
        req.setYoyStartDate(yoyDate.format(DTF));
        req.setYoyEndDate(yoyDate.format(DTF));
        return generateDailyReport(req, fileTag, unionStockCodes);
    }

    /**
     * 同比日期 = 去年本期的今天（本期−1年），就近取与本期同周几的最近一天。
     * 例：本期 2026-08-27 周四 → 去年本期 2025-08-27 周三 → 最近周四 = 2025-08-28。
     * 无平局：偏移 d∈[0,6]，d≤3 往后取，d≥4 往前取。
     */
    private LocalDate calcYoySameDow(LocalDate cur) {
        LocalDate base = cur.minusYears(1);
        int d = (cur.getDayOfWeek().getValue() - base.getDayOfWeek().getValue() + 7) % 7;
        return d <= 3 ? base.plusDays(d) : base.minusDays(7 - d);
    }

    /**
     * 按页面查询参数生成销售详情2 截图（手动触发用）。
     * 日期 / 机构编码 / 部门层级 / 部门编码全部来自前端传入的 queryForm，不再写死。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(DailyReportParam req) {
        return generateDailyReport(req, null, null);
    }

    /**
     * 按页面查询参数生成销售详情2 截图；fileTag 非空时追加到 HTML/PNG 文件名。
     * fileTag 用于区分同日不同机构的截图（如 -1103-1104），避免文件名冲突被覆盖。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(DailyReportParam req, String fileTag) {
        return generateDailyReport(req, fileTag, null);
    }

    /**
     * 按页面查询参数生成销售详情2 截图；fileTag 非空时追加到 HTML/PNG 文件名；
     * unionStockCodes 非空时，取数后 UNION ALL 追加「仅库存」门店（见 mergeStockOnlyRows）。
     *
     * @return 截图 PNG 路径（失败返回 null）
     */
    public String generateDailyReport(DailyReportParam req, String fileTag, String unionStockCodes) {
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

        // ===== 1.5 副收件人特例：UNION ALL 追加「仅库存」门店 =====
        // 引擎查询只返回有销售的门店；unionStockCodes 指定的门店（如 1104901/1103801）即使无销售
        // 也要出现在截图里（仅机构编码/机构名称/当日库存金额三列，其余列按 NULL=0 处理）。
        // 语义等同 UNION ALL + 按机构编码聚合：引擎已返回的门店不重复追加（防同源库存翻倍）。
        if (unionStockCodes != null && !unionStockCodes.trim().isEmpty()) {
            mergeStockOnlyRows(momData, yoyData, unionStockCodes);
        }

        // ===== 2. 组装表格行 =====
        List<StoreRow> rows = buildRows(momData, yoyData);

        // ===== 3. 生成 HTML =====
        try {
            reportService.ensureOutputDir();
        } catch (Exception e) {
            log.error("[销售详情2] 创建输出目录失败", e);
            return null;
        }
        String tag = (fileTag != null && !fileTag.trim().isEmpty()) ? "-" + fileTag.trim() : "";
        String htmlPath = outputDir + "/sales-detail2-" + today + tag + ".html";
        try {
            Files.write(Paths.get(htmlPath), buildHtml(q, m, y, rows).getBytes(StandardCharsets.UTF_8));
            log.info("[销售详情2] HTML 已生成: {}", new File(htmlPath).getAbsolutePath());
        } catch (Exception e) {
            log.error("[销售详情2] 生成 HTML 失败", e);
            return null;
        }

        // ===== 4. 截图 =====
        String pngPath = outputDir + "/sales-detail2-" + today + tag + ".png";
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

    /**
     * UNION ALL 追加「仅库存」门店（副收件人销售详情2 截图专用）。
     *
     * 补充 SQL 只返回 机构编码 / 机构名称 / 当日库存金额 三列（与引擎最终 SELECT 列名一致），
     * 其余列未设置，buildRows 取数时自动按 0/NULL 处理 —— 等价于用户要求的
     * 「引擎 SQL UNION ALL 补充 SQL，其余列用 NULL 补齐」。
     *
     * 防翻倍：补充库存来自 tb_wb_gdsstock 的 SUM(c_at_cost)，与引擎最终 SELECT 的 mykucun
     * 库存子查询同源同值；因此引擎已返回过的门店（本期或同比出现）跳过，只补真正缺失的门店。
     *
     * @param momData 环比查询结果（补充行追加到这里，进入 buildRows 的 mom 循环：hasStock=true、按全 0 门店展示）
     * @param yoyData 同比查询结果（仅用于收集已存在机构，避免补重复行）
     * @param codes   逗号分隔机构编码，如 "1104901,1103801"
     */
    private void mergeStockOnlyRows(List<Map<String, Object>> momData, List<Map<String, Object>> yoyData, String codes) {
        try {
            // 已存在机构编码集合（引擎已返回的门店跳过，防止同 code 库存翻倍）
            Set<String> exists = new HashSet<>();
            for (Map<String, Object> r : momData) {
                String c = str(r.get("机构编码"));
                if (!c.isEmpty()) exists.add(c);
            }
            for (Map<String, Object> r : yoyData) {
                String c = str(r.get("机构编码"));
                if (!c.isEmpty()) exists.add(c);
            }

            List<Map<String, Object>> extra = salesDetailService.getStockOnlyRows(codes);
            int added = 0;
            for (Map<String, Object> r : extra) {
                String c = str(r.get("机构编码"));
                if (c.isEmpty() || exists.contains(c)) continue;
                momData.add(r);
                exists.add(c);
                added++;
            }
            if (added > 0) {
                log.info("[销售详情2] UNION ALL 追加仅库存门店 {} 家（{}），截图将包含这些门店的库存行", added, codes);
            } else {
                log.info("[销售详情2] UNION ALL 补充门店无新增（{} 均已存在或查无数据）", codes);
            }
        } catch (Exception e) {
            // 补充行失败不影响主报表（与页面查询一致），仅记录
            log.error("[销售详情2] UNION ALL 追加仅库存门店失败（codes={}），忽略继续", codes, e);
        }
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
        // 注意：当日库存金额为 0 的门店【不参与展示但必须参与计算】——它们的销售额/毛利额等
        // 仍要累加进合计并参与百分比计算（用户明确要求"隐藏而非过滤"），渲染层负责隐藏（见 buildHtml）
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
        // 按机构编码升序（前四位相同即同组，升序后同组自然连续）
        storeRows.sort(Comparator.comparing(r -> String.valueOf(r.orgCode)));

        // 按组（前四位）分段 + 组尾合计行：组 = 出现过的前四位，升序（如 1101、1102、1103、1104、1191…）
        List<StoreRow> result = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        for (StoreRow r : storeRows) {
            if (!groups.contains(r.group)) groups.add(r.group);
        }
        Collections.sort(groups);
        for (String g : groups) {
            List<StoreRow> grp = new ArrayList<>();
            for (StoreRow r : storeRows) {
                if (g.equals(r.group)) grp.add(r);
            }
            if (!grp.isEmpty()) {
                result.addAll(grp);
                result.add(buildSubtotal(grp));
            }
        }
        return result;
    }

    /** 机构分组：按机构编码前四位归类（1101/1102/1103/1104…，前四位相同即同组）。与前端 SalesDetail2.vue 同源 */
    private String getGroup(String code) {
        if (code == null) return "其他";
        return code.length() >= 4 ? code.substring(0, 4) : code;
    }

    /** 合计行：金额求和，派生指标按合计值公式计算。合计行统一只叫「合计」（不带组名前缀） */
    private StoreRow buildSubtotal(List<StoreRow> rows) {
        StoreRow s = new StoreRow("");
        s.isSubtotal = true;
        s.orgName = "合计";
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
            // 当日库存金额为 0 的门店：不生成行（截图里隐藏），但数据已参与合计/派生计算（与前端 v-show 同源隐藏）
            if (!r.isSubtotal && r.stockAmount <= 0) continue;
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
