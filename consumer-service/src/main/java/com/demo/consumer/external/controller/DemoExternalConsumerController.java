package com.demo.consumer.external.controller;

import com.demo.consumer.external.feign.DemoExternalFeignClient;
import com.demo.consumer.external.vo.DemoGoodsVO;
import com.demo.consumer.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 示例：Consumer 端 External 调用入口
 *
 * <p>前端或调用方访问这里的接口，内部走 Feign → Provider External Controller → ExternalService → 外部系统。</p>
 *
 * <p>后续对接新的外部系统时，参照本类编写新 Consumer Controller:</p>
 * <ol>
 *   <li>新增 XXController → {@code @RequestMapping("/consumer/external/xxx")}</li>
 *   <li>注入对应的 XXFeignClient</li>
 *   <li>接收参数，委托 Feign，透传 ApiResult</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/consumer/external/demo")
public class DemoExternalConsumerController {

    @Resource
    private DemoExternalFeignClient demoExternalFeignClient;

    /**
     * 查询商品 — 对接外部系统（Mock 模式）
     *
     * <p>调用链路：前端 → Consumer Controller → Feign → Provider Controller → ExternalService → [外部系统]</p>
     *
     * @param goodsCode 商品编码（可选）
     * @param goodsName 商品名称（可选）
     * @param pageNum   页码（默认 1）
     * @param pageSize  每页条数（默认 10）
     */
    @GetMapping("/goods/query")
    public ApiResult<List<DemoGoodsVO>> queryGoods(
            @RequestParam(required = false) String goodsCode,
            @RequestParam(required = false) String goodsName,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {

        log.info("Consumer 收到外部商品查询请求: goodsCode={}, goodsName={}, pageNum={}, pageSize={}",
                goodsCode, goodsName, pageNum, pageSize);

        return demoExternalFeignClient.queryGoods(goodsCode, goodsName, pageNum, pageSize);
    }
}
