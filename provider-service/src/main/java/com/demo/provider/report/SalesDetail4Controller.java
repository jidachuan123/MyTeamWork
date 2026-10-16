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
 * 销售详情4（SD4，配送调拨汇总表）接口 —— 2026-10-16 新建。
 *
 * 全部走 /provider/sd4/**（与 SD1/SD2/SD3 分开，互不干扰），
 * 前端通过 vite 的 /api/provider 代理访问，无需新增代理规则。
 *
 * 接口清单：
 *   GET /provider/sd4/options   页面初始化用：6 个日期默认值 + 发货机构默认值
 *   GET /provider/sd4/query     表格查询（参数 = 页面上 7 个可输入项：6 日期 + 发货机构）
 *   GET /provider/sd4/trigger   手动触发一次定时推送（异步）
 */
@RestController
@RequestMapping("/provider/sd4")
public class SalesDetail4Controller {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail4Controller.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SalesDetail4Service service;

    @Autowired
    private Sd4Properties props;

    @Autowired
    private SalesDetail4Scheduler scheduler;

    /**
     * 页面初始化：6 个日期默认值（与 SD1 推算逻辑一致：记账=前一天、环比=前两天、同比=去年同日）
     * + 发货机构默认值（yml 配置）。
     */
    @GetMapping("/options")
    public Map<String, Object> options() {
        LocalDate d = LocalDate.now();
        String acc = d.minusDays(1).format(DTF);
        String cmp = d.minusDays(2).format(DTF);
        String yoy = d.minusYears(1).format(DTF);

        Map<String, Object> m = new HashMap<>();
        m.put("defaultAccStartDate", acc);
        m.put("defaultAccEndDate", acc);
        m.put("defaultCmpStartDate", cmp);
        m.put("defaultCmpEndDate", cmp);
        m.put("defaultYoyStartDate", yoy);
        m.put("defaultYoyEndDate", yoy);
        m.put("defaultOutOrgCodes", props.getDefaultOutOrgCodes() == null ? "" : props.getDefaultOutOrgCodes());
        return m;
    }

    /**
     * 表格查询。页面上可输入的参数只有 7 个：6 个日期 + 发货机构；
     * 其余 SQL 参数（收货机构/部门/仓库/品类/类型/记账类型等）全部来自 application-sd4.yml。
     */
    @GetMapping("/query")
    public Map<String, Object> query(
            @RequestParam(required = false) String accStartDate,
            @RequestParam(required = false) String accEndDate,
            @RequestParam(required = false) String cmpStartDate,
            @RequestParam(required = false) String cmpEndDate,
            @RequestParam(required = false) String yoyStartDate,
            @RequestParam(required = false) String yoyEndDate,
            @RequestParam(required = false) String outOrgCodes) {

        SalesDetail4Service.Sd4Result result = service.query(
                accStartDate, accEndDate, cmpStartDate, cmpEndDate, yoyStartDate, yoyEndDate, outOrgCodes);
        List<SalesDetail4Service.Sd4Row> rows = result.getRows();

        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("rows", rows);
        m.put("accStartDate", result.getAccStartDate());
        m.put("accEndDate", result.getAccEndDate());
        m.put("cmpStartDate", result.getCmpStartDate());
        m.put("cmpEndDate", result.getCmpEndDate());
        m.put("yoyStartDate", result.getYoyStartDate());
        m.put("yoyEndDate", result.getYoyEndDate());
        m.put("outOrgCodes", result.getOutOrgCodes());
        log.info("[销售详情4] 页面查询返回 {} 行", rows.size());
        return m;
    }

    /**
     * 手动触发一次定时推送（用 application-sd4.yml 里配置的发货机构/日期）。
     * 异步执行，接口立即返回；结果看日志。
     */
    @GetMapping("/trigger")
    public Map<String, Object> trigger() {
        new Thread(() -> {
            try {
                String r = scheduler.runNow();
                log.info("[销售详情4] 手动触发结果：\n{}", r);
            } catch (Exception e) {
                log.error("[销售详情4] 手动触发异常", e);
            }
        }).start();
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("message", "销售详情4 推送任务已触发，请查看日志/邮箱/企微");
        return m;
    }
}
