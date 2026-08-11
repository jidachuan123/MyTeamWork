package com.demo.consumer.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "provider-service", contextId = "reportFeignClient")
public interface ReportFeignClient {

    @GetMapping("/provider/report/inventory")
    List<Map<String, Object>> inventoryReport(
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "warehouse", required = false) String warehouse,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "docNo", required = false) String docNo,
            @RequestParam(value = "docStatus", required = false) String docStatus,
            @RequestParam(value = "tenantId", defaultValue = "8") String tenantId,
            @RequestParam(value = "lang", defaultValue = "L") String lang,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "checkResult", required = false) String checkResult,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate);
}
