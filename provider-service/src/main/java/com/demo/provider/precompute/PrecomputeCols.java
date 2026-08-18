package com.demo.provider.precompute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 销售报表1预计算表 dw.rpt_sale_detail_precompute 列映射常量。
 * 由脚本从建表 SQL / 设计文档自动生成，勿手改；加列时重新跑生成脚本。
 *
 * COLS：208 列（含 id，INSERT 时跳过自增列）
 * CH：英文列名 -> 引擎最终 SELECT 返回的中文别名
 * ACC_EQ：acc_* 累计列 -> 等价 C_* 列（记账日期模式下引擎 UPDATE C_ = C_ACC_，最终 SELECT 返回 C_）
 * NULL_COLS：引擎不返回/预留列，写入 NULL
 * SQL_TYPES：列名 -> JDBC 类型（setNull 用；mssql-jdbc 对 Types.NULL 会发 varbinary，必须指定具体类型）
 */
public final class PrecomputeCols {
    private PrecomputeCols() {}

    /** 208 列（含 id），顺序即写入顺序 */
    public static final List<String> COLS = Arrays.asList(
        "id",
        "batch_id",
        "etl_time",
        "query_date",
        "comparison_type",
        "cmp_start_date",
        "cmp_end_date",
        "report_type",
        "org_code",
        "dept_levels",
        "tenant_id",
        "date_type",
        "lang",
        "show_store",
        "show_brand",
        "show_date",
        "fs_date",
        "hs_date",
        "store_id",
        "store_name",
        "adno",
        "dept_id1",
        "dept_id2",
        "dept_id3",
        "dept_id4",
        "dept_id5",
        "dept_id6",
        "dept_id7",
        "dept_id8",
        "dept_name1",
        "dept_name2",
        "dept_name3",
        "dept_name4",
        "dept_name5",
        "dept_name6",
        "dept_name7",
        "dept_name8",
        "depart_name",
        "ccode",
        "cat_id1",
        "cat_id2",
        "cat_id3",
        "cat_id4",
        "cat_id5",
        "cat_id6",
        "cat_id7",
        "cat_id8",
        "cat_name1",
        "cat_name2",
        "cat_name3",
        "cat_name4",
        "cat_name5",
        "cat_name6",
        "cat_name7",
        "cat_name8",
        "cat_name",
        "gds_bcode",
        "sale_qty",
        "sale_qty_fpl",
        "sale_revenue",
        "wsale_revenue",
        "net_sale_revenue",
        "at_cost",
        "aet_cost",
        "disc_value",
        "at_disc",
        "profit",
        "wsale_profit",
        "net_profit",
        "sale_mem",
        "net_sale_mem",
        "sale_mem_rate_raw",
        "sale_revenue_pro",
        "net_sale_revenue_pro",
        "at_cost_pro",
        "aet_cost_pro",
        "profit_pro",
        "net_profit_pro",
        "sale_revenue_pro_rate_raw",
        "profit_pro_rate_raw",
        "pay_free",
        "sale_revenue_dq",
        "wsale_revenue_dq",
        "net_sale_revenue_dq",
        "at_cost_dq",
        "aet_cost_dq",
        "disc_value_dq",
        "at_disc_dq",
        "profit_dq",
        "wsale_profit_dq",
        "net_profit_dq",
        "sale_mem_dq",
        "net_sale_mem_dq",
        "sale_mem_rate_dq_raw",
        "sale_revenue_pro_dq",
        "net_sale_revenue_pro_dq",
        "at_cost_pro_dq",
        "aet_cost_pro_dq",
        "profit_pro_dq",
        "net_profit_pro_dq",
        "sale_revenue_pro_rate_dq_raw",
        "profit_pro_rate_dq_raw",
        "pay_free_dq",
        "sale_qty_dq",
        "sale_revenue_dbc",
        "profit_dbc",
        "sale_revenue_pm",
        "profit_pm",
        "count_trade",
        "count_trade_dq",
        "count_mem",
        "count_mem_dq",
        "plan_sale",
        "plan_profit",
        "acc_sale_qty",
        "acc_sale_qty_fpl",
        "acc_sale_revenue",
        "acc_wsale_revenue",
        "acc_net_sale_revenue",
        "acc_at_cost",
        "acc_aet_cost",
        "acc_disc_value",
        "acc_at_disc",
        "acc_profit",
        "acc_wsale_profit",
        "acc_net_profit",
        "acc_sale_mem",
        "acc_net_sale_mem",
        "acc_sale_mem_rate",
        "acc_sale_revenue_pro",
        "acc_net_sale_revenue_pro",
        "acc_at_cost_pro",
        "acc_aet_cost_pro",
        "acc_profit_pro",
        "acc_net_profit_pro",
        "acc_sale_revenue_pro_rate",
        "acc_profit_pro_rate",
        "acc_pay_free",
        "acc_sale_revenue_dbc",
        "acc_sale_revenue_dq",
        "acc_wsale_revenue_dq",
        "acc_net_sale_revenue_dq",
        "acc_at_cost_dq",
        "acc_aet_cost_dq",
        "acc_disc_value_dq",
        "acc_at_disc_dq",
        "acc_profit_dq",
        "acc_wsale_profit_dq",
        "acc_net_profit_dq",
        "acc_sale_mem_dq",
        "acc_net_sale_mem_dq",
        "acc_sale_mem_rate_dq",
        "acc_sale_revenue_pro_dq",
        "acc_net_sale_revenue_pro_dq",
        "acc_at_cost_pro_dq",
        "acc_aet_cost_pro_dq",
        "acc_profit_pro_dq",
        "acc_net_profit_pro_dq",
        "acc_sale_revenue_pro_rate_dq",
        "acc_profit_pro_rate_dq",
        "acc_pay_free_dq",
        "acc_profit_dbc",
        "acc_sale_revenue_pm",
        "acc_profit_pm",
        "profit_rate",
        "net_sale_amount",
        "net_profit_amount",
        "net_profit_rate",
        "wsale_revenue_rate",
        "wsale_profit_rate",
        "sale_mem_rate",
        "sale_revenue_pro_rate",
        "profit_pro_rate",
        "cust_price",
        "mem_cust_price",
        "mem_trade_rate",
        "plan_sale_rate",
        "plan_profit_rate",
        "sale_revenue_growth",
        "profit_growth",
        "brand_name",
        "dq_net_sale_amount",
        "dq_net_profit_amount",
        "dq_wsale_revenue_rate",
        "dq_wsale_profit_rate",
        "dq_sale_mem_rate",
        "dq_sale_revenue_pro_rate",
        "dq_profit_pro_rate",
        "dq_profit_rate",
        "dq_net_profit_rate",
        "dq_cust_price",
        "dq_mem_cust_price",
        "dq_mem_trade_rate",
        "profit_rate_growth",
        "net_sale_growth",
        "net_profit_growth",
        "net_profit_rate_growth",
        "stock_amount",
        "ext_01",
        "ext_02",
        "ext_03",
        "ext_04",
        "ext_05",
        "ext_06",
        "ext_07",
        "ext_08",
        "ext_09",
        "ext_10"
    );

    /** 英文列名 -> 引擎返回的中文 key（125 条） */
    public static final Map<String, String> CH = new HashMap<>();
    static {
        CH.put("fs_date", "日期");
        CH.put("store_id", "机构编码");
        CH.put("store_name", "机构名称");
        CH.put("adno", "部门编码");
        CH.put("dept_id1", "部门编码1");
        CH.put("dept_id2", "部门编码2");
        CH.put("dept_id3", "部门编码3");
        CH.put("dept_id4", "部门编码4");
        CH.put("dept_id5", "部门编码5");
        CH.put("dept_id6", "部门编码6");
        CH.put("dept_id7", "部门编码7");
        CH.put("dept_id8", "部门编码8");
        CH.put("dept_name1", "部门名称1");
        CH.put("dept_name2", "部门名称2");
        CH.put("dept_name3", "部门名称3");
        CH.put("dept_name4", "部门名称4");
        CH.put("dept_name5", "部门名称5");
        CH.put("dept_name6", "部门名称6");
        CH.put("dept_name7", "部门名称7");
        CH.put("dept_name8", "部门名称8");
        CH.put("depart_name", "部门名称");
        CH.put("ccode", "品类编码");
        CH.put("cat_id1", "品类编码1");
        CH.put("cat_id2", "品类编码2");
        CH.put("cat_id3", "品类编码3");
        CH.put("cat_id4", "品类编码4");
        CH.put("cat_id5", "品类编码5");
        CH.put("cat_id6", "品类编码6");
        CH.put("cat_id7", "品类编码7");
        CH.put("cat_id8", "品类编码8");
        CH.put("cat_name1", "品类名称1");
        CH.put("cat_name2", "品类名称2");
        CH.put("cat_name3", "品类名称3");
        CH.put("cat_name4", "品类名称4");
        CH.put("cat_name5", "品类名称5");
        CH.put("cat_name6", "品类名称6");
        CH.put("cat_name7", "品类名称7");
        CH.put("cat_name8", "品类名称8");
        CH.put("cat_name", "品类名称");
        CH.put("gds_bcode", "品牌编码");
        CH.put("sale_qty", "销售数量");
        CH.put("sale_qty_fpl", "生鲜盘点损溢数量");
        CH.put("sale_revenue", "销售金额");
        CH.put("wsale_revenue", "批发销售金额");
        CH.put("net_sale_revenue", "不含税销售额");
        CH.put("at_cost", "含税成本");
        CH.put("aet_cost", "不含税成本");
        CH.put("disc_value", "总折扣额");
        CH.put("at_disc", "供应商折扣承担金额");
        CH.put("profit", "含税毛利");
        CH.put("wsale_profit", "批发销售毛利");
        CH.put("net_profit", "不含税毛利");
        CH.put("sale_mem", "含税会员销售");
        CH.put("net_sale_mem", "不含税会员销售");
        CH.put("sale_revenue_pro", "促销销售");
        CH.put("net_sale_revenue_pro", "不含税促销销售");
        CH.put("at_cost_pro", "含税促销成本");
        CH.put("aet_cost_pro", "不含税促销成本");
        CH.put("profit_pro", "促销毛利");
        CH.put("net_profit_pro", "不含税促销毛利");
        CH.put("pay_free", "支付优惠");
        CH.put("sale_revenue_dq", "对期销售金额");
        CH.put("wsale_revenue_dq", "对期批发销售金额");
        CH.put("net_sale_revenue_dq", "对期不含税销售额");
        CH.put("at_cost_dq", "对期含税成本");
        CH.put("aet_cost_dq", "对期不含税成本");
        CH.put("disc_value_dq", "对期总折扣额");
        CH.put("at_disc_dq", "对期供应商折扣承担金额");
        CH.put("profit_dq", "对期含税毛利");
        CH.put("wsale_profit_dq", "对期批发销售毛利");
        CH.put("net_profit_dq", "对期不含税毛利");
        CH.put("sale_mem_dq", "对期含税会员销售");
        CH.put("net_sale_mem_dq", "对期不含税会员销售");
        CH.put("sale_revenue_pro_dq", "对期促销销售");
        CH.put("net_sale_revenue_pro_dq", "对期不含税促销销售");
        CH.put("at_cost_pro_dq", "对期含税促销成本");
        CH.put("aet_cost_pro_dq", "对期不含税促销成本");
        CH.put("profit_pro_dq", "对期促销毛利");
        CH.put("net_profit_pro_dq", "对期不含税促销毛利");
        CH.put("pay_free_dq", "对期支付优惠");
        CH.put("sale_revenue_dbc", "销售额增长率");
        CH.put("profit_dbc", "毛利额增长率");
        CH.put("sale_revenue_pm", "销售额排名");
        CH.put("profit_pm", "毛利额排名");
        CH.put("count_trade", "交易笔数");
        CH.put("count_trade_dq", "对期交易笔数");
        CH.put("count_mem", "会员交易笔数");
        CH.put("count_mem_dq", "对期会员交易笔数");
        CH.put("plan_sale", "销售计划");
        CH.put("plan_profit", "毛利计划");
        CH.put("profit_rate", "毛利率");
        CH.put("net_sale_amount", "销售净额");
        CH.put("net_profit_amount", "净毛利额");
        CH.put("net_profit_rate", "净毛利率");
        CH.put("wsale_revenue_rate", "批发销售占比");
        CH.put("wsale_profit_rate", "批发毛利占比");
        CH.put("sale_mem_rate", "会员销售占比");
        CH.put("sale_revenue_pro_rate", "促销销售占比");
        CH.put("profit_pro_rate", "促销毛利占比");
        CH.put("cust_price", "客单价");
        CH.put("mem_cust_price", "会员客单价");
        CH.put("mem_trade_rate", "会员交易占比");
        CH.put("plan_sale_rate", "销售计划达成率");
        CH.put("plan_profit_rate", "毛利计划达成率");
        CH.put("sale_revenue_growth", "销售额增长率");
        CH.put("profit_growth", "毛利额增长率");
        CH.put("brand_name", "品牌名称");
        CH.put("dq_net_sale_amount", "对期销售净额");
        CH.put("dq_net_profit_amount", "对期净毛利额");
        CH.put("dq_wsale_revenue_rate", "对期批发销售占比");
        CH.put("dq_wsale_profit_rate", "对期批发毛利占比");
        CH.put("dq_sale_mem_rate", "对期会员销售占比");
        CH.put("dq_sale_revenue_pro_rate", "对期促销销售占比");
        CH.put("dq_profit_pro_rate", "对期促销毛利占比");
        CH.put("dq_profit_rate", "对期毛利率");
        CH.put("dq_net_profit_rate", "对期净毛利率");
        CH.put("dq_cust_price", "对期客单价");
        CH.put("dq_mem_cust_price", "对期会员客单价");
        CH.put("dq_mem_trade_rate", "对期会员交易占比");
        CH.put("profit_rate_growth", "毛利率对比增长");
        CH.put("net_sale_growth", "销售净额对比增长");
        CH.put("net_profit_growth", "净毛利额对比增长");
        CH.put("net_profit_rate_growth", "净毛利率对比增长");
        CH.put("stock_amount", "当日库存金额");
        CH.put("hs_date", "记账日期");
    }

    /** acc_* -> 等价 C_* 列（44 条） */
    public static final Map<String, String> ACC_EQ = new HashMap<>();
    static {
        ACC_EQ.put("acc_sale_qty", "sale_qty");
        ACC_EQ.put("acc_sale_qty_fpl", "sale_qty_fpl");
        ACC_EQ.put("acc_sale_revenue", "sale_revenue");
        ACC_EQ.put("acc_wsale_revenue", "wsale_revenue");
        ACC_EQ.put("acc_net_sale_revenue", "net_sale_revenue");
        ACC_EQ.put("acc_at_cost", "at_cost");
        ACC_EQ.put("acc_aet_cost", "aet_cost");
        ACC_EQ.put("acc_disc_value", "disc_value");
        ACC_EQ.put("acc_at_disc", "at_disc");
        ACC_EQ.put("acc_profit", "profit");
        ACC_EQ.put("acc_wsale_profit", "wsale_profit");
        ACC_EQ.put("acc_net_profit", "net_profit");
        ACC_EQ.put("acc_sale_mem", "sale_mem");
        ACC_EQ.put("acc_net_sale_mem", "net_sale_mem");
        ACC_EQ.put("acc_sale_revenue_pro", "sale_revenue_pro");
        ACC_EQ.put("acc_net_sale_revenue_pro", "net_sale_revenue_pro");
        ACC_EQ.put("acc_at_cost_pro", "at_cost_pro");
        ACC_EQ.put("acc_aet_cost_pro", "aet_cost_pro");
        ACC_EQ.put("acc_profit_pro", "profit_pro");
        ACC_EQ.put("acc_net_profit_pro", "net_profit_pro");
        ACC_EQ.put("acc_pay_free", "pay_free");
        ACC_EQ.put("acc_sale_revenue_dbc", "sale_revenue_dbc");
        ACC_EQ.put("acc_sale_revenue_dq", "sale_revenue_dq");
        ACC_EQ.put("acc_wsale_revenue_dq", "wsale_revenue_dq");
        ACC_EQ.put("acc_net_sale_revenue_dq", "net_sale_revenue_dq");
        ACC_EQ.put("acc_at_cost_dq", "at_cost_dq");
        ACC_EQ.put("acc_aet_cost_dq", "aet_cost_dq");
        ACC_EQ.put("acc_disc_value_dq", "disc_value_dq");
        ACC_EQ.put("acc_at_disc_dq", "at_disc_dq");
        ACC_EQ.put("acc_profit_dq", "profit_dq");
        ACC_EQ.put("acc_wsale_profit_dq", "wsale_profit_dq");
        ACC_EQ.put("acc_net_profit_dq", "net_profit_dq");
        ACC_EQ.put("acc_sale_mem_dq", "sale_mem_dq");
        ACC_EQ.put("acc_net_sale_mem_dq", "net_sale_mem_dq");
        ACC_EQ.put("acc_sale_revenue_pro_dq", "sale_revenue_pro_dq");
        ACC_EQ.put("acc_net_sale_revenue_pro_dq", "net_sale_revenue_pro_dq");
        ACC_EQ.put("acc_at_cost_pro_dq", "at_cost_pro_dq");
        ACC_EQ.put("acc_aet_cost_pro_dq", "aet_cost_pro_dq");
        ACC_EQ.put("acc_profit_pro_dq", "profit_pro_dq");
        ACC_EQ.put("acc_net_profit_pro_dq", "net_profit_pro_dq");
        ACC_EQ.put("acc_pay_free_dq", "pay_free_dq");
        ACC_EQ.put("acc_profit_dbc", "profit_dbc");
        ACC_EQ.put("acc_sale_revenue_pm", "sale_revenue_pm");
        ACC_EQ.put("acc_profit_pm", "profit_pm");
    }

    /** 写入 NULL 的列（23 条：ext_01~10 预留 + 引擎不返回的 raw/acc 比率列 + sale_qty_dq） */
    public static final Set<String> NULL_COLS = new HashSet<>();
    static {
        NULL_COLS.add("ext_01");
        NULL_COLS.add("sale_qty_dq");
        NULL_COLS.add("acc_profit_pro_rate_dq");
        NULL_COLS.add("profit_pro_rate_raw");
        NULL_COLS.add("ext_10");
        NULL_COLS.add("sale_revenue_pro_rate_dq_raw");
        NULL_COLS.add("acc_sale_mem_rate_dq");
        NULL_COLS.add("ext_07");
        NULL_COLS.add("ext_09");
        NULL_COLS.add("ext_06");
        NULL_COLS.add("sale_revenue_pro_rate_raw");
        NULL_COLS.add("sale_mem_rate_dq_raw");
        NULL_COLS.add("acc_sale_revenue_pro_rate");
        NULL_COLS.add("ext_02");
        NULL_COLS.add("acc_profit_pro_rate");
        NULL_COLS.add("ext_03");
        NULL_COLS.add("ext_04");
        NULL_COLS.add("ext_08");
        NULL_COLS.add("profit_pro_rate_dq_raw");
        NULL_COLS.add("acc_sale_revenue_pro_rate_dq");
        NULL_COLS.add("ext_05");
        NULL_COLS.add("sale_mem_rate_raw");
        NULL_COLS.add("acc_sale_mem_rate");
    }

    /** 列名 -> JDBC 类型（setNull 必须用具体类型；mssql-jdbc 对 Types.NULL 发送 varbinary(8000) 会触发 date 列隐式转换错误） */
    public static final Map<String, Integer> SQL_TYPES = new HashMap<>();
    static {
        SQL_TYPES.put("id", java.sql.Types.BIGINT);
        SQL_TYPES.put("batch_id", java.sql.Types.VARCHAR);
        SQL_TYPES.put("etl_time", java.sql.Types.TIMESTAMP);
        SQL_TYPES.put("query_date", java.sql.Types.DATE);
        SQL_TYPES.put("comparison_type", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cmp_start_date", java.sql.Types.DATE);
        SQL_TYPES.put("cmp_end_date", java.sql.Types.DATE);
        SQL_TYPES.put("report_type", java.sql.Types.VARCHAR);
        SQL_TYPES.put("org_code", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_levels", java.sql.Types.INTEGER);
        SQL_TYPES.put("tenant_id", java.sql.Types.VARCHAR);
        SQL_TYPES.put("date_type", java.sql.Types.VARCHAR);
        SQL_TYPES.put("lang", java.sql.Types.VARCHAR);
        SQL_TYPES.put("show_store", java.sql.Types.VARCHAR);
        SQL_TYPES.put("show_brand", java.sql.Types.VARCHAR);
        SQL_TYPES.put("show_date", java.sql.Types.VARCHAR);
        SQL_TYPES.put("fs_date", java.sql.Types.DATE);
        SQL_TYPES.put("hs_date", java.sql.Types.DATE);
        SQL_TYPES.put("store_id", java.sql.Types.VARCHAR);
        SQL_TYPES.put("store_name", java.sql.Types.VARCHAR);
        SQL_TYPES.put("adno", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id1", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id2", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id3", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id4", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id5", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id6", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id7", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_id8", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name1", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name2", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name3", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name4", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name5", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name6", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name7", java.sql.Types.VARCHAR);
        SQL_TYPES.put("dept_name8", java.sql.Types.VARCHAR);
        SQL_TYPES.put("depart_name", java.sql.Types.VARCHAR);
        SQL_TYPES.put("ccode", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id1", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id2", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id3", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id4", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id5", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id6", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id7", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_id8", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name1", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name2", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name3", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name4", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name5", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name6", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name7", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name8", java.sql.Types.VARCHAR);
        SQL_TYPES.put("cat_name", java.sql.Types.VARCHAR);
        SQL_TYPES.put("gds_bcode", java.sql.Types.VARCHAR);
        SQL_TYPES.put("sale_qty", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_qty_fpl", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_cost", java.sql.Types.NUMERIC);
        SQL_TYPES.put("aet_cost", java.sql.Types.NUMERIC);
        SQL_TYPES.put("disc_value", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_disc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_mem", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_mem", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_mem_rate_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_revenue_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_cost_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("aet_cost_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pro_rate_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pro_rate_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("pay_free", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_cost_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("aet_cost_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("disc_value_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_disc_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_mem_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_mem_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_mem_rate_dq_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_revenue_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("at_cost_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("aet_cost_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pro_rate_dq_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pro_rate_dq_raw", java.sql.Types.NUMERIC);
        SQL_TYPES.put("pay_free_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_qty_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_dbc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_dbc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pm", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pm", java.sql.Types.NUMERIC);
        SQL_TYPES.put("count_trade", java.sql.Types.NUMERIC);
        SQL_TYPES.put("count_trade_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("count_mem", java.sql.Types.NUMERIC);
        SQL_TYPES.put("count_mem_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("plan_sale", java.sql.Types.NUMERIC);
        SQL_TYPES.put("plan_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_qty", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_qty_fpl", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_wsale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_revenue", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_cost", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_aet_cost", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_disc_value", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_disc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_wsale_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_profit", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_mem", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_mem", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_mem_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_revenue_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_cost_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_aet_cost_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_profit_pro", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_pay_free", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_dbc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_wsale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_revenue_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_cost_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_aet_cost_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_disc_value_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_disc_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_wsale_profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_profit_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_mem_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_mem_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_mem_rate_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_sale_revenue_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_at_cost_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_aet_cost_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_net_profit_pro_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_pro_rate_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_pro_rate_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_pay_free_dq", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_dbc", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_sale_revenue_pm", java.sql.Types.NUMERIC);
        SQL_TYPES.put("acc_profit_pm", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_amount", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_amount", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_revenue_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("wsale_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_mem_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("cust_price", java.sql.Types.NUMERIC);
        SQL_TYPES.put("mem_cust_price", java.sql.Types.NUMERIC);
        SQL_TYPES.put("mem_trade_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("plan_sale_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("plan_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("sale_revenue_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("brand_name", java.sql.Types.NVARCHAR);
        SQL_TYPES.put("dq_net_sale_amount", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_net_profit_amount", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_wsale_revenue_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_wsale_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_sale_mem_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_sale_revenue_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_profit_pro_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_net_profit_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_cust_price", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_mem_cust_price", java.sql.Types.NUMERIC);
        SQL_TYPES.put("dq_mem_trade_rate", java.sql.Types.NUMERIC);
        SQL_TYPES.put("profit_rate_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_sale_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("net_profit_rate_growth", java.sql.Types.NUMERIC);
        SQL_TYPES.put("stock_amount", java.sql.Types.NUMERIC);
        SQL_TYPES.put("ext_01", java.sql.Types.NUMERIC);
        SQL_TYPES.put("ext_02", java.sql.Types.NUMERIC);
        SQL_TYPES.put("ext_03", java.sql.Types.NUMERIC);
        SQL_TYPES.put("ext_04", java.sql.Types.NVARCHAR);
        SQL_TYPES.put("ext_05", java.sql.Types.NVARCHAR);
        SQL_TYPES.put("ext_06", java.sql.Types.NVARCHAR);
        SQL_TYPES.put("ext_07", java.sql.Types.NVARCHAR);
        SQL_TYPES.put("ext_08", java.sql.Types.DATE);
        SQL_TYPES.put("ext_09", java.sql.Types.VARCHAR);
        SQL_TYPES.put("ext_10", java.sql.Types.VARCHAR);
    }
}
