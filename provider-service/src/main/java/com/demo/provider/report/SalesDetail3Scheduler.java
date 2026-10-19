package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
        String chnl = props.getChnl() == null ? "" : props.getChnl().trim();
        String orderNo = props.getOrderNo() == null ? "" : props.getOrderNo().trim();

        // 截图任务列表：优先用 screenshots 配置；为空则回退用单一 org-code（向后兼容，发一张）
        List<Sd3Properties.ScreenshotTask> tasks = props.getScreenshots();
        if (tasks == null || tasks.isEmpty()) {
            String org = props.getOrgCode() == null ? "" : props.getOrgCode().trim();
            Sd3Properties.ScreenshotTask t = new Sd3Properties.ScreenshotTask();
            t.setOrgCode(org);
            tasks = Collections.singletonList(t);
        }

        log.info("[销售详情3] 使用日期：本期 {}/{} 渠道={} 截图任务数={}",
                startDate, endDate, chnl.isEmpty() ? "(全部)" : chnl, tasks.size());

        StringBuilder sb = new StringBuilder();
        sb.append("[销售详情3] 开始执行（").append(endDate).append("）\n");

        // ===== 1. 截图：按 screenshots 列表逐张生成 =====
        List<String> pngs = new ArrayList<>();
        List<String> orgList = new ArrayList<>();
        StringBuilder desc = new StringBuilder();
        for (Sd3Properties.ScreenshotTask task : tasks) {
            String org = task.getOrgCode() == null ? "" : task.getOrgCode().trim();
            String tag = task.getTag() == null ? "" : task.getTag().trim();
            String label = "销售详情3" + (org.isEmpty() ? "" : "(" + org + ")");
            if (!org.isEmpty()) {
                orgList.add(org);
            }
            try {
                String title = task.getTitle() == null ? "" : task.getTitle().trim();
                String totalLabel = task.getTotalLabel() == null ? "" : task.getTotalLabel().trim();
                String png = reportService3.generateReport(startDate, endDate, org, chnl, orderNo, tag, title, totalLabel);
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
                log.error("[销售详情3] 截图生成异常（org={}, tag={}）", org, tag, e);
            }
        }
        if (pngs.isEmpty()) {
            sb.append("截图全部失败，终止推送\n");
            return sb.toString();
        }

        // ===== 2. 摘要数据（邮件正文 / 企微文案共用）=====
        // 单机构：带金额汇总；多机构：只给机构范围（金额看各图），避免误导
        SalesDetail3Service.Sd3Row total = null;
        if (tasks.size() == 1) {
            String org = tasks.get(0).getOrgCode() == null ? "" : tasks.get(0).getOrgCode().trim();
            total = sd3Service.query(startDate, endDate, org, chnl, orderNo).getTotal();
        }
        String digest = buildDigest(endDate, orgList, chnl, total);

        // ===== 3. 邮件 =====
        String mailTo = props.getMailTo() == null ? "" : props.getMailTo().trim();
        if (!mailTo.isEmpty()) {
            try {
                String r = mailService.sendMail(endDate, pngs, mailTo, desc.toString());
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
     *
     * @param orgList 各截图任务的机构列表（多机构时拼接展示）
     * @param total   单机构时的金额汇总；多机构传 null（金额看各图，避免误导）
     */
    private String buildDigest(String date, List<String> orgList, String chnl,
                               SalesDetail3Service.Sd3Row total) {
        String orgText = (orgList == null || orgList.isEmpty()) ? "全部机构" : String.join(", ", orgList);
        String chnlText = (chnl == null || chnl.trim().isEmpty()) ? "全部渠道" : chnl.trim();
        boolean hasAmount = total != null;
        String amt = hasAmount && total.getSaleAmt() != null ? total.getSaleAmt().toPlainString() : "";
        String orders = hasAmount ? String.valueOf(total.getOrderCnt()) : "";
        String avg = hasAmount && total.getAvgPrice() != null ? total.getAvgPrice().toPlainString() : "";

        String tpl = (props.getWecomDigest() == null) ? "" : props.getWecomDigest();
        if (!tpl.trim().isEmpty()) {
            return tpl.replace("{date}", date)
                      .replace("{org}", orgText)
                      .replace("{chnl}", chnlText)
                      .replace("{amt}", amt)
                      .replace("{orders}", orders)
                      .replace("{avg}", avg);
        }

        // 纯文本模板：企微摘要走 text 消息（markdown 在微信客户端打不开）
        if (hasAmount) {
            return "销售详情3 " + date + "\n"
                    + "机构：" + orgText + "\n"
                    + "渠道：" + chnlText + "\n"
                    + "销售额：" + amt + "\n"
                    + "订单数：" + orders + "\n"
                    + "客单价：" + avg;
        }
        return "销售详情3 " + date + "\n"
                + "机构：" + orgText + "\n"
                + "渠道：" + chnlText;
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
