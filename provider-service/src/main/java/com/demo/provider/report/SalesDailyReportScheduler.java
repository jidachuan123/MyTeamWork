package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 销售详情日报定时任务
 *
 * 默认每天早上 10:00 触发（cron 可在 application.yml 的 report.daily.cron 修改）。
 */
@Component
public class SalesDailyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SalesDailyReportScheduler.class);

    @Autowired
    private SalesDailyReportService salesDailyReportService;

    @Scheduled(cron = "${report.daily.cron:0 0 10 * * ?}")
    public void runDailyReport() {
        log.info("[销售日报] 定时任务开始执行...");
        try {
            String result = salesDailyReportService.generateDailyReport(LocalDate.now());
            log.info("[销售日报] 执行完成：\n{}", result);
        } catch (Exception e) {
            log.error("[销售日报] 执行异常", e);
        }
    }
}
