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
 * 销售详情日报定时任务
 *
 * 默认每天早上 10:00 触发（cron 可在 application.yml 的 report.daily.cron 修改）。
 * 依次生成 SalesDetail.vue + SalesDetail2.vue 两个页面的截图，用一封邮件发送。
 *
 * 日期规则：本期=前一天、环比=前两天、同比=去年的今天
 * SalesDetail：orgCode=1101001, deptLevels=3
 * SalesDetail2：orgCode=1101,1102,1191001, deptLevels=空, department=空
 */
@Component
public class SalesDailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SalesDailyReportScheduler.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDailyReportService salesDailyReportService;

    @Autowired
    private SalesDetail2ReportService salesDetail2ReportService;

    @Scheduled(cron = "${report.daily.cron:0 0 10 * * ?}")
    public void runDailyReport() {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DTF);
        log.info("[销售日报] 定时任务开始执行（{}）...", todayStr);

        List<String> pngPaths = new ArrayList<>();

        // 1. SalesDetail.vue 截图（orgCode=1101001, deptLevels=3）
        try {
            String png1 = salesDailyReportService.generateDailyReport(today);
            if (png1 != null) {
                pngPaths.add(png1);
                log.info("[销售日报] 销售详情1 截图成功: {}", png1);
            } else {
                log.warn("[销售日报] 销售详情1 截图失败");
            }
        } catch (Exception e) {
            log.error("[销售日报] 销售详情1 生成异常", e);
        }

        // 2. SalesDetail2.vue 截图（orgCode=1101,1102,1191001, deptLevels=空, department=空）
        try {
            String png2 = salesDetail2ReportService.generateDailyReport(today);
            if (png2 != null) {
                pngPaths.add(png2);
                log.info("[销售日报] 销售详情2 截图成功: {}", png2);
            } else {
                log.warn("[销售日报] 销售详情2 截图失败");
            }
        } catch (Exception e) {
            log.error("[销售日报] 销售详情2 生成异常", e);
        }

        // 3. 发送邮件（两张截图一起发）
        if (!pngPaths.isEmpty()) {
            String result = salesDailyReportService.sendMail(todayStr, pngPaths);
            log.info("[销售日报] 邮件发送结果: {}", result.trim());
        } else {
            log.warn("[销售日报] 无截图生成，跳过邮件发送");
        }

        log.info("[销售日报] 定时任务执行完成");
    }
}
