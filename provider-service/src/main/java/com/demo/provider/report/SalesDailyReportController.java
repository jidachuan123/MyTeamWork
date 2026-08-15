package com.demo.provider.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 销售日报手动触发接口（测试用，不参与定时调度）
 */
@RestController
@RequestMapping("/provider/sales")
public class SalesDailyReportController {

    @Autowired
    private SalesDailyReportService salesDailyReportService;

    /**
     * 手动生成销售日报（取数→HTML→截图→发邮件）
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
