package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 销售详情3（SD3）截图服务 —— 2026-10-15 新建。
 *
 * ★ 与 SalesDetail3Service 共用同一套聚合结果，因此「截图 = 页面」严格一致。
 * ★ 截图能力复用 SalesDailyReportService 的 screenshot()/ensureOutputDir()（同包可见），
 *   不重复造轮子；配色沿用 SD1 规范（合计行青 #B8E0DC、总计行暗黄 #F2E1A6）。
 * ★ 原 SD1/SD2 的所有文件均未改动。
 */
@Service
public class SalesDetail3ReportService {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail3ReportService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetail3Service sd3Service;

    /** 复用 SD1 的截图与目录创建能力（同包可见，未修改其源码） */
    @Autowired
    private SalesDailyReportService reportService;

    @Autowired
    private Sd3Properties props;

    @Value("${sd3.output-dir:target/report}")
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
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param orgCode   机构（空=全部）
     * @param chnl      渠道（空=全部）
     * @param orderNo   订单号（空=不筛选）
     * @param fileTag        文件名后缀（同日多机构时区分，防覆盖）；可空
     * @param taskTitle      本张截图顶部标题（screenshots 里配的）；空=用全局 sd3.title
     * @param taskTotalLabel 本张截图底部总计行的文案（screenshots 里配的）；空=用全局 sd3.total-label
     * @return PNG 绝对路径；失败返回 null
     */
    public String generateReport(String startDate, String endDate, String orgCode,
                                 String chnl, String orderNo, String fileTag,
                                 String taskTitle, String taskTotalLabel) {
        try {
            // ★ 自己创建 SD3 的输出目录（不能复用 SD1 的 ensureOutputDir —— 那个建的是
            //   report.daily.output-dir，与 sd3.output-dir 不是同一个路径，尤其 IDEA 里跑
            //   工作目录不是 provider-service 时，相对路径 target/report 会因目录不存在报
            //   NoSuchFileException）。用 createDirectories 相对/绝对路径都能正确创建。
            Files.createDirectories(Paths.get(outputDir));
        } catch (Exception e) {
            log.error("[销售详情3] 创建输出目录失败：{}", outputDir, e);
            return null;
        }

        SalesDetail3Service.Sd3Result result = sd3Service.query(startDate, endDate, orgCode, chnl, orderNo);
        List<SalesDetail3Service.Sd3Row> rows = result.getRows();

        String tag = (fileTag == null || fileTag.trim().isEmpty()) ? "" : "-" + fileTag.trim();
        String name = "sales-detail3-" + endDate + tag;
        String htmlPath = outputDir + "/" + name + ".html";
        try {
            Files.write(Paths.get(htmlPath),
                    buildHtml(startDate, endDate, orgCode, chnl, rows, result.getTotal(), taskTitle, taskTotalLabel).getBytes(StandardCharsets.UTF_8));
            log.info("[销售详情3] HTML 已生成: {}", new File(htmlPath).getAbsolutePath());
        } catch (Exception e) {
            log.error("[销售详情3] 生成 HTML 失败", e);
            return null;
        }

        String pngPath = outputDir + "/" + name + ".png";
        // 竖版 680px 宽截图（2026-10-26）：方便在微信/企业微信手机端直接阅读
        boolean ok = reportService.screenshot(htmlPath, pngPath, rows.size(), SCREENSHOT_WIDTH);
        log.info("[销售详情3] 截图{}：{}（{} 行，画布宽 {}px）", ok ? "成功" : "失败", pngPath, rows.size(), SCREENSHOT_WIDTH);
        return ok ? pngPath : null;
    }

    /** 定时任务的便捷入口：日期为空时取前一天 */
    public String generateReport(String startDate, String endDate, String orgCode, String chnl, String orderNo) {
        return generateReport(startDate, endDate, orgCode, chnl, orderNo, null, null, null);
    }

    // ==================== HTML ====================
    //
    // ★ 2026-10-26 竖版重排（方案 B）：本 HTML 只用于「截图」，页面 SalesDetail3.vue 完全不动。
    //   原因：企业微信/微信按宽度自适应缩放图片，1900×718 的横图在手机竖屏里字只剩 3px、
    //   上下黑边 697px。把画布压到 680px、字号放大到 17px 后，手机上字号约 10px，
    //   领导竖屏即可阅读，7 列全部保留。
    //   列宽合计 656px = 680 - .page 左右 padding 24px，table-layout:fixed 下不会撑破。
    private static final int SCREENSHOT_WIDTH = 680;

    private String buildHtml(String startDate, String endDate, String orgCode, String chnl,
                             List<SalesDetail3Service.Sd3Row> rows, SalesDetail3Service.Sd3Row total,
                             String taskTitle, String taskTotalLabel) {
        String dateText = startDate.equals(endDate) ? startDate : startDate + " ~ " + endDate;
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
          .append("<title>销售详情3</title>\n<style>\n")
          .append("body{margin:0;padding:0;background:#fff;font-family:'Microsoft YaHei','Segoe UI',sans-serif;font-size:17px;color:#333;}\n")
          .append(".page{padding:12px 12px 16px;}\n")
          .append(".header{text-align:center;padding:8px 0 10px;border-bottom:1px solid #e8e8e8;background:#fff;}\n")
          .append(".header h2{margin:0 0 8px;font-size:22px;font-weight:700;color:#b71c1c;letter-spacing:1px;}\n")
          .append(".date{display:inline-block;font-size:15px;padding:3px 8px;border-radius:4px;margin:2px 3px;background:#fff;color:#d32f2f;font-weight:700;line-height:1.4;}\n")
          .append("table{width:100%;border-collapse:collapse;margin-top:10px;table-layout:fixed;}\n")
          .append("th,td{box-sizing:border-box;overflow-wrap:break-word;}\n")
          .append("th{padding:9px 2px;border:2px solid #000;text-align:center;background:#fff;color:#333;font-weight:700;font-size:16px;}\n")
          .append("td{padding:8px 2px;border:1px solid #000;text-align:center;font-size:17px;font-variant-numeric:tabular-nums;line-height:1.2;}\n")
          .append(".col-name{text-align:center !important;vertical-align:middle;white-space:normal;}\n")
          .append("tr.subtotal td{background:#B8E0DC !important;border-top:2px solid #000;border-bottom:2px solid #000;color:#1a1a1a !important;font-weight:700;}\n")
          .append("tr.total td{background:#F2E1A6 !important;border-top:2px solid #000;border-bottom:3px double #000;color:#1a1a1a !important;font-weight:800;}\n")
          .append("</style>\n</head>\n<body>\n<div class=\"page\">\n")
          .append("<div class=\"header\">\n")
          .append("<h2>").append(esc(resolveTitle(taskTitle))).append("</h2>\n")
          .append("<span class=\"date\">查询日期：").append(dateText).append("</span>\n")
          .append("<span class=\"date\">机构：").append(orgCode == null || orgCode.trim().isEmpty() ? "全部" : orgCode).append("</span>\n")
          .append("<span class=\"date\">渠道：").append(chnl == null || chnl.trim().isEmpty() ? "全部" : chnl).append("</span>\n")
          .append("</div>\n")
          .append("<table>\n<colgroup>")
          .append("<col style=\"width:40px\">")
          .append("<col style=\"width:82px\">")
          .append("<col style=\"width:194px\">")
          .append("<col style=\"width:92px\">")
          .append("<col style=\"width:106px\">")
          .append("<col style=\"width:58px\">")
          .append("<col style=\"width:84px\">")
          .append("</colgroup>\n<thead>\n<tr>")
          .append("<th>序号</th>")
          .append("<th>门店编码</th>")
          .append("<th>门店名称</th>")
          .append("<th>平台</th>")
          .append("<th>销售额</th>")
          .append("<th>订单数</th>")
          .append("<th>客单价</th>")
          .append("</tr>\n</thead>\n<tbody>\n");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan=\"7\">当日无数据</td></tr>\n");
        } else {
            for (SalesDetail3Service.Sd3Row r : rows) {
                boolean isSub = "subtotal".equals(r.getRowType());
                // 序号/门店编码/门店名称 三列在整块内合并（含合计行）：span>0 才输出，span=0 表示被盖住
                int span = r.getStoreRowSpan();
                String spanAttr = span > 1 ? " rowspan=\"" + span + "\"" : "";
                sb.append("<tr").append(isSub ? " class=\"subtotal\"" : "").append(">");
                if (span > 0) {
                    sb.append("<td").append(spanAttr).append(">")
                      .append(r.getSeq() == null ? "" : r.getSeq()).append("</td>")
                      .append("<td").append(spanAttr).append(">").append(esc(r.getStoreCode())).append("</td>")
                      .append("<td class=\"col-name\"").append(spanAttr).append(">").append(esc(r.getStoreName())).append("</td>");
                }
                sb.append("<td>").append(esc(r.getChnl())).append("</td>")
                  .append("<td>").append(fmt(r.getSaleAmt())).append("</td>")
                  .append("<td>").append(r.getOrderCnt()).append("</td>")
                  .append("<td>").append(fmt(r.getAvgPrice())).append("</td>")
                  .append("</tr>\n");
            }
            sb.append("<tr class=\"total\">")
              // 左侧 4 列（序号/编码/名称/平台）合并成一个大格
              // 文案优先级：截图任务配置（screenshots[].total-label）> 全局 sd3.total-label > 默认
              .append("<td colspan=\"4\">").append(esc(resolveTotalLabel(total, taskTotalLabel))).append("</td>")
              .append("<td>").append(fmt(total.getSaleAmt())).append("</td>")
              .append("<td>").append(total.getOrderCnt()).append("</td>")
              .append("<td>").append(fmt(total.getAvgPrice())).append("</td>")
              .append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n</div>\n</body>\n</html>");
        return sb.toString();
    }

    /** 标题文案优先级：截图任务配置（screenshots[].title）> 全局 sd3.title */
    private String resolveTitle(String taskTitle) {
        if (taskTitle != null && !taskTitle.trim().isEmpty()) {
            return taskTitle.trim();
        }
        String global = props.getTitle();
        return (global == null || global.trim().isEmpty()) ? "销售详情3（外卖渠道）" : global.trim();
    }

    /** 总计行文案优先级：截图任务配置 > 全局 sd3.total-label > 默认 */
    private static String resolveTotalLabel(SalesDetail3Service.Sd3Row total, String taskTotalLabel) {
        if (taskTotalLabel != null && !taskTotalLabel.trim().isEmpty()) {
            return taskTotalLabel.trim();
        }
        if (total != null && total.getTotalLabel() != null && !total.getTotalLabel().trim().isEmpty()) {
            return total.getTotalLabel();
        }
        return "线上外卖销售共计";
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

    /** 供定时任务调用：取「前一天」 */
    public static String yesterday() {
        return LocalDate.now().minusDays(1).format(DTF);
    }
}
