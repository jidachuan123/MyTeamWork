package com.demo.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品主档实体 — 映射 dbo.tbi_imp_gds
 */
@Data
@TableName("tbi_imp_gds")
public class Goods {

    /** 商品编码 */
    @TableId("c_gcode")
    private String gcode;

    /** 商品条码 */
    @TableField("c_barcode")
    private String barcode;

    /** PLU号 */
    @TableField("c_pluno")
    private String pluno;

    /** 品类编码 */
    @TableField("c_ccode")
    private String ccode;

    /** 商品名称 */
    @TableField("c_name")
    private String name;

    /** 规格/型号 */
    @TableField("c_model")
    private String model;

    /** 基本单位 */
    @TableField("c_basic_unit")
    private String basicUnit;

    /** 建议售价 */
    @TableField("c_advice_price")
    private BigDecimal advicePrice;

    /** 产地 */
    @TableField("c_produce")
    private String produce;

    /** 状态 */
    @TableField("c_status")
    private String status;

    /** 部门编码 */
    @TableField("c_adno")
    private String adno;
}
