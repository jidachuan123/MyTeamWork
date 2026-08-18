package com.demo.provider.precompute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售报表1 预计算定时任务
 *
 * 每天凌晨 2:00 自动跑批（cron 可在 application.yml 的 report.precompute.cron 修改）：
 *   本期 = 昨天、环比对期 = 前天、同比对期 = 去年的今天（与前端 defaultQueryForm 规则一致）
 *
 * 机构编码在 application.yml 的 report.precompute.org-code 配置，支持多个（英文逗号分隔），
 * 例如：org-code: "1101001,1102"，定时任务会逐个机构跑批落库。
 *
 * 跑批只写 dw. 自有表；幂等可重跑；失败不影响生产数据。
 */
@Component
public class SaleDetailPrecomputeScheduler {

    private static final Logger log = LoggerFactory.getLogger(SaleDetailPrecomputeScheduler.class);

    @Value("${report.precompute.org-code:1101001}")
    private String orgCodeConfig;

    @Autowired
    private SaleDetailPrecomputeService saleDetailPrecomputeService;

    @Scheduled(cron = "${report.precompute.cron:0 0 2 * * ?}")
    public void runPrecompute() {
        LocalDate today = LocalDate.now();
        LocalDate queryDate = today.minusDays(1);   // 本期 = 昨天
        List<String> orgs = parseOrgCodes(orgCodeConfig);
        log.info("[预计算] 定时任务开始，业务日期={}，机构={}", queryDate, orgs);
        try {
            saleDetailPrecomputeService.runPrecompute(queryDate, "SCHEDULE", orgs);
        } catch (Exception e) {
            // 失败已由 Service 记录 FAILED 日志；这里只打日志，避免定时任务线程中断影响后续
            log.error("[预计算] 定时跑批失败 queryDate={}: {}", queryDate, e.getMessage(), e);
        }
    }

    /** 解析 application.yml 机构配置：英文逗号分隔、去空白、去空；全部为空则回退默认 1101001 */
    private List<String> parseOrgCodes(String cfg) {
        List<String> list = new ArrayList<>();
        if (cfg != null) {
            for (String s : cfg.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
        }
        if (list.isEmpty()) {
            list.add("1101001");
        }
        return list;
    }
}
