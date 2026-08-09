package com.demo.provider.external.controller;

import com.demo.provider.external.demo.DemoExternalService;
import com.demo.provider.external.demo.dto.resp.DemoQueryResp;
import com.demo.provider.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 示例：对外部系统对接接口的 Web 层暴露
 *
 * <p>Provider 本地 Controller，接收 Feign / HTTP 调用，
 * 委托 {@link DemoExternalService} 完成外部系统对接，统一 ApiResult 返回。</p>
 *
 * <p>后续对接新的外部系统时，参照本类编写新 Controller:</p>
 * <ol>
 *   <li>新增 XXController → {@code @RequestMapping("/provider/external/xxx")}</li>
 *   <li>注入对应的 XXService</li>
 *   <li>接收参数后委托 Service，用 ApiResult 包装</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/provider/external/demo")
public class DemoExternalController {

    @Resource
    private DemoExternalService demoExternalService;

    /**
     * 查询商品 — 对接外部系统
     *
     * @param goodsCode 商品编码（可选）
     * @param goodsName 商品名称（可选，模糊匹配）
     * @param pageNum   页码（默认 1）
     * @param pageSize  每页条数（默认 10）
     */
    @GetMapping("/goods/query")
    public ApiResult<List<DemoQueryResp.GoodsItem>> queryGoods(
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String goodsName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        log.info("收到外部商品查询请求: goodsCode={}, goodsName={}, pageNum={}, pageSize={}",
                goodsCode, goodsName, pageNum, pageSize);

        List<DemoQueryResp.GoodsItem> list = demoExternalService.queryGoods(
                goodsCode, goodsName, pageNum, pageSize);

        return ApiResult.ok(list);
    }
}
