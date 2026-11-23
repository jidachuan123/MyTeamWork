package com.demo.provider.cockpit.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 驾驶舱 · 门店下钻详情（点击购物中心卡片后展示）。
 *
 * 数据来源（.120 / RDS_SC，只读）：
 *   cockpit/store-detail.sql    门店汇总（含成员机构）
 *   cockpit/category-detail.sql 三级品类销售明细
 *
 * 金额单位「万元」，比率「小数」（0.0871 = 8.71%）。
 */
@Data
public class StoreDetailVO {

    /** 业务日期 yyyy-MM-dd */
    private String date;

    /** store = 门店下钻；class = 业态下钻 */
    private String type;

    private String storeId;

    private String storeName;

    /** 门店汇总 */
    private Summary summary;

    /** 三级品类明细（按昨日销售降序） */
    private List<Category> categories;

    /** 该门店近 7 日销售（万元），日期如 11-16 */
    private List<Trend> trend7d;

    /**
     * 业态下钻时的门店列表；门店下钻时为空（门店下钻用 categories）。
     */
    private List<StoreRow> stores;

    @Data
    public static class StoreRow {
        private String storeId;
        private String name;
        private BigDecimal daySale;
        private BigDecimal monthSale;
        private BigDecimal yearSale;
        private BigDecimal dayChain;
        private BigDecimal dayYear;
        private BigDecimal monthChain;
        private BigDecimal monthYear;
        private BigDecimal yearYear;
        /** 占本业态比重 */
        private BigDecimal share;
    }

    @Data
    public static class Trend {
        private String date;
        private BigDecimal amount;

        public Trend() {
        }

        public Trend(String date, BigDecimal amount) {
            this.date = date;
            this.amount = amount;
        }
    }

    @Data
    public static class Summary {
        /** 业态下钻时的门店数；门店下钻为 null */
        private Integer storeCount;
        /** 业态名称（业态下钻时由 tb_id_sys 取） */
        private String className;

        private BigDecimal daySale;
        private BigDecimal dayChain;
        private BigDecimal dayYear;

        private BigDecimal monthSale;
        private BigDecimal monthChain;
        private BigDecimal monthYear;

        private BigDecimal yearSale;
        private BigDecimal yearYear;
    }

    @Data
    public static class Category {
        private String ccode;
        private String name;

        private BigDecimal daySale;
        private BigDecimal monthSale;
        private BigDecimal yearSale;

        private BigDecimal dayChain;
        private BigDecimal dayYear;
        private BigDecimal monthChain;
        private BigDecimal monthYear;
        private BigDecimal yearYear;

        /** 占本门店比重 */
        private BigDecimal share;
    }
}
