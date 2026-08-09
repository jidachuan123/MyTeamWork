package com.demo.provider.controller;

import com.demo.provider.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 盘点报表接口
 *
 * 对应 RDS_SC 库的盘点报表 SQL，参数由接口传入。
 *
 * 示例请求:
 *   GET /provider/report/inventory?orgCode=1104001&docNo=PD12509300010&tenantId=8&startDate=2025-09-01&endDate=2025-09-30
 */
@RestController
@RequestMapping("/provider/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 盘点报表查询
     *
     * @param orgCode     机构
     * @param warehouse   仓库
     * @param department  部门
     * @param docNo       盘点单据号
     * @param docStatus   单据状态（尚未审核/已审核/已完成）
     * @param tenantId    租户ID
     * @param lang        语言
     * @param location    位置
     * @param checkResult 盘点结果（盘盈/盘亏/盘平/漏盘）
     * @param startDate   开始时间（yyyy-MM-dd）
     * @param endDate     结束时间（yyyy-MM-dd）
     */
    @GetMapping("/inventory")
    public List<Map<String, Object>> inventoryReport(
            @RequestParam(defaultValue = "") String orgCode,       // 机构
            @RequestParam(defaultValue = "") String warehouse,      // 仓库
            @RequestParam(defaultValue = "") String department,     // 部门
            @RequestParam(defaultValue = "") String docNo,          // 盘点单据号
            @RequestParam(defaultValue = "") String docStatus,      // 单据状态
            @RequestParam(defaultValue = "8") String tenantId,      // 租户ID
            @RequestParam(defaultValue = "L") String lang,          // 语言
            @RequestParam(defaultValue = "") String location,       // 位置
            @RequestParam(defaultValue = "") String checkResult,    // 盘点结果
            @RequestParam(defaultValue = "2025-09-01") String startDate, // 开始时间
            @RequestParam(defaultValue = "2025-09-30") String endDate    // 结束时间
    ) {
        return reportService.getInventoryReport(
                orgCode, warehouse, department, docNo,
                docStatus, tenantId, lang, location,
                checkResult, startDate, endDate);
    }
}
