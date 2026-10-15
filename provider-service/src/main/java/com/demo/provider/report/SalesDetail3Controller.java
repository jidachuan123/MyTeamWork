package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售详情3（SD3）接口 —— 2026-10-15 新建。
 *
 * 全部走 /provider/sd3/**（与 SD1 的 /provider/sales/** 分开，互不干扰），
 * 前端通过 vite 的 /api/provider 代理访问，无需新增代理规则。
 *
 * 接口清单：
 *   GET /provider/sd3/options      页面初始化用：渠道下拉选项 + 默认机构/渠道
 *   GET /provider/sd3/query        表格查询（参数与页面查询框一一对应）
 */
@RestController
@RequestMapping("/provider/sd3")
public class SalesDetail3Controller {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail3Controller.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetail3Service service;

    @Autowired
    private Sd3Properties props;

    @Autowired
    private SalesDetail3Scheduler scheduler;

    /** 页面初始化：渠道下拉选项、默认机构/渠道、默认日期（前一天） */
    @GetMapping("/options")
    public Map<String, Object> options() {
        Map<String, Object> m = new HashMap<>();
        m.put("chnlOptions", props.getChnlOptions());
        m.put("defaultOrgCode", props.getDefaultOrgCode());
        m.put("defaultChnl", props.getDefaultChnl());
        m.put("defaultStartDate", LocalDate.now().minusDays(1).format(DTF));
        m.put("defaultEndDate", LocalDate.now().minusDays(1).format(DTF));
        return m;
    }

    /**
     * 表格查询。
     *
     * @param startDate 开始日期（默认前一天）
     * @param endDate   结束日期（默认前一天）
     * @param orgCode   机构：门店编码前缀，空=不筛选
     * @param chnl      渠道：取值来自 /options 的 chnlOptions，空=全部渠道
     * @param orderNo   订单号，默认空
     */
    @GetMapping("/query")
    public Map<String, Object> query(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String orgCode,
            @RequestParam(required = false) String chnl,
            @RequestParam(required = false) String orderNo) {

        String d1 = blank(startDate) ? LocalDate.now().minusDays(1).format(DTF) : startDate.trim();
        String d2 = blank(endDate) ? LocalDate.now().minusDays(1).format(DTF) : endDate.trim();
        String org = blank(orgCode) ? "" : orgCode.trim();
        String ch = blank(chnl) ? "" : chnl.trim();
        String no = blank(orderNo) ? "" : orderNo.trim();

        SalesDetail3Service.Sd3Result result = service.query(d1, d2, org, ch, no);
        List<SalesDetail3Service.Sd3Row> rows = result.getRows();

        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("startDate", d1);
        m.put("endDate", d2);
        m.put("orgCode", org);
        m.put("chnl", ch);
        m.put("rows", rows);
        m.put("total", result.getTotal());
        log.info("[销售详情3] 页面查询返回 {} 行（含合计/总计行）", rows.size());
        return m;
    }

    /**
     * 手动触发一次定时推送（用 application-sd3.yml 里配置的机构/渠道/日期）。
     * 异步执行，接口立即返回；结果看日志。
     */
    @GetMapping("/trigger")
    public Map<String, Object> trigger() {
        new Thread(() -> {
            try {
                String r = scheduler.runNow();
                log.info("[销售详情3] 手动触发结果：\n{}", r);
            } catch (Exception e) {
                log.error("[销售详情3] 手动触发异常", e);
            }
        }).start();
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("message", "销售详情3 推送任务已触发，请查看日志/邮箱/企微");
        return m;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
