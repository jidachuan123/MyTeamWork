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
 * 销售详情3（SD3）定时推送 —— 2026-10-15 新建。
 *
 * ★ 与 SD1/SD2 的 SalesDailyReportScheduler 完全独立（各自的 cron、各自的配置），
 *   互不干扰；原调度器未做任何改动。
 * ★ 推送通道复用现有实现：
 *     邮件   → SalesDailyReportService.sendMail(...)
 *     企微   → WeComBotService.sendReport(...)
 *   两者互不冲突：mail-to 和 wecom-webhook 都填就都发，留空则不发。
 *
 * 参数来源（application-sd3.yml）：
 *   sd3.org-code / sd3.chnl / sd3.order-no   定时发送用（页面手动查询不受影响）
 *   sd3.use-fixed-date + start-date/end-date 日期开关（false=前一天，true=固定日期）
 */
@Component
public class SalesDetail3Scheduler {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail3Scheduler.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetail3ReportService reportService3;

    @Autowired
    private SalesDetail3Service sd3Service;

    @Autowired
    private SalesDailyReportService mailService;

    @Autowired
    private WeComBotService weComBotService;

    @Autowired
    private Sd3Properties props;

    @Scheduled(cron = "${sd3.cron:0 35 8 * * ?}")
    public void runDaily() {
        log.info("[销售详情3] 定时任务触发");
        try {
            String summary = runNow();
            log.info("[销售详情3] 定时任务执行结果：\n{}", summary);
        } catch (Exception e) {
            log.error("[销售详情3] 定时任务异常", e);
        }
    }

    /** 手动触发入口（返回可读结果） */
    public String runNow() {
        LocalDate today = LocalDate.now();
        String cur = today.minusDays(1).format(DTF);   // 默认：本期 = 前一天

        String startDate, endDate;
        if (props.isUseFixedDate()) {
            startDate = fallback(props.getStartDate(), cur, "start-date");
            endDate = fallback(props.getEndDate(), cur, "end-date");
        } else {
            startDate = endDate = cur;
        }
        String orgCode = props.getOrgCode() == null ? "" : props.getOrgCode().trim();
        String chnl = props.getChnl() == null ? "" : props.getChnl().trim();
        String orderNo = props.getOrderNo() == null ? "" : props.getOrderNo().trim();

        log.info("[销售详情3] 使用日期：本期 {}/{} 机构={} 渠道={}",
                startDate, endDate, orgCode.isEmpty() ? "(全部)" : orgCode, chnl.isEmpty() ? "(全部)" : chnl);

        StringBuilder sb = new StringBuilder();
        sb.append("[销售详情3] 开始执行（").append(endDate).append("）\n");

        // ===== 1. 截图 =====
        String png = reportService3.generateReport(startDate, endDate, orgCode, chnl, orderNo);
        if (png == null) {
            sb.append("截图失败，终止推送\n");
            return sb.toString();
        }
        sb.append("截图成功：").append(png).append("\n");
        List<String> pngs = new ArrayList<>();
        pngs.add(png);

        // ===== 2. 摘要数据（邮件正文 / 企微文案共用）=====
        SalesDetail3Service.Sd3Result result = sd3Service.query(startDate, endDate, orgCode, chnl, orderNo);
        SalesDetail3Service.Sd3Row total = result.getTotal();
        String desc = "销售详情3";
        String digest = buildDigest(endDate, orgCode, chnl, total);

        // ===== 3. 邮件 =====
        String mailTo = props.getMailTo() == null ? "" : props.getMailTo().trim();
        if (!mailTo.isEmpty()) {
            try {
                String r = mailService.sendMail(endDate, pngs, mailTo, desc);
                sb.append("邮件(").append(mailTo).append(") 发送结果：").append(r.trim()).append("\n");
            } catch (Exception e) {
                sb.append("邮件(").append(mailTo).append(") 发送异常：").append(e.getMessage()).append("\n");
                log.error("[销售详情3] 邮件发送异常", e);
            }
        } else {
            sb.append("邮件：未配置 mail-to，跳过\n");
        }

        // ===== 4. 企业微信 =====
        String webhook = props.getWecomWebhook() == null ? "" : props.getWecomWebhook().trim();
        if (!webhook.isEmpty()) {
            try {
                String r = weComBotService.sendReport(webhook, digest, pngs);
                sb.append("企微推送：").append(r.trim()).append("\n");
            } catch (Exception e) {
                sb.append("企微推送异常：").append(e.getMessage()).append("\n");
                log.error("[销售详情3] 企微推送异常", e);
            }
        } else {
            sb.append("企微：未配置 wecom-webhook，跳过\n");
        }

        sb.append("[销售详情3] 执行完成");
        return sb.toString();
    }

    /**
     * 企微摘要文案：配了 wecom-digest 就用配置原文（替换占位符），否则用默认模板。
     * 占位符：{date} {org} {chnl} {amt} {orders} {avg}
     */
    private String buildDigest(String date, String orgCode, String chnl,
                               SalesDetail3Service.Sd3Row total) {
        String orgText = (orgCode == null || orgCode.trim().isEmpty()) ? "全部机构" : orgCode.trim();
        String chnlText = (chnl == null || chnl.trim().isEmpty()) ? "全部渠道" : chnl.trim();
        String amt = total == null || total.getSaleAmt() == null ? "0.00" : total.getSaleAmt().toPlainString();
        String orders = total == null ? "0" : String.valueOf(total.getOrderCnt());
        String avg = total == null || total.getAvgPrice() == null ? "0.00" : total.getAvgPrice().toPlainString();

        String tpl = (props.getWecomDigest() == null) ? "" : props.getWecomDigest();
        if (!tpl.trim().isEmpty()) {
            return tpl.replace("{date}", date)
                      .replace("{org}", orgText)
                      .replace("{chnl}", chnlText)
                      .replace("{amt}", amt)
                      .replace("{orders}", orders)
                      .replace("{avg}", avg);
        }

        return "**销售详情3 " + date + "**\n"
                + "> 机构：" + orgText + "\n"
                + "> 渠道：" + chnlText + "\n"
                + "> 销售额：" + amt + "\n"
                + "> 订单数：" + orders + "\n"
                + "> 客单价：" + avg;
    }

    /** 配置值为空时回退推算值并打 WARN（与 SD1/SD2 同样的「不静默」原则） */
    private String fallback(String configured, String computed, String field) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        log.warn("[销售详情3] 已开启 use-fixed-date 但未配置 {}，回退使用推算值 {}", field, computed);
        return computed;
    }
}
