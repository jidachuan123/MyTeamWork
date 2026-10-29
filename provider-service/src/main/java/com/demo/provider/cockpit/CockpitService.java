package com.demo.provider.cockpit;

import com.demo.provider.cockpit.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 经营驾驶舱（3004）数据服务 —— 2026-10-29 新建，目前返回示例数据。
 *
 * ============================ 下一步怎么接真实数据 ============================
 * 五个区块各自独立，可以「一块一块」替换，互不影响（页面按块渲染）：
 *
 *  ① trend7d（近 7 天趋势）
 *     按日期分组汇总销售流水，取最近 7 天；去年同期用去年同日期再跑一次。
 *     建议数据源：销售流水事实表（与 SD1/SD2 同源），日期用有索引的日期列过滤。
 *
 *  ② groupYesterday / monthToDate / yearToDate
 *     同一份汇总的三种时间窗：昨日一天、本月 1 号至今、本年 1 月 1 号至今；
 *     环比 = 前天 / 上月同期，同比 = 去年同期。金额单位统一换成万元。
 *
 *  ③ todayRealtime + bizTypes
 *     今日 T+0 累计（含当日未日结数据），按业态分组；注意与「日结口径」可能有差异，
 *     页面上标注了「T+0 口径」，不要和昨日数据混着解释。
 *
 *  ④ bizCards（11 张卡片）
 *     按「板块/业态」聚合：5 个购物中心 + 6 个业态汇总。
 *     板块与机构的对应关系建议放 application-cockpit.yml，避免写死在 SQL 里。
 *
 *  ⑤ stores（门店总览）
 *     按门店分组：昨日销售、同比、环比、毛利率、客单价、来客数。
 *
 *  指标口径务必与既有报表（SD1/SD2/SD3）对齐后再上线，否则会出现
 *  「驾驶舱和明细报表对不上」这类难以解释的问题。
 * ==========================================================================
 */
@Service
public class CockpitService {

    private static final Logger log = LoggerFactory.getLogger(CockpitService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 整屏数据。
     * TODO 接真实数据时，把下面各段替换为对应查询即可（VO 结构保持不变）。
     */
    public CockpitOverviewVO buildOverview() {
        CockpitOverviewVO vo = new CockpitOverviewVO();
        vo.setAsOf(LocalDateTime.now().format(DTF));
        vo.setDemo(true);
        vo.setTodayRealtime(realtime());
        vo.setGroupYesterday(KpiVO.of("1862.34").withDod("-0.0432").withYoy("0.0871"));
        vo.setMonthToDate(KpiVO.of("37072.30").withMom("0.0312").withYoy("0.0654"));
        vo.setYearToDate(KpiVO.of("444923.00").withYoy("0.0412"));
        vo.setTrend7d(trend7d());
        vo.setBizTypes(bizTypes());
        vo.setBizCards(bizCards());
        vo.setStores(stores());
        log.debug("[驾驶舱] 返回示例数据（待接入真实取数）");
        return vo;
    }

    // ------------------------------------------------------------------ ③ 今日实时
    private RealtimeVO realtime() {
        RealtimeVO rt = new RealtimeVO();
        rt.setAmount(new BigDecimal("428.66"));
        rt.setAsOfTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        rt.setVsYesterday(new BigDecimal("0.0682"));
        return rt;
    }

    // ------------------------------------------------------------------ ① 近 7 天趋势
    private List<TrendPointVO> trend7d() {
        List<TrendPointVO> list = new ArrayList<>();
        list.add(new TrendPointVO("10-22", "1720.40", "1630.20"));
        list.add(new TrendPointVO("10-23", "1798.20", "1690.50"));
        list.add(new TrendPointVO("10-24", "2105.60", "1980.30"));
        list.add(new TrendPointVO("10-25", "2380.90", "2150.70"));
        list.add(new TrendPointVO("10-26", "1960.30", "1880.40"));
        list.add(new TrendPointVO("10-27", "1946.20", "1820.60"));
        list.add(new TrendPointVO("10-28", "1862.30", "1712.80"));
        return list;
    }

    // ------------------------------------------------------------------ ③ 各业态
    private List<BizTypeVO> bizTypes() {
        List<BizTypeVO> list = new ArrayList<>();
        list.add(new BizTypeVO("商管业态", "1282.20"));
        list.add(new BizTypeVO("超市业态", "344.90"));
        list.add(new BizTypeVO("便利店业态", "73.60"));
        list.add(new BizTypeVO("佳和餐饮", "68.20"));
        list.add(new BizTypeVO("佳和畅通", "41.30"));
        list.add(new BizTypeVO("佳和酒店", "32.60"));
        list.add(new BizTypeVO("其他自营业态", "19.54"));
        return list;
    }

    // ------------------------------------------------------------------ ④ 业态卡片
    private List<BizCardVO> bizCards() {
        List<BizCardVO> list = new ArrayList<>();
        list.add(card("海湾佳和诚", "购物中心", "428.60", "0.0318", "0.1084", "8520.60", "103240.50"));
        list.add(card("海湾佳和里", "购物中心", "336.20", "-0.0138", "0.0716", "6680.20", "81240.80"));
        list.add(card("巨野中心店", "购物中心", "168.40", "0.0214", "0.0562", "3382.40", "40128.60"));
        list.add(card("巨野天悦店", "购物中心", "196.70", "0.0456", "0.1338", "3925.80", "46210.30"));
        list.add(card("单县佳和汇", "购物中心", "152.30", "-0.0524", "-0.0312", "3060.50", "37105.20"));
        list.add(card("佳和超市", "样板店", "344.90", "0.0128", "0.0412", "6820.40", "81050.60"));
        list.add(card("佳和超市", "便利店", "73.60", "0.0196", "0.0742", "1462.80", "17428.90"));
        list.add(card("佳和餐饮", "餐饮", "68.20", "0.0532", "0.1186", "1358.40", "16204.50"));
        list.add(card("佳和畅通", "物流", "41.30", "0.0086", "0.0324", "820.60", "9850.20"));
        list.add(card("佳和酒店", "酒店", "32.60", "-0.0142", "-0.0248", "652.40", "7812.60"));
        list.add(card("其他自营业态", "其他", "19.54", "0.0042", "0.0156", "388.20", "4650.80"));
        return list;
    }

    private BizCardVO card(String name, String type, String yesterday, String dod, String yoy,
                           String monthAcc, String yearAcc) {
        BizCardVO c = new BizCardVO();
        c.setName(name);
        c.setType(type);
        c.setYesterday(new BigDecimal(yesterday));
        c.setDod(new BigDecimal(dod));
        c.setYoy(new BigDecimal(yoy));
        c.setMonthAcc(new BigDecimal(monthAcc));
        c.setYearAcc(new BigDecimal(yearAcc));
        return c;
    }

    // ------------------------------------------------------------------ ⑤ 门店总览
    private List<StoreRowVO> stores() {
        List<StoreRowVO> list = new ArrayList<>();
        list.add(store(1, "海湾佳和诚购物中心", "菏泽市区", "428.60", "0.1084", "0.0318", "0.2135", "86.50", 4954));
        list.add(store(2, "海湾佳和里购物中心", "菏泽市区", "336.20", "0.0716", "-0.0138", "0.2051", "79.20", 4245));
        list.add(store(3, "巨野中心店", "巨野", "168.40", "0.0562", "0.0214", "0.1984", "62.40", 2699));
        list.add(store(4, "巨野天悦城店", "巨野", "196.70", "0.1338", "0.0456", "0.2213", "65.80", 2989));
        list.add(store(5, "单县佳和汇", "单县", "152.30", "-0.0312", "-0.0524", "0.1872", "58.90", 2586));
        list.add(store(6, "佳和超市样板店", "菏泽市区", "218.40", "0.0412", "0.0128", "0.1763", "42.60", 5127));
        list.add(store(7, "佳和超市人民路店", "菏泽市区", "62.10", "0.0226", "-0.0084", "0.1685", "39.80", 1560));
        list.add(store(8, "佳和超市牡丹路店", "菏泽市区", "48.60", "0.0674", "0.0212", "0.1702", "41.20", 1179));
        list.add(store(9, "佳和超市长江路店", "菏泽市区", "15.80", "-0.0842", "-0.0316", "0.1621", "37.50", 421));
        list.add(store(10, "佳和便利店001", "菏泽市区", "43.20", "0.0956", "0.0284", "0.2245", "28.60", 1510));
        list.add(store(11, "佳和便利店002", "菏泽市区", "30.40", "0.0428", "-0.0112", "0.2187", "26.40", 1152));
        list.add(store(12, "佳和餐饮中心店", "菏泽市区", "68.20", "0.1186", "0.0532", "0.5842", "126.80", 538));
        list.add(store(13, "佳和畅通", "菏泽市区", "41.30", "0.0324", "0.0086", "0.1425", "118.40", 349));
        list.add(store(14, "佳和酒店", "菏泽市区", "32.60", "-0.0248", "-0.0142", "0.6128", "428.50", 76));
        list.add(store(15, "其他自营", "菏泽市区", "19.54", "0.0156", "0.0042", "0.2341", "46.20", 423));
        return list;
    }

    private StoreRowVO store(int seq, String name, String region, String yesterday,
                             String yoy, String mom, String grossRate, String avgTicket, int customers) {
        StoreRowVO r = new StoreRowVO();
        r.setSeq(seq);
        r.setName(name);
        r.setRegion(region);
        r.setYesterday(new BigDecimal(yesterday));
        r.setYoy(new BigDecimal(yoy));
        r.setMom(new BigDecimal(mom));
        r.setGrossRate(new BigDecimal(grossRate).setScale(4, RoundingMode.HALF_UP));
        r.setAvgTicket(new BigDecimal(avgTicket));
        r.setCustomers(customers);
        return r;
    }
}
