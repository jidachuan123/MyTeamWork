package com.demo.consumer.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品出参 VO（Consumer 端，接收 Provider JSON 反序列化）
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
}
