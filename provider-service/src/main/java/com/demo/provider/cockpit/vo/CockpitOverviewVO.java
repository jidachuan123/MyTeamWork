package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.util.List;

/**
 * 驾驶舱整屏数据（一次取全）
 *
 * 结构对应页面五个区块：
 *   ① trend7d      销售趋势（近 7 天）
 *   ② groupYesterday / monthToDate / yearToDate   集团销售三件套
 *   ③ todayRealtime + bizTypes    今日实时 + 各业态柱状图
 *   ④ bizCards     各业态昨日经营（11 张卡片）
 *   ⑤ stores       各门店销售总览
 *
 * 后续要拆接口时，这里不用动结构，只是把字段分组返回即可。
 */
@Data
public class CockpitOverviewVO {

    /** 数据时点，如 2026-10-28 21:30 */
    private String asOf;

    /** true = 示例数据（页面右上角显示「示例数据」角标） */
    private boolean demo;

    /** ③ 今日实时销售 */
    private RealtimeVO todayRealtime;

    /** ② 昨日集团销售（dod + yoy） */
    private KpiVO groupYesterday;

    /** ② 本月累计（mom + yoy） */
    private KpiVO monthToDate;

    /** ② 本年累计（yoy） */
    private KpiVO yearToDate;

    /** ① 近 7 天趋势 */
    private List<TrendPointVO> trend7d;

    /** ③ 今日各业态 */
    private List<BizTypeVO> bizTypes;

    /** ⑤ 门店总览 */
    private List<StoreRowVO> stores;

    /** ④ 业态卡片 */
    private List<BizCardVO> bizCards;
}
