package com.demo.provider.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品出参 VO — 返回给前端的单个商品
 *
 * 用 @JsonProperty 保持蛇形 JSON 字段名，前端无需改动。
 */
@Data
@NoArgsConstructor
public class GoodsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("c_gcode")
    private String gcode;

    @JsonProperty("c_barcode")
    private String barcode;

    @JsonProperty("c_pluno")
    private String pluno;

    @JsonProperty("c_ccode")
    private String ccode;

    @JsonProperty("c_name")
    private String name;

    @JsonProperty("c_model")
    private String model;

    @JsonProperty("c_basic_unit")
    private String basicUnit;

    @JsonProperty("c_advice_price")
    private BigDecimal advicePrice;

    @JsonProperty("c_produce")
    private String produce;

    @JsonProperty("c_status")
    private String status;

    @JsonProperty("c_adno")
    private String adno;

    /**
     * 从 Goods 实体快捷构造
     */
    public static GoodsVO from(com.demo.provider.entity.Goods g) {
        if (g == null) return null;
        GoodsVO vo = new GoodsVO();
        vo.setGcode(g.getGcode());
        vo.setBarcode(g.getBarcode());
        vo.setPluno(g.getPluno());
        vo.setCcode(g.getCcode());
        vo.setName(g.getName());
        vo.setModel(g.getModel());
        vo.setBasicUnit(g.getBasicUnit());
        vo.setAdvicePrice(g.getAdvicePrice());
        vo.setProduce(g.getProduce());
        vo.setStatus(g.getStatus());
        vo.setAdno(g.getAdno());
        return vo;
    }
}
