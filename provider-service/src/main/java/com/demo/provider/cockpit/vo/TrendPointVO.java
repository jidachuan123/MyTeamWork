package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 驾驶舱 - 销售趋势单点 */
@Data
public class TrendPointVO {

    /** 显示用日期，如 10-22 */
    private String date;

    /** 本期销售额（万元） */
    private BigDecimal amount;

    /** 去年同期（万元），可为空 —— 为空时前端不画对照虚线 */
    private BigDecimal lastYear;

    public TrendPointVO() {
    }

    public TrendPointVO(String date, String amount, String lastYear) {
        this.date = date;
        this.amount = new BigDecimal(amount);
        this.lastYear = lastYear == null ? null : new BigDecimal(lastYear);
    }
}
