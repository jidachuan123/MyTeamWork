package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 驾驶舱 - 单个指标块（金额 + 三种比率）
 *
 * 单位约定：金额一律「万元」，比率一律「小数」（0.0871 = 8.71%），
 * 由前端统一格式化，后端不做百分比换算，避免两边口径打架。
 */
@Data
public class KpiVO {

    /** 金额（万元） */
    private BigDecimal amount;

    /** 日环比（昨日用） */
    private BigDecimal dod;

    /** 月环比（本月累计用） */
    private BigDecimal mom;

    /** 同比（年同比 / 月同比） */
    private BigDecimal yoy;

    public KpiVO() {
    }

    public KpiVO(BigDecimal amount) {
        this.amount = amount;
    }

    public static KpiVO of(String amount) {
        return new KpiVO(new BigDecimal(amount));
    }

    public KpiVO withDod(String v) {
        this.dod = new BigDecimal(v);
        return this;
    }

    public KpiVO withMom(String v) {
        this.mom = new BigDecimal(v);
        return this;
    }

    public KpiVO withYoy(String v) {
        this.yoy = new BigDecimal(v);
        return this;
    }
}
