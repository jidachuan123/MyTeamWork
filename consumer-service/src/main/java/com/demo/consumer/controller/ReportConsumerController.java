package com.demo.consumer.controller;

import com.demo.consumer.feign.ReportFeignClient;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/consumer/report")
public class ReportConsumerController {

    @Autowired
    private ReportFeignClient reportFeignClient;

    @GetMapping("/inventory")
    @RequiresPermissions("report:view")
    public List<Map<String, Object>> inventoryReport(
            @RequestParam(required = false) String orgCode,
            @RequestParam(required = false) String warehouse,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String docNo,
            @RequestParam(required = false) String docStatus,
            @RequestParam(defaultValue = "8") String tenantId,
            @RequestParam(defaultValue = "L") String lang,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String checkResult,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return reportFeignClient.inventoryReport(orgCode, warehouse, department, docNo,
                docStatus, tenantId, lang, location, checkResult, startDate, endDate);
    }
}
