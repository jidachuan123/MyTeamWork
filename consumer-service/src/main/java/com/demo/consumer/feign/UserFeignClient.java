package com.demo.consumer.feign;

import com.demo.consumer.utils.ApiResult;
import com.demo.consumer.vo.GoodsCcodeVO;
import com.demo.consumer.vo.GoodsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "provider-service", contextId = "userFeignClient")
public interface UserFeignClient {

    @GetMapping("/provider/user/list")
    ApiResult<List<GoodsVO>> listGoods(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "ccode", required = false) String ccode);

    @GetMapping("/provider/user/{gcode}")
    ApiResult<GoodsVO> getGoods(@PathVariable("gcode") String gcode);

    @GetMapping("/provider/user/ccodes")
    ApiResult<List<GoodsCcodeVO>> getCcodes();
}
