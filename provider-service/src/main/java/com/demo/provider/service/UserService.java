package com.demo.provider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.provider.entity.Goods;
import com.demo.provider.mapper.GoodsMapper;
import com.demo.provider.utils.ApiResult;
import com.demo.provider.vo.GoodsCcodeVO;
import com.demo.provider.vo.GoodsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品 Service — MyBatis Plus 查询 + 统一 ApiResult 返回
 *
 * 查询 RDS_BC 库的 dbo.tbi_imp_gds 表（商品主档）
 */
@Service
public class UserService {

    @Autowired
    private GoodsMapper goodsMapper;

    /**
     * 分页查询商品列表，支持关键词搜索和品类过滤
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param keyword 搜索关键词（匹配商品编码、条码、名称、PLU号）
     * @param ccode   品类编码过滤
     * @return ApiResult，泛型 result 为商品 VO 列表，page/size/total 挂载在 ApiResult 上
     */
    public ApiResult<List<GoodsVO>> getGoodsPage(int page, int size, String keyword, String ccode) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Goods::getGcode, kw)
                             .or().like(Goods::getBarcode, kw)
                             .or().like(Goods::getName, kw)
                             .or().like(Goods::getPluno, kw));
        }

        if (ccode != null && !ccode.trim().isEmpty()) {
            wrapper.eq(Goods::getCcode, ccode.trim());
        }

        // 计数查询（无排序 — SQL Server 子查询不允许 ORDER BY）
        LambdaQueryWrapper<Goods> countWrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            countWrapper.and(w -> w.like(Goods::getGcode, kw)
                                   .or().like(Goods::getBarcode, kw)
                                   .or().like(Goods::getName, kw)
                                   .or().like(Goods::getPluno, kw));
        }
        if (ccode != null && !ccode.trim().isEmpty()) {
            countWrapper.eq(Goods::getCcode, ccode.trim());
        }

        // 数据查询加排序
        wrapper.orderByAsc(Goods::getGcode);

        Page<Goods> pageParam = new Page<>(page, size);
        pageParam.setSearchCount(false);
        Page<Goods> result = goodsMapper.selectPage(pageParam, wrapper);
        long total = goodsMapper.selectCount(countWrapper);
        result.setTotal(total);

        List<GoodsVO> voList = result.getRecords().stream()
                .map(GoodsVO::from)
                .collect(Collectors.toList());

        ApiResult<List<GoodsVO>> apiResult = ApiResult.ok(voList);
        apiResult.setPage(page);
        apiResult.setSize(size);
        apiResult.setTotal(total);
        apiResult.setCount(voList.size());
        return apiResult;
    }

    /**
     * 按商品编码查询单条商品
     */
    public ApiResult<GoodsVO> getGoodsByCode(String gcode) {
        LambdaQueryWrapper<Goods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Goods::getGcode, gcode);
        Goods goods = goodsMapper.selectOne(wrapper);
        GoodsVO vo = GoodsVO.from(goods);
        return vo != null ? ApiResult.ok(vo) : ApiResult.failed("商品不存在");
    }

    /**
     * 获取所有品类编码（用于下拉筛选）
     */
    public ApiResult<List<GoodsCcodeVO>> getAllCcodes() {
        QueryWrapper<Goods> wrapper = new QueryWrapper<>();
        wrapper.select("DISTINCT c_ccode")
               .isNotNull("c_ccode")
               .ne("c_ccode", "")
               .orderByAsc("c_ccode");

        List<Object> objs = goodsMapper.selectObjs(wrapper);
        List<GoodsCcodeVO> voList = objs.stream()
                .map(obj -> new GoodsCcodeVO(obj != null ? obj.toString() : ""))
                .collect(Collectors.toList());

        return ApiResult.ok(voList);
    }

    // ==================== 诊断方法（仍用 JdbcTemplate） ====================

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 诊断: 列出当前库所有表
     */
    public List<Map<String, Object>> listTables() {
        String tableSql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                          "WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(tableSql);

        String dbSql = "SELECT name FROM sys.databases WHERE state_desc = 'ONLINE' ORDER BY name";
        List<Map<String, Object>> dbs = jdbcTemplate.queryForList(dbSql);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> t : tables) {
            Map<String, Object> r = new HashMap<>();
            r.put("type", "TABLE");
            r.put("schema", t.get("TABLE_SCHEMA"));
            r.put("name", t.get("TABLE_NAME"));
            result.add(r);
        }
        for (Map<String, Object> d : dbs) {
            Map<String, Object> r = new HashMap<>();
            r.put("type", "DATABASE");
            r.put("name", d.get("name"));
            result.add(r);
        }
        return result;
    }
}
