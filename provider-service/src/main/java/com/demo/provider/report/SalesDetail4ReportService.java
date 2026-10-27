package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * 销售详情4（SD4，配送调拨汇总表）截图服务 —— 2026-10-16 新建。
 *
 * ★ 与 SalesDetail4Service 共用同一套取数结果，因此「截图 = 页面」严格一致。
 * ★ 截图能力复用 SalesDailyReportService 的 screenshot()（同包可见），不重复造轮子；
 *   配色沿用 SD1/SD3 规范（表头白底黑框、数据行白底）。
 * ★ 原 SD1/SD2/SD3 的所有文件均未改动。
 */
@Service
public class SalesDetail4ReportService {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail4ReportService.class);

    /**
     * 截图画布宽度（2026-10-27）。
     *
     * ★ 为什么是 1350 而不是原来的 1900：企业微信/微信里看图按「宽度自适应」缩放，
     *   1900px 的画布缩到手机 390pt 宽只剩 1/4.9，14px 的字实际只有 2.9px。
     *   1350px 是本表 9 列在 18px 字号下刚好装下的宽度（列宽合计 1318 + 左右 padding 32），
     *   同样的内容手机上能放大到 5.2px，配合 10px 行内边距，截图在电脑上看也更清晰。
     * ★ 列宽写死在 buildHtml() 的 colgroup 里，两者必须同步改。
     */
    private static final int SCREENSHOT_WIDTH = 1350;

    /** 每行高度估算（td 18px + padding 10px×2 + border，实际约 45px，留余量给机构名折行） */
    private static final int SCREENSHOT_ROW_HEIGHT = 56;

    @Autowired
    private SalesDetail4Service sd4Service;

    /** 复用 SD1 的截图与目录创建能力（同包可见，未修改其源码） */
    @Autowired
    private SalesDailyReportService reportService;

    @Autowired
    private Sd4Properties props;

    @Value("${sd4.output-dir:target/report}")
    private String outputDir;

    /** 千分位格式（截图展示用） */
    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    static {
        NF.setGroupingUsed(true);
        NF.setMinimumFractionDigits(2);
        NF.setMaximumFractionDigits(2);
    }

    /**
     * 按指定条件生成截图。
     *
     * @param accStartDate 记账日期开始
     * @param accEndDate   记账日期结束
     * @param cmpStartDate 环比日期开始
     * @param cmpEndDate   环比日期结束
     * @param yoyStartDate 同比日期开始
     * @param yoyEndDate   同比日期结束
     * @param outOrgCodes  发货机构（空=全部）
     * @param fileTag      文件名后缀（同日多张时区分，防覆盖）；可空
     * @return PNG 绝对路径；失败返回 null
     */
    public String generateReport(String accStartDate, String accEndDate,
                                 String cmpStartDate, String cmpEndDate,
                                 String yoyStartDate, String yoyEndDate,
                                 String outOrgCodes, String fileTag) {
        try {
            // ★ 自己创建 SD4 的输出目录（不要复用 SD1 的 ensureOutputDir —— 那个建的是
            //   report.daily.output-dir，与 sd4.output-dir 可能不是同一个路径）
            Files.createDirectories(Paths.get(outputDir));
        } catch (Exception e) {
            log.error("[销售详情4] 创建输出目录失败：{}", outputDir, e);
            return null;
        }

        SalesDetail4Service.Sd4Result result = sd4Service.query(
                accStartDate, accEndDate, cmpStartDate, cmpEndDate, yoyStartDate, yoyEndDate, outOrgCodes);
        List<SalesDetail4Service.Sd4Row> rows = result.getRows();

        String tag = (fileTag == null || fileTag.trim().isEmpty()) ? "" : "-" + fileTag.trim();
        String name = "sales-detail4-" + result.getAccEndDate() + tag;
        String htmlPath = outputDir + "/" + name + ".html";
        try {
            Files.write(Paths.get(htmlPath),
                    buildHtml(result, rows).getBytes(StandardCharsets.UTF_8));
            log.info("[销售详情4] HTML 已生成: {}", new File(htmlPath).getAbsolutePath());
        } catch (Exception e) {
            log.error("[销售详情4] 生成 HTML 失败", e);
            return null;
        }

        String pngPath = outputDir + "/" + name + ".png";
        // 2026-10-27：大头像化 —— 画布从 1900 收到 1350（内容刚好装下），字号 14→18、行高加大
        boolean ok = reportService.screenshot(htmlPath, pngPath, rows.size(),
                SCREENSHOT_WIDTH, SCREENSHOT_ROW_HEIGHT);
        log.info("[销售详情4] 截图{}：{}（{} 行，画布 {}px 宽）",
                ok ? "成功" : "失败", pngPath, rows.size(), SCREENSHOT_WIDTH);
        return ok ? pngPath : null;
    }

    // ==================== HTML ====================

    private String buildHtml(SalesDetail4Service.Sd4Result result, List<SalesDetail4Service.Sd4Row> rows) {
        // ★ 2026-10-16 修复：头部补齐「全部查询条件」——此前只显示记账日期+发货机构，
        //   环比日期/同比日期没截上（用户反馈企微收到的图看不到完整条件）。
        String accText = rangeText(result.getAccStartDate(), result.getAccEndDate());
        String cmpText = rangeText(result.getCmpStartDate(), result.getCmpEndDate());
        String yoyText = rangeText(result.getYoyStartDate(), result.getYoyEndDate());
        String orgText = (result.getOutOrgCodes() == null || result.getOutOrgCodes().isEmpty())
                ? "全部" : result.getOutOrgCodes();
        String title = (props.getTitle() == null || props.getTitle().trim().isEmpty())
                ? "配送调拨汇总表" : props.getTitle().trim();

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
          .append("<title>").append(esc(title)).append("</title>\n<style>\n")
          // ★ 2026-10-27 全表放大：td 14→18px、th 13→17px、行内边距 5→10px、标题 18→26px。
          //   同时画布从 1900 收到 1350（见 SCREENSHOT_WIDTH），两者配合才既大又不溢出。
          .append("body{margin:0;padding:0;background:#fff;font-family:'Microsoft YaHei','Segoe UI',sans-serif;font-size:18px;color:#333;}\n")
          .append(".page{padding:14px 16px 20px;}\n")
          .append(".header{text-align:center;padding:10px 0 12px;border-bottom:1px solid #e8e8e8;background:#fff;}\n")
          .append(".header h2{margin:0 0 10px;font-size:26px;font-weight:700;color:#b71c1c;letter-spacing:1px;}\n")
          .append(".date{display:inline-block;font-size:19px;padding:5px 12px;border-radius:4px;margin:3px 5px;background:#fff;color:#d32f2f;font-weight:700;line-height:1.4;}\n")
          .append("table{width:100%;border-collapse:collapse;margin-top:10px;table-layout:fixed;}\n")
          .append("th,td{box-sizing:border-box;white-space:nowrap;}\n")
          .append("th{padding:12px 4px;border:2px solid #000;text-align:center;background:#fff;color:#333;font-weight:700;font-size:17px;line-height:1.3;}\n")
          .append("td{padding:10px 4px;border:1px solid #000;text-align:center;font-size:18px;font-variant-numeric:tabular-nums;line-height:1.3;}\n")
          // 机构名允许折行（个别名字超过 13 字时宁可折两行，也不要用 nowrap 撑破表格）
          .append(".col-name{text-align:center !important;vertical-align:middle;white-space:normal;}\n")
          .append("td.rate-up .rate-arrow{color:#2e7d32 !important;font-weight:600;}\n")
          .append("td.rate-up .rate-num{color:#1a1a1a;font-weight:600;}\n")
          .append("td.rate-down .rate-arrow{color:#c62828 !important;font-weight:600;}\n")
          .append("td.rate-down .rate-num{color:#1a1a1a;font-weight:600;}\n")
          .append(".rate-arrow{display:inline-block;width:1em;text-align:center;}\n")
          .append(".rate-num{display:inline-block;min-width:4.6em;text-align:right;}\n")
          .append("</style>\n</head>\n<body>\n<div class=\"page\">\n")
          .append("<div class=\"header\">\n")
          .append("<h2>").append(esc(title)).append("</h2>\n")
          .append("<span class=\"date\">记账日期：").append(accText).append("</span>\n")
          .append("<span class=\"date\">环比日期：").append(cmpText).append("</span>\n")
          .append("<span class=\"date\">同比日期：").append(yoyText).append("</span>\n")
          .append("<span class=\"date\">发货机构：").append(esc(orgText)).append("</span>\n")
          .append("</div>\n")
          // 列宽合计 1318 = 画布 1350 - .page 左右 padding 32。改这里必须同步 SCREENSHOT_WIDTH。
          .append("<table>\n<colgroup>")
          .append("<col style=\"width:52px\">")     // 序号
          .append("<col style=\"width:120px\">")    // 发货机构编码
          .append("<col style=\"width:256px\">")    // 发货机构名称
          .append("<col style=\"width:148px\">")    // 当日库存金额
          .append("<col style=\"width:148px\">")    // 发货结算金额
          .append("<col style=\"width:156px\">")    // 环比发货结算金额
          .append("<col style=\"width:156px\">")    // 同比发货结算金额
          .append("<col style=\"width:141px\">")    // 环比增长率
          .append("<col style=\"width:141px\">")    // 同比增长率
          .append("</colgroup>\n<thead>\n<tr>")
          .append("<th>序号</th>")
          .append("<th>发货机构编码</th>")
          .append("<th>发货机构名称</th>")
          .append("<th>当日库存金额</th>")
          .append("<th>发货结算金额</th>")
          .append("<th>环比发货结算金额</th>")
          .append("<th>同比发货结算金额</th>")
          .append("<th>环比增长率</th>")
          .append("<th>同比增长率</th>")
          .append("</tr>\n</thead>\n<tbody>\n");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan=\"9\">当日无数据</td></tr>\n");
        } else {
            for (SalesDetail4Service.Sd4Row r : rows) {
                sb.append("<tr>")
                  .append("<td>").append(r.getSeq()).append("</td>")
                  .append("<td>").append(esc(r.getOutOrgCode())).append("</td>")
                  .append("<td class=\"col-name\">").append(esc(r.getOutOrgName())).append("</td>")
                  .append("<td>").append(fmt(r.getStockAmt())).append("</td>")
                  .append("<td>").append(fmt(r.getSendAmt())).append("</td>")
                  .append("<td>").append(fmt(r.getCmpAmt())).append("</td>")
                  .append("<td>").append(fmt(r.getYoyAmt())).append("</td>")
                  .append(rateTd(r.getCmpRate()))
                  .append(rateTd(r.getYoyRate()))
                  .append("</tr>\n");
            }
        }

        sb.append("</tbody>\n</table>\n</div>\n</body>\n</html>");
        return sb.toString();
    }

    /** 增长率单元格：涨=绿▲+正号、跌=红▼（与 SD1 截图口径完全一致），null 显示「-」 */
    private static String rateTd(BigDecimal rate) {
        if (rate == null) {
            return "<td>-</td>";
        }
        String cls = rateClass(rate);
        String clsAttr = cls.isEmpty() ? "" : " class=\"" + cls + "\"";
        return "<td" + clsAttr + ">"
                + "<span class=\"rate-arrow\">" + rateArrow(rate) + "</span>"
                + "<span class=\"rate-num\">" + rateText(rate) + "</span></td>";
    }

    /** 涨 = rate-up（绿▲）/ 跌 = rate-down（红▼）/ 0 与空 = 无色（与 SD1 一致） */
    private static String rateClass(BigDecimal v) {
        if (v.signum() > 0) {
            return "rate-up";
        }
        if (v.signum() < 0) {
            return "rate-down";
        }
        return "";
    }

    private static String rateArrow(BigDecimal v) {
        if (v.signum() > 0) {
            return "▲";
        }
        if (v.signum() < 0) {
            return "▼";
        }
        return "";
    }

    /** 增长率数值文本：正数带 + 号，2 位小数 + %（如 +154.64% / -57.89%），与 SD1 一致 */
    private static String rateText(BigDecimal v) {
        String prefix = v.signum() > 0 ? "+" : "";
        return prefix + v.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 日期区间文案：起止相同显示单日，否则「起 ~ 止」（截图头部查询条件用） */
    private static String rangeText(String start, String end) {
        String s = (start == null) ? "" : start;
        String e = (end == null) ? "" : end;
        return s.equals(e) ? s : s + " ~ " + e;
    }

    private static String fmt(BigDecimal v) {
        if (v == null) {
            return "0.00";
        }
        return NF.format(v);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
