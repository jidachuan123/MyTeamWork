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
 * 销售详情4（SD4，配送调拨汇总表）定时推送 —— 2026-10-16 新建。
 *
 * ★ 与 SD1/SD2/SD3 的各自调度器完全独立（各自的 cron、各自的配置），原调度器未做任何改动。
 * ★ 推送通道复用现有实现：
 *     邮件   → SalesDailyReportService.sendMail(...)
 *     企微   → WeComBotService.sendReport(...)
 *   两者互不冲突：mail-to 和 wecom-webhook 都填就都发，留空则不发。
 *
 * 参数来源（application-sd4.yml）：
 *   sd4.out-org-codes                       定时发送用发货机构
 *   sd4.use-fixed-date + 6 个日期            日期开关（false=推算：记账=前一天、环比=前两天、同比=去年同日）
 *   其余 SQL 参数（收货机构/部门/…）        SalesDetail4Service 执行时直接从配置读取
 */
@Component
public class SalesDetail4Scheduler {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail4Scheduler.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetail4ReportService reportService4;

    @Autowired
    private SalesDailyReportService mailService;

    @Autowired
    private WeComBotService weComBotService;

    @Autowired
    private Sd4Properties props;

    @Scheduled(cron = "${sd4.cron:0 50 8 * * ?}")
    public void runDaily() {
        log.info("[销售详情4] 定时任务触发");
        try {
            String summary = runNow();
            log.info("[销售详情4] 定时任务执行结果：\n{}", summary);
        } catch (Exception e) {
            log.error("[销售详情4] 定时任务异常", e);
        }
    }

    /** 手动触发入口（返回可读结果） */
    public String runNow() {
        LocalDate today = LocalDate.now();
        String defAcc = today.minusDays(1).format(DTF);   // 默认：记账 = 前一天
        String defCmp = today.minusDays(2).format(DTF);   // 默认：环比 = 前两天
        String defYoy = today.minusYears(1).format(DTF);  // 默认：同比 = 去年同日

        String accS, accE, cmpS, cmpE, yoyS, yoyE;
        if (props.isUseFixedDate()) {
            accS = fallback(props.getAccStartDate(), defAcc, "acc-start-date");
            accE = fallback(props.getAccEndDate(), accS, "acc-end-date");
            cmpS = fallback(props.getCmpStartDate(), defCmp, "cmp-start-date");
            cmpE = fallback(props.getCmpEndDate(), cmpS, "cmp-end-date");
            yoyS = fallback(props.getYoyStartDate(), defYoy, "yoy-start-date");
            yoyE = fallback(props.getYoyEndDate(), yoyS, "yoy-end-date");
        } else {
            accS = accE = defAcc;
            cmpS = cmpE = defCmp;
            yoyS = yoyE = defYoy;
        }
        String outOrg = props.getOutOrgCodes() == null ? "" : props.getOutOrgCodes().trim();

        log.info("[销售详情4] 使用日期：记账 {}~{} 环比 {}~{} 同比 {}~{} 发货机构={}",
                accS, accE, cmpS, cmpE, yoyS, yoyE, outOrg.isEmpty() ? "(全部)" : outOrg);

        StringBuilder sb = new StringBuilder();
        sb.append("[销售详情4] 开始执行（记账 ").append(accE).append("）\n");

        // ===== 1. 截图 =====
        String png = reportService4.generateReport(accS, accE, cmpS, cmpE, yoyS, yoyE, outOrg, null);
        if (png == null) {
            sb.append("截图失败，终止推送\n");
            return sb.toString();
        }
        sb.append("截图成功：").append(png).append("\n");
        List<String> pngs = new ArrayList<>();
        pngs.add(png);

        // ===== 2. 摘要数据（邮件正文 / 企微文案共用）=====
        String mailDate = accE;
        String desc = "配送调拨汇总表" + (outOrg.isEmpty() ? "" : "(" + outOrg + ")");
        String digest = buildDigest(mailDate, accS, accE, cmpS, cmpE, yoyS, yoyE, outOrg);

        // ===== 3. 邮件 =====
        String mailTo = props.getMailTo() == null ? "" : props.getMailTo().trim();
        if (!mailTo.isEmpty()) {
            try {
                String r = mailService.sendMail(mailDate, pngs, mailTo, desc);
                sb.append("邮件(").append(mailTo).append(") 发送结果：").append(r.trim()).append("\n");
            } catch (Exception e) {
                sb.append("邮件(").append(mailTo).append(") 发送异常：").append(e.getMessage()).append("\n");
                log.error("[销售详情4] 邮件发送异常", e);
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
                log.error("[销售详情4] 企微推送异常", e);
            }
        } else {
            sb.append("企微：未配置 wecom-webhook，跳过\n");
        }

        sb.append("[销售详情4] 执行完成");
        return sb.toString();
    }

    /**
     * 企微摘要文案：配了 wecom-digest 就用配置原文（替换占位符），否则用默认模板。
     * 占位符：{date} {accRange} {cmpRange} {yoyRange} {org}
     */
    private String buildDigest(String date, String accS, String accE,
                               String cmpS, String cmpE, String yoyS, String yoyE, String outOrg) {
        String accRange = range(accS, accE);
        String cmpRange = range(cmpS, cmpE);
        String yoyRange = range(yoyS, yoyE);
        String orgText = (outOrg == null || outOrg.trim().isEmpty()) ? "全部机构" : outOrg.trim();

        String tpl = (props.getWecomDigest() == null) ? "" : props.getWecomDigest();
        if (!tpl.trim().isEmpty()) {
            return tpl.replace("{date}", date)
                      .replace("{accRange}", accRange)
                      .replace("{cmpRange}", cmpRange)
                      .replace("{yoyRange}", yoyRange)
                      .replace("{org}", orgText);
        }

        return "**配送调拨汇总表 " + date + "**\n"
                + "> 记账日期：" + accRange + "\n"
                + "> 环比日期：" + cmpRange + "\n"
                + "> 同比日期：" + yoyRange + "\n"
                + "> 发货机构：" + orgText;
    }

    private static String range(String s, String e) {
        return (s != null && s.equals(e)) ? s : s + " ~ " + e;
    }

    /** 配置值为空时回退推算值并打 WARN（与 SD1/SD2/SD3 同样的「不静默」原则） */
    private String fallback(String configured, String computed, String field) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        log.warn("[销售详情4] 已开启 use-fixed-date 但未配置 {}，回退使用推算值 {}", field, computed);
        return computed;
    }
}
