package com.demo.consumer.external.feign;

import com.demo.consumer.external.vo.DemoGoodsVO;
import com.demo.consumer.utils.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Provider External 接口的 Feign 客户端
 *
 * <p>用于 Consumer 内部调用 Provider 的 external 接口。
 * 后续对接新的外部系统时，新增方法即可。</p>
 */
@FeignClient(name = "provider-service", contextId = "demoExternalFeignClient")
public interface DemoExternalFeignClient {

    /**
     * 查询商品（通过 Provider → 外部系统）
     */
    @GetMapping("/provider/external/demo/goods/query")
    ApiResult<List<DemoGoodsVO>> queryGoods(
            @RequestParam(value = "goodsCode", required = false) String goodsCode,
            @RequestParam(value = "goodsName", required = false) String goodsName,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);
}
