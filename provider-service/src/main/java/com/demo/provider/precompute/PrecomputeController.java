package com.demo.provider.precompute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售报表1 预计算接口（新开，不改原 SalesDetailController）
 *
 * 1. GET  /provider/sales/precompute?queryDate=&comparisonType=&orgCode=&deptLevels=
 *        查预计算表（秒级），替代原 6 次引擎并发查询
 * 2. POST /provider/sales/precompute/trigger?queryDate=
 *        手动触发跑批/回补（幂等：先删该日旧数据再重算）
 * 3. GET  /provider/sales/precompute/logs?limit=
 *        最近跑批记录（前端「预计算管理」面板）
 *
 * 🔴 安全红线：本接口只读 dw. 表；trigger 的删除仅限预计算表自身且带全条件。
 */
@RestController
@RequestMapping("/provider/sales/precompute")
public class PrecomputeController {

    private static final Logger log = LoggerFactory.getLogger(PrecomputeController.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private SaleDetailPrecomputeService saleDetailPrecomputeService;

    /**
     * 查预计算表（替代原 /provider/sales/detail 的 6 次引擎并发）
     *
     * deptLevels 默认空 = 机构汇总（前端不传即表示超市总计行）；传 3=明细 / 2=部门合计。
     * 示例：GET /provider/sales/precompute?queryDate=2026-08-15&comparisonType=MOM&orgCode=1101001&deptLevels=3
     */
    @GetMapping
    public List<Map<String, Object>> query(
            @RequestParam String queryDate,
            @RequestParam String comparisonType,
            @RequestParam(defaultValue = "1101001") String orgCode,
            @RequestParam(defaultValue = "") String deptLevels) {
        return saleDetailPrecomputeService.query(queryDate, comparisonType, orgCode, deptLevels);
    }

    /**
     * 手动触发跑批/回补（幂等，支持多机构）
     *
     * 示例：POST /provider/sales/precompute/trigger?queryDate=2026-08-15&orgCode=1101001,1102
     * 说明：同步执行（每个机构约 10-20 秒），返回 { code, queryDate, orgCodes, momRows, yoyRows, message }
     *       orgCode 支持多个，英文逗号分隔；空则默认 1101001。
     */
    @PostMapping("/trigger")
    public Map<String, Object> trigger(
            @RequestParam String queryDate,
            @RequestParam(defaultValue = "1101001") String orgCode) {
        LocalDate qd = parseDate(queryDate);
        List<String> orgs = parseOrgCodes(orgCode);
        log.info("[预计算] 手动触发跑批 queryDate={} orgCodes={}", qd, orgs);
        Map<String, Object> ret = new LinkedHashMap<>();
        try {
            Map<String, Object> result = saleDetailPrecomputeService.runPrecompute(qd, "MANUAL", orgs);
            ret.putAll(result);
        } catch (IllegalArgumentException e) {
            ret.put("code", 1);
            ret.put("queryDate", queryDate);
            ret.put("orgCodes", orgs);
            ret.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("[预计算] 手动跑批异常 queryDate={}", queryDate, e);
            ret.put("code", 1);
            ret.put("queryDate", queryDate);
            ret.put("orgCodes", orgs);
            ret.put("message", "跑批异常: " + e.getMessage());
        }
        return ret;
    }

    /**
     * 最近跑批记录（预计算管理面板）
     */
    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(defaultValue = "20") int limit) {
        return saleDetailPrecomputeService.batchLogs(limit);
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("业务日期不能为空");
        }
        try {
            return LocalDate.parse(s.trim(), DTF);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("业务日期格式错误（应为 yyyy-MM-dd）: " + s);
        }
    }

    /** 解析机构编码：英文逗号分隔、去空白、去空、去重（保序）；全部为空则回退默认 1101001 */
    private List<String> parseOrgCodes(String orgCode) {
        List<String> list = new ArrayList<>();
        if (orgCode == null || orgCode.trim().isEmpty()) {
            list.add("1101001");
            return list;
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String s : orgCode.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        if (set.isEmpty()) {
            list.add("1101001");
            return list;
        }
        list.addAll(set);
        return list;
    }
}
