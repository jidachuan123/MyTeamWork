package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 集团驾驶舱整屏数据（结构对齐前端 MyVue/src/cockpit/mock/demo.js）。
 *
 * 页面按四个区块渲染：
 *   kpis      顶部 KPI 卡（昨日 / 本月 / 本年）
 *   bizCards  中部业态卡（购物中心门店 + 业态，共 8 张）
 *   ranking   右侧各业态排行
 *   trend7d   底部近 7 日趋势
 *
 * 金额单位统一「万元」，比率统一「小数」（0.0871 = 8.71%），由前端格式化。
 * 比率算不出来（去年同期无数据）时为 null，前端显示 "-"。
 */
@Data
public class CockpitOverviewVO {

    /** 数据时点，如 2026-11-23 14:30 */
    private String asOf;

    /** true = 示例数据（当前接真实数据后恒为 false；取数失败由前端自行退回 demo） */
    private boolean demo;

    private List<Kpi> kpis;

    private List<Card> bizCards;

    private List<Rank> ranking;

    private List<TrendPoint> trend7d;

    // ------------------------------------------------------------------ 顶部 KPI

    @Data
    public static class Kpi {
        private String key;
        private String label;
        private BigDecimal amount;
        private String unit;
        private String tone;
        private List<Rate> rates;
        private List<Foot> foot;
        private Side side;
        private Progress progress;
    }

    @Data
    public static class Rate {
        private String label;
        private BigDecimal value;

        public Rate() {
        }

        public Rate(String label, BigDecimal value) {
            this.label = label;
            this.value = value;
        }
    }

    @Data
    public static class Foot {
        private String label;
        private BigDecimal value;
        private BigDecimal trend;

        public Foot() {
        }

        public Foot(String label, BigDecimal value, BigDecimal trend) {
            this.label = label;
            this.value = value;
            this.trend = trend;
        }
    }

    @Data
    public static class Side {
        private String prefix;
        private BigDecimal value;
    }

    @Data
    public static class Progress {
        private String label;
        private BigDecimal value;
    }

    // ------------------------------------------------------------------ 业态卡

    @Data
    public static class Card {
        private String key;
        private String name;
        /** 门店编码（仅购物中心门店有值，用于点击下钻；业态卡为空） */
        private String storeId;
        /** 业态编码（仅业态卡有值，等于 c_group_id：10/21/22/40） */
        private String classCode;
        private String tag;
        private String tone;
        private BigDecimal amount;
        private BigDecimal dod;
        private BigDecimal yoy;
        private BigDecimal share;
        private String extraLabel;
        private String extraValue;
        private BigDecimal monthAcc;
        private BigDecimal yearAcc;
        private List<BigDecimal> spark;
    }

    // ------------------------------------------------------------------ 排行

    @Data
    public static class Rank {
        private Integer seq;
        private String name;
        private BigDecimal amount;
        private String color;

        public Rank() {
        }

        public Rank(Integer seq, String name, BigDecimal amount, String color) {
            this.seq = seq;
            this.name = name;
            this.amount = amount;
            this.color = color;
        }
    }

    // ------------------------------------------------------------------ 趋势

    @Data
    public static class TrendPoint {
        /** 显示用日期，如 11-22 */
        private String date;
        private BigDecimal amount;

        public TrendPoint() {
        }

        public TrendPoint(String date, BigDecimal amount) {
            this.date = date;
            this.amount = amount;
        }
    }
}
