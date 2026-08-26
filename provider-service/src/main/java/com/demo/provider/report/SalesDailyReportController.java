package com.demo.provider.report;

import com.demo.provider.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 销售日报手动触发接口。
 *
 * 与顶部导航那个「整点合并」按钮不同，这里按页面拆成两个独立入口：
 *   - POST /provider/sales/report/detail1/trigger  → 销售详情1 截图 + 单独发邮件
 *   - POST /provider/sales/report/detail2/trigger  → 销售详情2 截图 + 单独发邮件
 * 两者都接收前端当前页面的查询参数（DailyReportParam），不再写死 orgCode / 日期。
 *
 * 每日 10:00 的合并定时任务由 SalesDailyReportScheduler 负责，与此处无关。
 */
@RestController
@RequestMapping("/provider/sales")
public class SalesDailyReportController {

    @Autowired
    private SalesDailyReportService salesDailyReportService;

    @Autowired
    private SalesDetail2ReportService salesDetail2ReportService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 报表日期标签：优先用页面本期结束日期，否则回退今天 */
    private static String reportDate(DailyReportParam req) {
        if (req.getEndDate() != null && !req.getEndDate().trim().isEmpty()) {
            return req.getEndDate().trim();
        }
        return LocalDate.now().format(DTF);
    }

    /**
     * 手动生成并发送「销售详情1」日报（只发这一张截图）。
     * 请求体为页面 queryForm 对应字段：orgCode / deptLevels / 6 个日期。
     */
    @PostMapping("/report/detail1/trigger")
    public ApiResult<String> triggerDetail1(@RequestBody DailyReportParam req) {
        String png = salesDailyReportService.generateDailyReport(req);
        if (png == null) {
            return ApiResult.ok("销售详情1 截图生成失败（检查浏览器/输出目录日志）");
        }
        String mail = salesDailyReportService.sendMail(reportDate(req), Collections.singletonList(png));
        return ApiResult.ok("销售详情1 截图：" + png + "\n" + mail);
    }

    /**
     * 手动生成并发送「销售详情2」日报（只发这一张截图）。
     * 请求体为页面 queryForm 对应字段：orgCode / department / deptLevels / 6 个日期。
     */
    @PostMapping("/report/detail2/trigger")
    public ApiResult<String> triggerDetail2(@RequestBody DailyReportParam req) {
        String png = salesDetail2ReportService.generateDailyReport(req);
        if (png == null) {
            return ApiResult.ok("销售详情2 截图生成失败（检查浏览器/输出目录日志）");
        }
        String mail = salesDailyReportService.sendMail(reportDate(req), Collections.singletonList(png));
        return ApiResult.ok("销售详情2 截图：" + png + "\n" + mail);
    }

    /**
     * 手动生成销售详情1 截图（仅截图，不发送邮件），测试用。
     *
     * GET /provider/sales/report/test?date=2026-08-14
     * date 为空时默认取今天
     */
    @GetMapping("/report/test")
    public String test(@RequestParam(required = false) String date) {
        LocalDate d = (date == null || date.trim().isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(date.trim());
        return salesDailyReportService.generateDailyReport(d);
    }
}
