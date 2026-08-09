package com.demo.consumer.controller;

import com.demo.consumer.dto.GoodsQueryDTO;
import com.demo.consumer.feign.UserFeignClient;
import com.demo.consumer.utils.ApiResult;
import com.demo.consumer.vo.GoodsCcodeVO;
import com.demo.consumer.vo.GoodsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/consumer/user")
public class UserConsumerController {

    @Autowired
    private UserFeignClient userFeignClient;

    /**
     * 分页查询商品列表（统一 ApiResult 返回）
     */
    @GetMapping("/list")
    public ApiResult<List<GoodsVO>> listGoods(GoodsQueryDTO query) {
        return userFeignClient.listGoods(
                query.getPage(), query.getSize(), query.getKeyword(), query.getCcode());
    }

    /**
     * 按商品编码查询单条
     */
    @GetMapping("/{gcode}")
    public ApiResult<GoodsVO> getGoods(@PathVariable String gcode) {
        return userFeignClient.getGoods(gcode);
    }

    /**
     * 获取所有品类编码
     */
    @GetMapping("/ccodes")
    public ApiResult<List<GoodsCcodeVO>> getCcodes() {
        return userFeignClient.getCcodes();
    }
}
