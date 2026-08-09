package com.demo.provider.controller;

import com.demo.provider.dto.GoodsQueryDTO;
import com.demo.provider.service.UserService;
import com.demo.provider.utils.ApiResult;
import com.demo.provider.vo.GoodsCcodeVO;
import com.demo.provider.vo.GoodsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/provider")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 分页查询商品列表（统一 ApiResult 返回，分页信息挂载在 ApiResult 上）
     */
    @GetMapping("/user/list")
    public ApiResult<List<GoodsVO>> listGoods(GoodsQueryDTO query) {
        return userService.getGoodsPage(
                query.getPage(), query.getSize(), query.getKeyword(), query.getCcode());
    }

    /**
     * 按商品编码查询单条
     */
    @GetMapping("/user/{gcode}")
    public ApiResult<GoodsVO> getGoods(@PathVariable String gcode) {
        return userService.getGoodsByCode(gcode);
    }

    /**
     * 获取所有品类编码列表
     */
    @GetMapping("/user/ccodes")
    public ApiResult<List<GoodsCcodeVO>> getCcodes() {
        return userService.getAllCcodes();
    }

    /**
     * 诊断接口: 列出 RDS_BC 库中所有表
     */
    @GetMapping("/diag/tables")
    public List<Map<String, Object>> listTables() {
        return userService.listTables();
    }
}
