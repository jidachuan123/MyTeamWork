package com.demo.provider.controller;

import com.demo.provider.service.SalesDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 销售详情报表接口
 *
 * 对应 RDS_SC 库的销售详情 SQL，参数由接口传入。
 *
 * 示例请求:
 *   GET /provider/sales/detail?tenantId=8&startDate=2026-08-01&endDate=2026-08-10&orgCode=1101001
 */
@RestController
@RequestMapping("/provider/sales")
public class SalesDetailController {

    @Autowired
    private SalesDetailService salesDetailService;

    /**
     * 销售详情查询
     *
     * @param tenantId        租户ID，默认 8
     * @param lang            语言 L/E，默认 L
     * @param userNo          用户编号，默认 SYSTEM
     * @param dateType        日期类型（记账日期/发生日期），默认 记账日期
     * @param startDate       查询开始日期 yyyy-MM-dd，默认 2026-08-01
     * @param endDate         查询结束日期 yyyy-MM-dd，默认 2026-08-10
     * @param cmpStartDate    对比开始日期 yyyy-MM-dd，默认 2025-08-01
     * @param cmpEndDate      对比结束日期 yyyy-MM-dd，默认 2025-08-10
     * @param showStore       是否显示门店，默认 是
     * @param deptLevels      部门显示层级（逗号分隔，如 "3"），默认 3
     * @param catLevels       品类显示层级，默认空
     * @param showBrand       是否显示品牌，默认 不显示品牌
     * @param orgCode         机构编码，默认空（空=全部机构）
     * @param department      部门，默认空
     * @param category        品类，默认空
     * @param brand           品牌，默认空
     * @param channel         渠道，默认空
     * @param comparableStore 可比门店，默认 0
     * @param showPlan        是否展示计划，默认 否
     * @param showDate        是否显示日期，默认 显示日期
     * @return 销售详情结果列表
     */
    @GetMapping("/detail")
    public List<Map<String, Object>> salesDetail(
            @RequestParam(defaultValue = "8") String tenantId,
            @RequestParam(defaultValue = "L") String lang,
            @RequestParam(defaultValue = "SYSTEM") String userNo,
            @RequestParam(defaultValue = "记账日期") String dateType,
            @RequestParam(defaultValue = "2026-08-10") String startDate,
            @RequestParam(defaultValue = "2026-08-10") String endDate,
            @RequestParam(defaultValue = "2026-08-11") String cmpStartDate,
            @RequestParam(defaultValue = "2026-08-11") String cmpEndDate,
            @RequestParam(defaultValue = "显示门店") String showStore,
            @RequestParam(defaultValue = "") String deptLevels,
            @RequestParam(defaultValue = "") String catLevels,
            @RequestParam(defaultValue = "不显示品牌") String showBrand,
            @RequestParam(defaultValue = "") String orgCode,
            @RequestParam(defaultValue = "") String department,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String brand,
            @RequestParam(defaultValue = "") String channel,
            @RequestParam(defaultValue = "0") String comparableStore,
            @RequestParam(defaultValue = "否") String showPlan,
            @RequestParam(defaultValue = "显示日期") String showDate
    ) {
        return salesDetailService.getSalesDetail(
                tenantId, lang, userNo,
                dateType, startDate, endDate,
                cmpStartDate, cmpEndDate,
                showStore, deptLevels, catLevels, showBrand,
                orgCode, department, category, brand, channel,
                comparableStore, showPlan, showDate);
    }
}
