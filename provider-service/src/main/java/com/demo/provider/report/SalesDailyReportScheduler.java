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

        List<String> pngPaths = new ArrayList<>();

        // 1. SalesDetail.vue 截图（orgCode=1101001, deptLevels=3）
        try {
            String png1 = salesDailyReportService.generateDailyReport(date);
            if (png1 != null) {
                pngPaths.add(png1);
                sb.append("销售详情1 截图成功：").append(png1).append("\n");
            } else {
                sb.append("销售详情1 截图失败\n");
            }
        } catch (Exception e) {
            sb.append("销售详情1 生成异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 销售详情1 生成异常", e);
        }

        // 2. SalesDetail2.vue 截图（orgCode=1101,1102,1191001, deptLevels=空, department=空）
        try {
            String png2 = salesDetail2ReportService.generateDailyReport(date);
            if (png2 != null) {
                pngPaths.add(png2);
                sb.append("销售详情2 截图成功：").append(png2).append("\n");
            } else {
                sb.append("销售详情2 截图失败\n");
            }
        } catch (Exception e) {
            sb.append("销售详情2 生成异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 销售详情2 生成异常", e);
        }

        // 3. 发送邮件（两张截图一起发）
        if (!pngPaths.isEmpty()) {
            try {
                String result = salesDailyReportService.sendMail(todayStr, pngPaths);
                sb.append("邮件发送结果：").append(result.trim()).append("\n");
            } catch (Exception e) {
                sb.append("邮件发送异常：").append(e.getMessage()).append("\n");
                log.error("[销售日报] 邮件发送异常", e);
            }
        } else {
            sb.append("无截图生成，跳过邮件发送\n");
        }

        sb.append("[销售日报] 执行完成");
        return sb.toString();
    }

    @Scheduled(cron = "${report.daily.cron:0 0 10 * * ?}")
    public void runDailyReport() {
        log.info("[销售日报] 定时任务触发");
        String summary = runNow(LocalDate.now());
        log.info("[销售日报] 定时任务执行结果：\n{}", summary);
    }
}
