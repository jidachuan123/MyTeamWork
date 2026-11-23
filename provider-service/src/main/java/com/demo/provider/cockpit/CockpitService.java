package com.demo.provider.cockpit;

import com.demo.provider.cockpit.vo.CockpitOverviewVO;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.Card;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.Foot;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.Kpi;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.Rank;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.Rate;
import com.demo.provider.cockpit.vo.CockpitOverviewVO.TrendPoint;
import com.demo.provider.cockpit.vo.StoreDetailVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 集团驾驶舱（3004 大屏）数据服务 —— 2026-11-23 接入 192.168.0.120 / RDS_SC。
 *
 * 数据源：cockpitJdbcTemplate（只读账号 readonly_jdc，见 application-cockpit.yml）。
 * 取数：cookpit/overview.sql（昨日集团经营总览）+ cockpit/trend.sql（近 7 日趋势）。
 *
 * 口径约定：
 *   - 业务日期固定为「今天的前一天」——tbs_rd_store 是 T+1 汇总表，当天数据次日才生成；
 *     调试时可通过接口 ?date=yyyy-MM-dd 覆盖。
 *   - 租户固定 8（application-cockpit.yml cockpit.tenant-id）。
 *   - 金额全部换算成万元，比率统一小数（0.0871 = 8.71%）。
 *   - 只做 SELECT，不写任何数据。
 *
 * 页面结构见 com.demo.provider.cockpit.vo.CockpitOverviewVO；
 * 前端 MyVue/src/cockpit 会校验 kpis/bizCards/ranking/trend7d 四块形状，
 * 形状不符或接口报错时自动退回本地示例数据（避免大屏白屏）。
 */
@Service
public class CockpitService {

    private static final Logger log = LoggerFactory.getLogger(CockpitService.class);

    private static final DateTimeFormatter AS_OF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    /** 业态卡配色（与前端 BizCard 的 tone 一一对应） */
    private static final String[] CARD_TONES = {
            "orange", "cyan", "green", "gold", "pink", "purple", "blue", "teal"
    };

    /** 右侧排行配色 */
    private static final String[] RANK_COLORS = {
            "#ff8a4c", "#31e6a8", "#3fe3ff", "#ffcf5c", "#ff6ea9", "#a06bff", "#2f7dff", "#2ee6d6"
    };

    /**
     * 卡片 / 排行固定顺序（2026-11-23 按业务要求，不按金额排序）：
     *   佳和城 → 中心店 → 佳和里 → 天悦百货 → 百货 → 超市 → 便利店 → IP店
     * 前 4 个是购物中心门店（c_group_id = 门店编码），后 4 个是业态（c_group_id = c_class）。
     */
    private static final List<String> FIXED_ORDER = List.of(
            "1303001",  // 菏泽佳和城店百货
            "1301001",  // 巨野中心店百货
            "1303021",  // 菏泽佳和里店百货
            "1301002",  // 巨野天悦店百货
            "10",       // 百货
            "21",       // 超市
            "22",       // 便利店
            "40"        // IP店
    );

    private final JdbcTemplate cockpitJdbc;
    private final int tenantId;
    private final String brandLevel;
    private final String overviewSqlTemplate;
    private final String trendSqlTemplate;
    private final String storeDetailSqlTemplate;
    private final String categoryDetailSqlTemplate;
    private final String storeTrendSqlTemplate;
    private final String classDetailSqlTemplate;
    private final String classStoreListSqlTemplate;

    public CockpitService(@Qualifier("cockpitJdbcTemplate") JdbcTemplate cockpitJdbc,
                          @Value("${cockpit.tenant-id:8}") int tenantId,
                          @Value("${cockpit.brand-level:3}") String brandLevel) throws IOException {
        this.cockpitJdbc = cockpitJdbc;
        this.tenantId = tenantId;
        this.brandLevel = brandLevel;
        this.overviewSqlTemplate = loadSql("cockpit/overview.sql");
        this.trendSqlTemplate = loadSql("cockpit/trend.sql");
        this.storeDetailSqlTemplate = loadSql("cockpit/store-detail.sql");
        this.categoryDetailSqlTemplate = loadSql("cockpit/category-detail.sql");
        this.storeTrendSqlTemplate = loadSql("cockpit/store-trend.sql");
        this.classDetailSqlTemplate = loadSql("cockpit/class-detail.sql");
        this.classStoreListSqlTemplate = loadSql("cockpit/class-store-list.sql");
    }

    /**
     * 整屏数据。
     *
     * @param date 业务日期；null = 今天的前一天（T+1 口径）
     */
    public CockpitOverviewVO buildOverview(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now().minusDays(1);
        String dateStr = d.toString();

        List<GroupRow> rows;
        try {
            String sql = bind(overviewSqlTemplate, dateStr);
            rows = cockpitJdbc.query(sql, CockpitService::mapRow);
        } catch (DataAccessException e) {
            log.error("[驾驶舱] 取数失败，date={}：{}", dateStr, e.getMessage(), e);
            throw new IllegalStateException("驾驶舱取数失败：" + e.getMessage(), e);
        }
        sortByFixedOrder(rows);

        CockpitOverviewVO vo = new CockpitOverviewVO();
        vo.setAsOf(LocalDateTime.now().format(AS_OF));
        vo.setDemo(false);
        vo.setKpis(buildKpis(rows));
        vo.setBizCards(buildCards(rows));
        vo.setRanking(buildRanking(rows));
        vo.setTrend7d(buildTrend(dateStr));
        return vo;
    }

    // ------------------------------------------------------------------ 顶部 KPI

    private List<Kpi> buildKpis(List<GroupRow> rows) {
        BigDecimal day = sum(rows, r -> r.day);
        BigDecimal dayPrev = sum(rows, r -> r.dayPrev);
        BigDecimal dayYear = sum(rows, r -> r.dayYear);
        BigDecimal month = sum(rows, r -> r.month);
        BigDecimal monthPrev = sum(rows, r -> r.monthPrev);
        BigDecimal monthYear = sum(rows, r -> r.monthYear);
        BigDecimal year = sum(rows, r -> r.year);
        BigDecimal yearPrev = sum(rows, r -> r.yearPrev);

        BigDecimal dayChain = rate(day, dayPrev);
        BigDecimal dayYoy = rate(day, dayYear);
        BigDecimal monthChain = rate(month, monthPrev);
        BigDecimal monthYoy = rate(month, monthYear);
        BigDecimal yearYoy = rate(year, yearPrev);

        List<Kpi> list = new ArrayList<>();

        // ① 昨日集团销售
        Kpi yesterday = new Kpi();
        yesterday.setKey("yesterday");
        yesterday.setLabel("昨日集团销售（万元）");
        yesterday.setAmount(wan(day));
        yesterday.setUnit("万元");
        yesterday.setTone("white");
        yesterday.setRates(rateList(rate("日环比", dayChain), rate("年同比", dayYoy)));
        yesterday.setFoot(footList(
                new Foot("月累计", wan(month), monthChain),
                new Foot("年累计", wan(year), yearYoy)));
        list.add(yesterday);

        // ② 本月累计
        Kpi monthKpi = new Kpi();
        monthKpi.setKey("month");
        monthKpi.setLabel("本月累计销售");
        monthKpi.setAmount(wan(month));
        monthKpi.setUnit("万元");
        monthKpi.setTone("cyan");
        monthKpi.setRates(rateList(rate("月环比", monthChain), rate("年同比", monthYoy)));
        list.add(monthKpi);

        // ③ 本年累计
        Kpi yearKpi = new Kpi();
        yearKpi.setKey("year");
        yearKpi.setLabel("本年累计销售");
        yearKpi.setAmount(wan(year));
        yearKpi.setUnit("万元");
        yearKpi.setTone("green");
        yearKpi.setRates(rateList(rate("年同比", yearYoy)));
        list.add(yearKpi);

        return list;
    }

    // ------------------------------------------------------------------ 业态卡

    private List<Card> buildCards(List<GroupRow> rows) {
        List<Card> list = new ArrayList<>();
        int i = 0;
        for (GroupRow r : rows) {
            Card c = new Card();
            c.setKey((r.groupType == 1 ? "mall-" : "biz-") + r.groupId);
            c.setName(r.groupName);
            // 购物中心卡带 storeId；业态卡带 classCode（= c_group_id），两类都可下钻
            c.setStoreId(r.groupType == 1 ? r.groupId : null);
            c.setClassCode(r.groupType == 2 ? r.groupId : null);
            c.setTag(r.groupType == 1
                    ? (isBlank(r.typeName) ? "购物中心" : r.typeName)
                    : "业态");
            c.setTone(CARD_TONES[i % CARD_TONES.length]);
            c.setAmount(wan(r.day));
            c.setDod(r.dayChain);
            c.setYoy(r.dayYearRate);
            c.setShare(r.dayRate);
            c.setExtraLabel("门店数");
            c.setExtraValue(r.storeCount + " 家");
            c.setMonthAcc(wan(r.month));
            c.setYearAcc(wan(r.year));
            list.add(c);
            i++;
        }
        return list;
    }

    // ------------------------------------------------------------------ 业态排行

    /**
     * 右侧「各业态销售」：展示全部 8 项（4 购物中心 + 4 业态），
     * 顺序与卡片 FIXED_ORDER 完全一致（2026-11-23 业务要求，不按金额重排）。
     */
    private List<Rank> buildRanking(List<GroupRow> rows) {
        List<Rank> list = new ArrayList<>();
        int seq = 1;
        for (GroupRow r : rows) {
            if (!FIXED_ORDER.contains(r.groupId)) {
                continue;
            }
            list.add(new Rank(seq, r.groupName, wan(r.day), RANK_COLORS[(seq - 1) % RANK_COLORS.length]));
            seq++;
        }
        return list;
    }

    // ------------------------------------------------------------------ 近 7 日趋势

    private List<TrendPoint> buildTrend(String dateStr) {
        try {
            String sql = bind(trendSqlTemplate, dateStr);
            return cockpitJdbc.query(sql, (rs, i) ->
                    new TrendPoint(rs.getString("c_date"), wan(rs.getBigDecimal("c_sale"))));
        } catch (DataAccessException e) {
            log.error("[驾驶舱] 趋势取数失败，date={}：{}", dateStr, e.getMessage(), e);
            throw new IllegalStateException("驾驶舱趋势取数失败：" + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 门店下钻

    /**
     * 点击购物中心卡片后的下钻数据：
     *   summary    —— store-detail.sql，门店（含成员机构）的昨日/本月/本年汇总
     *   categories —— category-detail.sql，三级品类明细
     *
     * @param storeId   门店/机构编码（只允许数字和逗号，防止 SQL 注入）
     * @param storeName 展示用门店名（前端传入，仅回显）
     * @param date      业务日期；null = 今天的前一天
     */
    public StoreDetailVO buildStoreDetail(String storeId, String storeName, LocalDate date) {
        if (storeId == null || !storeId.matches("^[0-9]+(,[0-9]+)*$")) {
            throw new IllegalArgumentException("门店编码非法：" + storeId);
        }
        LocalDate d = date != null ? date : LocalDate.now().minusDays(1);
        String dateStr = d.toString();

        StoreDetailVO vo = new StoreDetailVO();
        vo.setDate(dateStr);
        vo.setType("store");
        vo.setStoreId(storeId);
        vo.setStoreName(storeName);

        try {
            String summarySql = storeDetailSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeId}", storeId)
                    .replace("${date}", dateStr);
            vo.setSummary(cockpitJdbc.queryForObject(summarySql, (rs, i) -> {
                StoreDetailVO.Summary s = new StoreDetailVO.Summary();
                BigDecimal day = rs.getBigDecimal("day_sale");
                BigDecimal dayPrev = rs.getBigDecimal("day_prev_sale");
                BigDecimal dayYear = rs.getBigDecimal("day_year_sale");
                BigDecimal month = rs.getBigDecimal("month_sale");
                BigDecimal monthPrev = rs.getBigDecimal("month_prev_sale");
                BigDecimal monthYear = rs.getBigDecimal("month_year_sale");
                BigDecimal year = rs.getBigDecimal("year_sale");
                BigDecimal yearPrev = rs.getBigDecimal("year_prev_sale");
                s.setDaySale(wan(day));
                s.setDayChain(rate(day, dayPrev));
                s.setDayYear(rate(day, dayYear));
                s.setMonthSale(wan(month));
                s.setMonthChain(rate(month, monthPrev));
                s.setMonthYear(rate(month, monthYear));
                s.setYearSale(wan(year));
                s.setYearYear(rate(year, yearPrev));
                return s;
            }));

            String categorySql = categoryDetailSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeId}", storeId)
                    .replace("${brandLevel}", brandLevel)
                    .replace("${date}", dateStr);
            vo.setCategories(cockpitJdbc.query(categorySql, (rs, i) -> {
                StoreDetailVO.Category c = new StoreDetailVO.Category();
                c.setCcode(rs.getString("c_ccode"));
                c.setName(rs.getString("c_ccode_name"));
                c.setDaySale(wan(rs.getBigDecimal("day_sale")));
                c.setMonthSale(wan(rs.getBigDecimal("month_sale")));
                c.setYearSale(wan(rs.getBigDecimal("year_sale")));
                c.setDayChain(rs.getBigDecimal("day_chain_rate"));
                c.setDayYear(rs.getBigDecimal("day_year_rate"));
                c.setMonthChain(rs.getBigDecimal("month_chain_rate"));
                c.setMonthYear(rs.getBigDecimal("month_year_rate"));
                c.setYearYear(rs.getBigDecimal("year_year_rate"));
                c.setShare(rs.getBigDecimal("sale_rate"));
                return c;
            }));

            String trendSql = storeTrendSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeId}", storeId)
                    .replace("${storeClass}", "")
                    .replace("${date}", dateStr);
            vo.setTrend7d(cockpitJdbc.query(trendSql, (rs, i) ->
                    new StoreDetailVO.Trend(rs.getString("c_date"), wan(rs.getBigDecimal("c_sale")))));
        } catch (DataAccessException e) {
            log.error("[驾驶舱] 门店下钻取数失败，storeId={}，date={}：{}", storeId, dateStr, e.getMessage(), e);
            throw new IllegalStateException("门店下钻取数失败：" + e.getMessage(), e);
        }
        return vo;
    }

    /**
     * 点击业态卡片后的下钻数据（@c_class = 卡片 c_group_id）：
     *   summary   —— class-detail.sql，业态汇总（门店数 / 昨日 / 本月 / 本年 + 比率）
     *   trend7d   —— store-trend.sql，按 c_class 圈定门店的近 7 日销售
     *   stores    —— class-store-list.sql，该业态下的门店列表（含占比）
     *
     * @param storeClass 业态编码（c_class）
     * @param className  展示用业态名（前端传入，仅回显）
     * @param date       业务日期；null = 今天的前一天
     */
    public StoreDetailVO buildClassDetail(String storeClass, String className, LocalDate date) {
        if (storeClass == null || !storeClass.matches("^[0-9A-Za-z]+$")) {
            throw new IllegalArgumentException("业态编码非法：" + storeClass);
        }
        LocalDate d = date != null ? date : LocalDate.now().minusDays(1);
        String dateStr = d.toString();

        StoreDetailVO vo = new StoreDetailVO();
        vo.setDate(dateStr);
        vo.setType("class");
        vo.setStoreId(storeClass);
        vo.setStoreName(className);

        try {
            String summarySql = classDetailSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeClass}", storeClass)
                    .replace("${date}", dateStr);
            vo.setSummary(cockpitJdbc.queryForObject(summarySql, (rs, i) -> {
                StoreDetailVO.Summary s = new StoreDetailVO.Summary();
                s.setClassName(rs.getString("c_class_name"));
                s.setStoreCount(rs.getInt("c_store_count"));
                // 业态汇总 SQL 已直接返回比率列，无需再算
                s.setDaySale(wan(rs.getBigDecimal("day_sale")));
                s.setDayChain(rs.getBigDecimal("day_chain_rate"));
                s.setDayYear(rs.getBigDecimal("day_year_rate"));
                s.setMonthSale(wan(rs.getBigDecimal("month_sale")));
                s.setMonthChain(rs.getBigDecimal("month_chain_rate"));
                s.setMonthYear(rs.getBigDecimal("month_year_rate"));
                s.setYearSale(wan(rs.getBigDecimal("year_sale")));
                s.setYearYear(rs.getBigDecimal("year_year_rate"));
                return s;
            }));
            // 卡片名优先，数据库名兜底
            if (isBlank(vo.getStoreName()) && vo.getSummary() != null) {
                vo.setStoreName(vo.getSummary().getClassName());
            }

            String trendSql = storeTrendSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeId}", "")
                    .replace("${storeClass}", storeClass)
                    .replace("${date}", dateStr);
            vo.setTrend7d(cockpitJdbc.query(trendSql, (rs, i) ->
                    new StoreDetailVO.Trend(rs.getString("c_date"), wan(rs.getBigDecimal("c_sale")))));

            String listSql = classStoreListSqlTemplate
                    .replace("${tenantId}", String.valueOf(tenantId))
                    .replace("${storeClass}", storeClass)
                    .replace("${entityStoreId}", "")
                    .replace("${date}", dateStr);
            vo.setStores(cockpitJdbc.query(listSql, (rs, i) -> {
                StoreDetailVO.StoreRow r = new StoreDetailVO.StoreRow();
                r.setStoreId(rs.getString("c_store_id"));
                r.setName(rs.getString("c_store_name"));
                r.setDaySale(wan(rs.getBigDecimal("day_sale")));
                r.setMonthSale(wan(rs.getBigDecimal("month_sale")));
                r.setYearSale(wan(rs.getBigDecimal("year_sale")));
                r.setDayChain(rs.getBigDecimal("day_chain_rate"));
                r.setDayYear(rs.getBigDecimal("day_year_rate"));
                r.setMonthChain(rs.getBigDecimal("month_chain_rate"));
                r.setMonthYear(rs.getBigDecimal("month_year_rate"));
                r.setYearYear(rs.getBigDecimal("year_year_rate"));
                r.setShare(rs.getBigDecimal("day_sale_rate"));
                return r;
            }));
        } catch (DataAccessException e) {
            log.error("[驾驶舱] 业态下钻取数失败，class={}，date={}：{}", storeClass, dateStr, e.getMessage(), e);
            throw new IllegalStateException("业态下钻取数失败：" + e.getMessage(), e);
        }
        return vo;
    }

    // ------------------------------------------------------------------ 行映射

    private static GroupRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        GroupRow r = new GroupRow();
        r.groupType = rs.getInt("c_group_type");
        r.typeName = rs.getString("c_group_type_name");
        r.groupId = rs.getString("c_group_id");
        r.groupName = rs.getString("c_group_name");
        r.sort = toInt(rs.getString("c_group_sort"));
        r.storeCount = rs.getInt("c_store_count");
        r.day = rs.getBigDecimal("day_sale");
        r.dayPrev = rs.getBigDecimal("day_prev_sale");
        r.dayYear = rs.getBigDecimal("day_year_sale");
        r.month = rs.getBigDecimal("month_sale");
        r.monthPrev = rs.getBigDecimal("month_prev_sale");
        r.monthYear = rs.getBigDecimal("month_year_sale");
        r.year = rs.getBigDecimal("year_sale");
        r.yearPrev = rs.getBigDecimal("year_prev_sale");
        r.dayRate = rs.getBigDecimal("day_sale_rate");
        r.dayChain = rs.getBigDecimal("day_chain_rate");
        r.dayYearRate = rs.getBigDecimal("day_year_rate");
        r.monthChain = rs.getBigDecimal("month_chain_rate");
        r.monthYearRate = rs.getBigDecimal("month_year_rate");
        r.yearYearRate = rs.getBigDecimal("year_year_rate");
        return r;
    }

    // ------------------------------------------------------------------ 工具方法

    /** 按 FIXED_ORDER 固定排序；不在清单里的行排到最后，且保持原有相对顺序（稳定排序） */
    private static void sortByFixedOrder(List<GroupRow> rows) {
        rows.sort(Comparator.comparingInt(r -> {
            int idx = FIXED_ORDER.indexOf(r.groupId);
            return idx < 0 ? Integer.MAX_VALUE : idx;
        }));
    }

    /** 替换 SQL 模板参数（tenantId / date 均由服务端控制，非用户输入） */
    private String bind(String template, String dateStr) {
        return template
                .replace("${tenantId}", String.valueOf(tenantId))
                .replace("${date}", dateStr);
    }

    private static String loadSql(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    private static BigDecimal sum(List<GroupRow> rows, java.util.function.Function<GroupRow, BigDecimal> f) {
        BigDecimal total = BigDecimal.ZERO;
        for (GroupRow r : rows) {
            BigDecimal v = f.apply(r);
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }

    /** 元 → 万元，保留 2 位 */
    private static BigDecimal wan(BigDecimal yuan) {
        if (yuan == null) {
            return BigDecimal.ZERO;
        }
        return yuan.divide(TEN_THOUSAND, 2, RoundingMode.HALF_UP);
    }

    /** (cur - base) / |base|，保留 4 位；base 为 0 或无值时返回 null */
    private static BigDecimal rate(BigDecimal cur, BigDecimal base) {
        if (cur == null || base == null || base.signum() == 0) {
            return null;
        }
        return cur.subtract(base).divide(base.abs(), 4, RoundingMode.HALF_UP);
    }

    private static Rate rate(String label, BigDecimal value) {
        return new Rate(label, value);
    }

    private static List<Rate> rateList(Rate... rates) {
        List<Rate> list = new ArrayList<>();
        for (Rate r : rates) {
            // 算不出来（去年同期无数据）的比率不下发，避免前端把 null 显示成 0.00%
            if (r != null && r.getValue() != null) {
                list.add(r);
            }
        }
        return list;
    }

    private static List<Foot> footList(Foot... feet) {
        List<Foot> list = new ArrayList<>();
        for (Foot f : feet) {
            if (f != null) {
                list.add(f);
            }
        }
        return list;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int toInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** overview.sql 的一行（购物中心门店 或 业态） */
    private static final class GroupRow {
        int groupType;
        String typeName;
        String groupId;
        String groupName;
        int sort;
        int storeCount;
        BigDecimal day;
        BigDecimal dayPrev;
        BigDecimal dayYear;
        BigDecimal month;
        BigDecimal monthPrev;
        BigDecimal monthYear;
        BigDecimal year;
        BigDecimal yearPrev;
        BigDecimal dayRate;
        BigDecimal dayChain;
        BigDecimal dayYearRate;
        BigDecimal monthChain;
        BigDecimal monthYearRate;
        BigDecimal yearYearRate;
    }
}
