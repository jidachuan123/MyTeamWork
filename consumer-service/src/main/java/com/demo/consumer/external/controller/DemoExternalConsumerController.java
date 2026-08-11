package com.demo.consumer.external.controller;

import com.demo.consumer.external.feign.DemoExternalFeignClient;
import com.demo.consumer.external.vo.DemoGoodsVO;
import com.demo.consumer.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/consumer/external/demo")
public class DemoExternalConsumerController {

    @Resource
    private DemoExternalFeignClient demoExternalFeignClient;

    @GetMapping("/goods/query")
    @RequiresPermissions("external:view")
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
