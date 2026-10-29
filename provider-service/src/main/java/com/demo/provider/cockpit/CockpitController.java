package com.demo.provider.cockpit;

import com.demo.provider.cockpit.vo.CockpitOverviewVO;
import com.demo.provider.utils.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营驾驶舱（3004 大屏）接口 —— 2026-10-29 新建。
 *
 * 独立包 com.demo.provider.cockpit，与 report / external 等业务包隔离；
 * 前端 MyCockpit(3004) 通过 vite 代理 /api/provider → 8001 访问。
 *
 * 接口：
 *   GET /provider/cockpit/overview    整屏数据（当前为示例数据，见 CockpitService 注释）
 *   GET /provider/cockpit/ping        连通性自检（大屏调试用）
 */
@RestController
@RequestMapping("/provider/cockpit")
public class CockpitController {

    private final CockpitService cockpitService;

    public CockpitController(CockpitService cockpitService) {
        this.cockpitService = cockpitService;
    }

    /**
     * 整屏数据。
     *
     * @param date 业务日期，yyyy-MM-dd；不传 = 按当前时间推算（预留参数，接真实数据时使用）
     */
    @GetMapping("/overview")
    public ApiResult<CockpitOverviewVO> overview(
            @RequestParam(value = "date", required = false) String date) {
        return ApiResult.ok(cockpitService.buildOverview());
    }

    /** 连通性自检 */
    @GetMapping("/ping")
    public ApiResult<String> ping() {
        return ApiResult.ok("cockpit-ok");
    }
}
