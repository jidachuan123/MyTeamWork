package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售详情日报定时任务
 *
 * 默认每天下午 16:15 触发（cron 可在 application.yml 的 report.daily.cron 修改）。
 * 每日生成两类报表截图并各发一封邮件：
 *   1) 主收件人（report.daily.mail-to，默认 1137851593@qq.com）：销售详情1（orgCode=report.daily.org-code）
 *      + 销售详情2（orgCode=report.daily.org-code2）两张截图，一封邮件发送。
 *   2) 第二收件人（report.daily.mail-to-2，默认 591111450@qq.com）：仅销售详情1 截图，
 *      机构编码=report.daily.org-code-extra（默认 1103011），文件名带 -1103011 防冲突；
 *      日期逻辑与第 1) 类完全一致。
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

    @Value("${report.daily.mail-to:}")
    private String mailTo;

    @Value("${report.daily.mail-to-2:}")
    private String mailTo2;

    @Value("${report.daily.org-code-extra:1103011}")
    private String orgCodeExtra;

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

        // 主收件人（report.daily.mail-to）：销售详情1 + 销售详情2 两张截图
        List<String> pngPrimary = new ArrayList<>();
        // 第二收件人（report.daily.mail-to-2）：仅销售详情1（机构=org-code-extra）
        List<String> pngExtra = new ArrayList<>();

        // 1. 销售详情1（主收件人，orgCode=report.daily.org-code，默认 1101001）
        try {
            String png1 = salesDailyReportService.generateDailyReport(date);
            if (png1 != null) {
                pngPrimary.add(png1);
                sb.append("销售详情1 截图成功：").append(png1).append("\n");
            } else {
                sb.append("销售详情1 截图失败\n");
            }
        } catch (Exception e) {
            sb.append("销售详情1 生成异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 销售详情1 生成异常", e);
        }

        // 2. 销售详情2（主收件人，orgCode=report.daily.org-code2）
        try {
            String png2 = salesDetail2ReportService.generateDailyReport(date);
            if (png2 != null) {
                pngPrimary.add(png2);
                sb.append("销售详情2 截图成功：").append(png2).append("\n");
            } else {
                sb.append("销售详情2 截图失败\n");
            }
        } catch (Exception e) {
            sb.append("销售详情2 生成异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 销售详情2 生成异常", e);
        }

        // 3. 销售详情1（第二收件人，orgCode=report.daily.org-code-extra，默认 1103011）
        //    文件名追加 -1103011 避免同日不同机构截图被覆盖；日期逻辑与前两处完全一致
        try {
            String png3 = salesDailyReportService.generateDailyReport(date, orgCodeExtra, orgCodeExtra);
            if (png3 != null) {
                pngExtra.add(png3);
                sb.append("销售详情1(").append(orgCodeExtra).append(") 截图成功：").append(png3).append("\n");
            } else {
                sb.append("销售详情1(").append(orgCodeExtra).append(") 截图失败\n");
            }
        } catch (Exception e) {
            sb.append("销售详情1(").append(orgCodeExtra).append(") 生成异常：").append(e.getMessage()).append("\n");
            log.error("[销售日报] 销售详情1(extra) 生成异常", e);
        }

        // 4. 分别发送两封邮件
        if (!pngPrimary.isEmpty()) {
            try {
                String result = salesDailyReportService.sendMail(todayStr, pngPrimary, mailTo, "销售详情1 + 销售详情2");
                sb.append("邮件(").append(mailTo).append(") 发送结果：").append(result.trim()).append("\n");
            } catch (Exception e) {
                sb.append("邮件(").append(mailTo).append(") 发送异常：").append(e.getMessage()).append("\n");
                log.error("[销售日报] 邮件发送异常", e);
            }
        } else {
            sb.append("主收件人无截图生成，跳过邮件发送\n");
        }

        if (!pngExtra.isEmpty()) {
            try {
                String result = salesDailyReportService.sendMail(todayStr, pngExtra, mailTo2, "销售详情1（" + orgCodeExtra + "）");
                sb.append("邮件(").append(mailTo2).append(") 发送结果：").append(result.trim()).append("\n");
            } catch (Exception e) {
                sb.append("邮件(").append(mailTo2).append(") 发送异常：").append(e.getMessage()).append("\n");
                log.error("[销售日报] 邮件(extra) 发送异常", e);
            }
        } else {
            sb.append("第二收件人无截图生成，跳过邮件发送\n");
        }

        sb.append("[销售日报] 执行完成");
        return sb.toString();
    }

    @Scheduled(cron = "${report.daily.cron:0 15 8 * * ?}")
    public void runDailyReport() {
        log.info("[销售日报] 定时任务触发");
        String summary = runNow(LocalDate.now());
        log.info("[销售日报] 定时任务执行结果：\n{}", summary);
    }
}
