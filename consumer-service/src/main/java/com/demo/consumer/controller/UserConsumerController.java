package com.demo.consumer.controller;

import com.demo.consumer.dto.GoodsQueryDTO;
import com.demo.consumer.feign.UserFeignClient;
import com.demo.consumer.utils.ApiResult;
import com.demo.consumer.vo.GoodsCcodeVO;
import com.demo.consumer.vo.GoodsVO;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/consumer/user")
public class UserConsumerController {

    @Autowired
    private UserFeignClient userFeignClient;

    @GetMapping("/list")
    @RequiresPermissions("user:list")
    public ApiResult<List<GoodsVO>> listGoods(GoodsQueryDTO query) {
        return userFeignClient.listGoods(
                query.getPage(), query.getSize(), query.getKeyword(), query.getCcode());
    }

    @GetMapping("/{gcode}")
    @RequiresPermissions("user:view")
    public ApiResult<GoodsVO> getGoods(@PathVariable String gcode) {
        return userFeignClient.getGoods(gcode);
    }

    @GetMapping("/ccodes")
    @RequiresPermissions("user:ccodes")
    public ApiResult<List<GoodsCcodeVO>> getCcodes() {
        return userFeignClient.getCcodes();
    }
}
