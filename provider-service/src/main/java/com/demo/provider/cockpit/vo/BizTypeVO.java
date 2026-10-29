package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 驾驶舱 - 业态销售（柱状图一项） */
@Data
public class BizTypeVO {

    /** 业态名称，如 商管业态 */
    private String name;

    /** 销售额（万元） */
    private BigDecimal amount;

    public BizTypeVO() {
    }

    public BizTypeVO(String name, String amount) {
        this.name = name;
        this.amount = new BigDecimal(amount);
    }
}
