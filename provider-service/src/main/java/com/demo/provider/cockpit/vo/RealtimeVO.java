package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 驾驶舱 - 今日实时销售（T+0） */
@Data
public class RealtimeVO {

    /** 今日累计销售（万元） */
    private BigDecimal amount;

    /** 取数时点，如 13:57 */
    private String asOfTime;

    /** 较昨日同时段增长率（小数） */
    private BigDecimal vsYesterday;
}
