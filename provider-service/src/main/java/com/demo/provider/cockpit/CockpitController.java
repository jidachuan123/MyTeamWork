package com.demo.provider.cockpit;

import com.demo.provider.cockpit.vo.CockpitOverviewVO;
import com.demo.provider.cockpit.vo.StoreDetailVO;
import com.demo.provider.utils.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 集团驾驶舱（3004 大屏）接口 —— 2026-10-29 新建，2026-11-23 接入 .120 真实数据。
 *
 * 独立包 com.demo.provider.cockpit，与 report / external 等业务包隔离；
 * 前端 MyVue(3004) 通过 vite 代理 /api/provider → 8001 访问。
 *
 * 接口：
 *   GET /provider/cockpit/overview    整屏数据
 *   GET /provider/cockpit/ping        连通性自检（大屏调试用）
 */
@RestController
@RequestMapping("/provider/cockpit")
public class CockpitController {

    private static final Logger log = LoggerFactory.getLogger(CockpitController.class);

    private final CockpitService cockpitService;

    public CockpitController(CockpitService cockpitService) {
        this.cockpitService = cockpitService;
    }

    /**
     * 整屏数据。
     *
     * @param date  业务日期 yyyy-MM-dd；不传 = 今天的前一天（tbs_rd_store 为 T+1 汇总表）
     * @param scope 页面入口 group / supermarket / mall（当前三入口共用集团数据，暂不区分）
     */
    @GetMapping("/overview")
    public ApiResult<CockpitOverviewVO> overview(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResult.ok(cockpitService.buildOverview(parseDate(date)));
    }

    /**
     * 门店下钻：点击购物中心卡片后查询该门店（含成员机构）的汇总 + 三级品类明细。
     *
     * @param storeId   门店/机构编码（必填，只允许数字和逗号）
     * @param storeName 展示用门店名（可选，仅回显）
     * @param date      业务日期 yyyy-MM-dd；不传 = 今天的前一天
     */
    @GetMapping("/store-detail")
    public ApiResult<StoreDetailVO> storeDetail(
            @RequestParam("storeId") String storeId,
            @RequestParam(value = "storeName", required = false) String storeName,
            @RequestParam(value = "date", required = false) String date) {
        try {
            return ApiResult.ok(cockpitService.buildStoreDetail(storeId, storeName, parseDate(date)));
        } catch (IllegalArgumentException e) {
            return ApiResult.failed(e.getMessage());
        }
    }

    /**
     * 业态下钻：点击业态卡片后查询该业态（c_class）的汇总 + 近 7 日趋势 + 门店列表。
     *
     * @param classCode 业态编码（卡片 c_group_id，必填）
     * @param className 展示用业态名（可选）
     * @param date      业务日期 yyyy-MM-dd；不传 = 今天的前一天
     */
    @GetMapping("/class-detail")
    public ApiResult<StoreDetailVO> classDetail(
            @RequestParam("classCode") String classCode,
            @RequestParam(value = "className", required = false) String className,
            @RequestParam(value = "date", required = false) String date) {
        try {
            return ApiResult.ok(cockpitService.buildClassDetail(classCode, className, parseDate(date)));
        } catch (IllegalArgumentException e) {
            return ApiResult.failed(e.getMessage());
        }
    }

    private LocalDate parseDate(String date) {
        if (date != null && !date.trim().isEmpty()) {
            try {
                return LocalDate.parse(date.trim());
            } catch (DateTimeParseException e) {
                log.warn("[驾驶舱] date 参数格式非法，忽略并使用默认值：{}", date);
            }
        }
        return null;
    }

    /** 连通性自检 */
    @GetMapping("/ping")
    public ApiResult<String> ping() {
        return ApiResult.ok("cockpit-ok");
    }
}
