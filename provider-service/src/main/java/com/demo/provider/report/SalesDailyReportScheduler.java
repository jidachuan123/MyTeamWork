package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售详情日报定时任务（配置驱动）
 *
 * 每天按 application.yml 的 report.daily 配置定时执行：
 *   - 主邮件（main）：按 main.screenshots 列表逐张生成截图，一封邮件发往 main.mail-to
 *   - 副邮件（secondary）：按 secondary.screenshots 列表逐张生成截图，一封邮件发往 secondary.mail-to
 *
 * 日期规则：本期=前一天、环比=前两天、同比=去年的今天（与页面查询完全一致）
 * 截图类型：type=sd1 → 销售详情1（SalesDetail）；type=sd2 → 销售详情2（SalesDetail2）
 *
 * ★ 以后加截图/加机构：只需在 yml 对应分组的 screenshots 列表增删一条记录，无需改代码。
 */
@Component
public class SalesDailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SalesDailyReportScheduler.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDailyReportService salesDailyReportService;

    @Autowired
    private SalesDetail2ReportService salesDetail2ReportService;

    @Autowired
    private ReportDailyProperties props;

    /**
     * 手动/立即执行销售日报完整流程（含截图 + 邮件）。
     * 供定时任务和手动触发接口复用。
     *
     * @param date 发送日（用于文件名和邮件主题），内部按规则算查询/对比日期
     * @return 执行结果摘要（可读）
     */
    public String runNow(LocalDate date) {
        String todayStr = date.format(DTF);
        StringBuilder sb = new StringBuilder();
        sb.append("[销售日报] 开始执行（").append(todayStr).append("）\n");

        // 主邮件
        runGroup(date, todayStr, sb, props.getMain(), "主邮件");
        // 副邮件
        runGroup(date, todayStr, sb, props.getSecondary(), "副邮件");

        sb.append("[销售日报] 执行完成");
        return sb.toString();
    }

    /**
     * 按配置生成一个邮件分组的全部截图并发送一封邮件。
     */
    private void runGroup(LocalDate date, String todayStr, StringBuilder sb,
                          ReportDailyProperties.MailGroup group, String groupName) {
        String recipient = (group != null && group.getMailTo() != null) ? group.getMailTo().trim() : "";
        List<String> pngs = new ArrayList<>();
        StringBuilder desc = new StringBuilder();

        if (group == null || group.getScreenshots() == null || group.getScreenshots().isEmpty()) {
            sb.append(groupName).append("：未配置截图列表，跳过\n");
            return;
        }

        // ★ 日期来源：use-fixed-date=true → 用本组配置的固定日期（缺失项逐字段回退推算值）
        //   false（默认）→ 全部按「本期=前一天、环比=前两天、同比=去年同日」推算（原逻辑）
        DailyReportParam fixedReq = group.isUseFixedDate() ? buildFixedParam(group, date, groupName) : null;

        for (ReportDailyProperties.ScreenshotTask task : group.getScreenshots()) {
            String type = (task.getType() == null) ? "sd1" : task.getType().trim().toLowerCase();
            String orgCode = (task.getOrgCode() == null) ? "" : task.getOrgCode().trim();
            String tag = (task.getTag() == null) ? "" : task.getTag().trim();
            String unionStockCodes = (task.getUnionStockCodes() == null) ? "" : task.getUnionStockCodes().trim();
            String label = ("sd2".equals(type) ? "销售详情2" : "销售详情1") + (orgCode.isEmpty() ? "" : "(" + orgCode + ")");
            try {
                String png;
                if (fixedReq != null) {
                    // 固定日期模式：机构/层级仍按每张截图自己的配置，只有日期被覆盖
                    fixedReq.setOrgCode(orgCode);
                    fixedReq.setDeptLevels("sd2".equals(type) ? "" : "3");
                    fixedReq.setDepartment("");
                    if ("sd2".equals(type)) {
                        png = salesDetail2ReportService.generateDailyReport(fixedReq, tag, unionStockCodes);
                    } else {
                        png = salesDailyReportService.generateDailyReport(fixedReq, tag);
                    }
                } else if ("sd2".equals(type)) {
                    png = salesDetail2ReportService.generateDailyReport(date, orgCode, tag, unionStockCodes);
                } else {
                    png = salesDailyReportService.generateDailyReport(date, orgCode, tag);
                }
                if (png != null) {
                    pngs.add(png);
                    sb.append(label).append(" 截图成功：").append(png).append("\n");
                } else {
                    sb.append(label).append(" 截图失败\n");
                }
                if (desc.length() > 0) {
                    desc.append(" + ");
                }
                desc.append(label);
            } catch (Exception e) {
                sb.append(label).append(" 生成异常：").append(e.getMessage()).append("\n");
                log.error("[销售日报] {} 生成异常（type={}, orgCode={}, tag={}）", groupName, type, orgCode, tag, e);
            }
        }

        if (pngs.isEmpty()) {
            sb.append(groupName).append("：无截图生成，跳过邮件发送\n");
            return;
        }
        // 邮件主题 / 文件名日期：固定日期模式用配置的本期结束日期，否则用发送日推算的本期日期
        String mailDate = (fixedReq != null) ? fixedReq.getEndDate() : todayStr;
        try {
            String result = salesDailyReportService.sendMail(mailDate, pngs, recipient, desc.toString());
            sb.append("邮件(").append(recipient).append(") 发送结果：").append(result.trim()).append("\n");
        } catch (Exception e) {
            sb.append("邮件(").append(recipient).append(") 发送异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 邮件发送异常（{}）", recipient, e);
        }
    }

    /**
     * 构造「固定日期」模式下的查询参数：6 个日期全部取自分组配置。
     * 某个日期没配 → 回退到该字段的推算值（SD1/SD2 推算规则不同，这里按 SD1 规则），并打 WARN 提醒。
     */
    private DailyReportParam buildFixedParam(ReportDailyProperties.MailGroup group, LocalDate date, String groupName) {
        DailyReportParam req = new DailyReportParam();
        String cur = date.minusDays(1).format(DTF);
        String cmp = date.minusDays(2).format(DTF);
        String yoy = date.minusYears(1).format(DTF);   // SD2 原本是「去年本期就近同周几」，固定模式下按配置的来，不再推算

        req.setStartDate(fallback(group.getStartDate(), cur, groupName, "start-date"));
        req.setEndDate(fallback(group.getEndDate(), cur, groupName, "end-date"));
        req.setCmpStartDate(fallback(group.getCmpStartDate(), cmp, groupName, "cmp-start-date"));
        req.setCmpEndDate(fallback(group.getCmpEndDate(), cmp, groupName, "cmp-end-date"));
        req.setYoyStartDate(fallback(group.getYoyStartDate(), yoy, groupName, "yoy-start-date"));
        req.setYoyEndDate(fallback(group.getYoyEndDate(), yoy, groupName, "yoy-end-date"));

        log.info("[销售日报] {} 使用固定日期：本期 {}/{} 环比 {}/{} 同比 {}/{}",
                groupName, req.getStartDate(), req.getEndDate(),
                req.getCmpStartDate(), req.getCmpEndDate(),
                req.getYoyStartDate(), req.getYoyEndDate());
        return req;
    }

    /** 配置值为空时回退推算值，并打 WARN（避免「开关开了但日期漏填」被静默忽略） */
    private String fallback(String configured, String computed, String groupName, String field) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        log.warn("[销售日报] {} 已开启 use-fixed-date 但未配置 {}，回退使用推算值 {}", groupName, field, computed);
        return computed;
    }

    @Scheduled(cron = "${report.daily.cron:0 15 8 * * ?}")
    public void runDailyReport() {
        log.info("[销售日报] 定时任务触发");
        String summary = runNow(LocalDate.now());
        log.info("[销售日报] 定时任务执行结果：\n{}", summary);
    }
}
