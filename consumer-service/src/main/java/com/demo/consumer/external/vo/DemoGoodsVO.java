package com.demo.consumer.external.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 示例：外部系统返回的商品 VO（Consumer 端 Feign 反序列化用）
 *
 * <p>字段结构与 Provider 端 DemoQueryResp.GoodsItem 一致。
 * 实际对接时，按外部系统接口文档调整字段及命名。</p>
 */
@Data
@NoArgsConstructor
public class DemoGoodsVO {

    /** 商品编码 */
    private String goodsCode;

    /** 商品名称 */
    private String goodsName;

    /** 规格型号 */
    private String goodsModel;

    /** 单位 */
    private String unit;

    /** 单价 */
    private BigDecimal price;
}
