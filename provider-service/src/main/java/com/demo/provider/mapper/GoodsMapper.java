package com.demo.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.provider.entity.Goods;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品表 Mapper
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
}
