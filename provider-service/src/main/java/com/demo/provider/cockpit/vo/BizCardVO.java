package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 驾驶舱 - 底部业态卡片 */
@Data
public class BizCardVO {

    /** 名称，如 海湾佳和诚 */
    private String name;

    /** 业态标签，如 购物中心 / 样板店 / 酒店 */
    private String type;

    /** 昨日销售（万元） */
    private BigDecimal yesterday;

    /** 日环比 */
    private BigDecimal dod;

    /** 年同比 */
    private BigDecimal yoy;

    /** 月累计（万元） */
    private BigDecimal monthAcc;

    /** 年累计（万元） */
    private BigDecimal yearAcc;
}
