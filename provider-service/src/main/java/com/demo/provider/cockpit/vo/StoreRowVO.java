package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 驾驶舱 - 门店销售总览一行 */
@Data
public class StoreRowVO {

    private Integer seq;

    /** 门店名称 */
    private String name;

    /** 所属区域 */
    private String region;

    /** 昨日销售（万元） */
    private BigDecimal yesterday;

    /** 销售同比 */
    private BigDecimal yoy;

    /** 销售额环比 */
    private BigDecimal mom;

    /** 毛利率 */
    private BigDecimal grossRate;

    /** 客单价（元） */
    private BigDecimal avgTicket;

    /** 来客数 */
    private Integer customers;
}
