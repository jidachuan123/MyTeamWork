package com.demo.provider.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 销售详情3（SD3）取数与组装服务 —— 2026-10-15 新建。
 *
 * ★ 数据源：MySQL(bcm)，与 SD1/SD2 的 SQL Server 完全独立。
 * ★ 口径统一：页面查询和定时截图<strong>共用本类的同一套聚合逻辑</strong>，
 *   保证「截图 = 页面」的红线（订单数、客单价只在后端算一次，两边取同一个字段）。
 *
 * 表格结构（7 列）：序号 / 门店编码 / 门店名称 / 平台(渠道) / 销售额 / 订单数 / 客单价
 *   - 每个门店一个「块」：序号 / 门店编码 / 门店名称 三列纵向合并，覆盖明细行 + 合计行（合计行左侧直达最左边）
 *   - 一个门店下存在多个渠道 → 该门店底部追加一行「合计」；单渠道不追加
 *   - 全表最后一行「总计」：左侧 4 列（序号/编码/名称/平台）合并成一个大格，文案 = sd3.total-label
 *
 * ★ 2026-10-16 新增「渠道销售额补差」：分组（门店+渠道）最终销售额 = 求和销售额 + 补差
 *     淘宝闪购   → + 该组「包装费」列求和（SQL 输出包装费列）
 *     美团闪购等 → + 该组订单数 × 每单补差值（sd3.chnl-per-order-fee，默认 1 元/单）
 *   补差在分组层叠加；合计行 / 总计行 / 客单价均基于补差后的销售额，页面与截图天然一致。
 */
@Service
public class SalesDetail3Service {

    private static final Logger log = LoggerFactory.getLogger(SalesDetail3Service.class);

    /** 淘宝闪购：补差 = 该组「包装费」列求和（固定逻辑；渠道名与 chnl_name 完全一致） */
    private static final String PACKAGE_FEE_CHNL = "淘宝闪购";

    @Autowired
    @Qualifier("sd3JdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private Sd3Properties props;

    // ==================== 对外：查询（供页面 + 截图共用）====================

    /**
     * 按条件查询并组装成表格行（含每个门店的合计行 + 末尾总计行）。
     *
     * @param startDate 开始日期 yyyy-MM-dd
     * @param endDate   结束日期 yyyy-MM-dd
     * @param orgCode   机构：门店编码，支持逗号分隔多个（精确匹配，空=不筛选）；如 1103011,1103021
     * @param chnl      渠道名称，空=全部渠道
     * @param orderNo   订单号，空=不筛选
     */
    public Sd3Result query(String startDate, String endDate, String orgCode, String chnl, String orderNo) {
        List<Map<String, Object>> detail = queryDetail(startDate, endDate, orgCode, chnl, orderNo);
        return buildResult(detail);
    }

    // ==================== 取数 ====================

    /**
     * 执行 SQL 取明细。
     *
     * ★ 关键点：SQL 里用的是 MySQL 会话变量（@开始日期 等），必须在<strong>同一个 Connection</strong>
     *   上先 SET 再查询，否则连接池换连接后变量丢失。因此这里用 JdbcTemplate.execute(ConnectionCallback)，
     *   保证整段逻辑复用同一连接。
     */
    public List<Map<String, Object>> queryDetail(String startDate, String endDate,
                                                 String orgCode, String chnl, String orderNo) {
        String sql = loadSql();
        if (sql == null || sql.trim().isEmpty()) {
            log.error("[销售详情3] SQL 未加载到，sql-file={}", props.getSqlFile());
            return new ArrayList<>();
        }
        String p1 = safe(startDate), p2 = safe(endDate);
        String p3 = normalizeOrgCodes(orgCode), p4 = safe(chnl), p5 = safe(orderNo);

        log.info("[销售详情3] 开始取数 {}~{} 机构={} 渠道={} 订单号={}",
                p1, p2, p3.isEmpty() ? "(全部)" : p3, p4.isEmpty() ? "(全部)" : p4, p5.isEmpty() ? "(空)" : p5);

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.execute((ConnectionCallback<List<Map<String, Object>>>) conn -> {
            setVar(conn, "@开始日期", p1);
            setVar(conn, "@结束日期", p2);
            setVar(conn, "@机构", p3);
            setVar(conn, "@渠道", p4);
            setVar(conn, "@订单号", p5);

            List<Map<String, Object>> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    list.add(row);
                }
            }
            return list;
        });

        if (rows == null) {
            rows = new ArrayList<>();
        }
        log.info("[销售详情3] 取数完成，明细 {} 行，耗时 {} ms", rows.size(), System.currentTimeMillis() - t0);
        return rows;
    }

    private void setVar(Connection conn, String name, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SET " + name + " = ?")) {
            ps.setString(1, value);
            ps.execute();
        }
    }

    /** 加载 SQL：支持 classpath:xxx 或磁盘绝对路径 */
    private String loadSql() {
        String path = props.getSqlFile();
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        path = path.trim();
        try {
            if (path.startsWith("classpath:")) {
                String cp = path.substring("classpath:".length());
                ClassPathResource res = new ClassPathResource(cp);
                try (InputStream in = res.getInputStream()) {
                    return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                }
            }
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[销售详情3] 读取 SQL 文件失败: {}", path, e);
            return "";
        }
    }

    // ==================== 聚合 ====================

    /** 一个分组的累加器：销售额求和 + 包装费求和 + 订单号去重计数 */
    private static class Agg {
        BigDecimal amt = BigDecimal.ZERO;
        /** 包装费求和（2026-10-16：淘宝闪购的补差来源） */
        BigDecimal pkg = BigDecimal.ZERO;
        Set<String> orders = new LinkedHashSet<>();
    }

    /**
     * 明细 → 表格行。
     * 分组：门店编码 → 渠道；门店内多渠道则追加合计行；最后追加总计行。
     */
    public Sd3Result buildResult(List<Map<String, Object>> detail) {
        // storeCode -> chnl -> Agg
        Map<String, Map<String, Agg>> byStore = new TreeMap<>();   // 门店按编码升序
        Map<String, String> storeName = new LinkedHashMap<>();

        for (Map<String, Object> r : detail) {
            String code = str(r.get("门店编码"));
            String name = str(r.get("门店名称"));
            String ch = str(r.get("渠道"));
            String orderNo = str(r.get("订单号"));
            BigDecimal amt = dec(r.get("含税销售金额"));
            if (code.isEmpty() && name.isEmpty() && ch.isEmpty()) {
                continue;   // 全空行（如左连接无匹配）丢弃
            }
            storeName.putIfAbsent(code, name);
            Map<String, Agg> chnlMap = byStore.computeIfAbsent(code, k -> new LinkedHashMap<>());
            Agg a = chnlMap.computeIfAbsent(ch, k -> new Agg());
            a.amt = a.amt.add(amt);
            a.pkg = a.pkg.add(dec(r.get("包装费")));
            if (!orderNo.isEmpty()) {
                a.orders.add(orderNo);
            }
        }

        List<Sd3Row> rows = new ArrayList<>();
        BigDecimal totalAmt = BigDecimal.ZERO;
        int totalOrders = 0;
        int seq = 0;

        for (Map.Entry<String, Map<String, Agg>> se : byStore.entrySet()) {
            String code = se.getKey();
            Map<String, Agg> chnlMap = se.getValue();
            // 渠道按配置顺序展示，未配置的排最后
            List<String> chnls = sortChnl(chnlMap.keySet());

            BigDecimal storeAmt = BigDecimal.ZERO;
            Set<String> storeOrders = new LinkedHashSet<>();

            // 块行数 = 明细行数 + 合计行（多渠道时 +1）
            // 序号 / 门店编码 / 门店名称 三列在整块内合并（含合计行），所以首行 span = 块行数
            int blockRows = chnls.size() + (chnls.size() > 1 ? 1 : 0);
            int storeSeq = ++seq;

            for (int i = 0; i < chnls.size(); i++) {
                String ch = chnls.get(i);
                Agg a = chnlMap.get(ch);
                // 2026-10-16 补差：淘宝闪购 += 包装费求和；配置渠道 += 订单数 × 每单补差值
                BigDecimal finalAmt = a.amt.add(adjustAmt(ch, a.pkg, a.orders.size()));
                storeAmt = storeAmt.add(finalAmt);
                storeOrders.addAll(a.orders);
                // 首行带合并单元格（span=块行数），其余行 span=0（前端/截图不输出左侧三列）
                int span = (i == 0) ? blockRows : 0;
                rows.add(new Sd3Row(storeSeq, code, storeName.get(code), ch,
                        finalAmt, a.orders.size(), divide(finalAmt, a.orders.size()), "detail", span));
            }

            // 门店下多渠道才追加合计行（单渠道时合计等于自身，没有意义）
            // 合计行左侧三列被上面的合并单元格覆盖（span=0，不输出 td）
            if (chnls.size() > 1) {
                rows.add(new Sd3Row(null, "", "", "合计",
                        storeAmt, storeOrders.size(), divide(storeAmt, storeOrders.size()), "subtotal", 0));
            }

            totalAmt = totalAmt.add(storeAmt);
            totalOrders += storeOrders.size();
        }

        Sd3Row total = new Sd3Row(null, "", "", "", totalAmt, totalOrders,
                divide(totalAmt, totalOrders), "total", 0);
        total.setTotalLabel(props.getTotalLabel());

        return new Sd3Result(rows, total);
    }

    /**
     * 分组销售额补差（2026-10-16 新增）。
     *   淘宝闪购 → 补差 = 该组「包装费」列求和（数据来自 SQL 输出的包装费列）
     *   配置渠道 → 补差 = 该组订单数 × 每单补差值（sd3.chnl-per-order-fee，元/单）
     *   其余渠道 → 不补差（返回 0）
     */
    private BigDecimal adjustAmt(String chnl, BigDecimal pkgSum, int orderCnt) {
        if (PACKAGE_FEE_CHNL.equals(chnl)) {
            return pkgSum;
        }
        Map<String, BigDecimal> fees = props.getChnlPerOrderFee();
        if (fees == null || fees.isEmpty() || orderCnt <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal per = fees.get(chnl);
        if (per == null) {
            return BigDecimal.ZERO;
        }
        return per.multiply(BigDecimal.valueOf(orderCnt));
    }

    /** 渠道排序：按 yml 里 chnl-options 的顺序；未配置的排最后（按名称） */
    private List<String> sortChnl(Set<String> chnls) {
        List<String> options = (props.getChnlOptions() == null) ? new ArrayList<>() : props.getChnlOptions();
        List<String> head = new ArrayList<>();
        List<String> tail = new ArrayList<>();
        for (String opt : options) {
            if (chnls.contains(opt)) {
                head.add(opt);
            }
        }
        for (String c : chnls) {
            if (!head.contains(c)) {
                tail.add(c);
            }
        }
        tail.sort(Comparator.naturalOrder());
        head.addAll(tail);
        return head;
    }

    // ==================== 工具 ====================

    /** 客单价 = 销售额 ÷ 订单数；订单数为 0 时返回 0（避免除零） */
    private static BigDecimal divide(BigDecimal amt, int orders) {
        if (orders <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amt.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal) {
            return (BigDecimal) o;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 机构归一化：把「1103011,1103021」这类输入整理成 SQL 里 FIND_IN_SET 要的 'a,b,c'。
     * 兼容中英文逗号/分号分隔、各项前后空格、重复项；空输入返回空串（= 不按机构过滤）。
     */
    private static String normalizeOrgCodes(String orgCode) {
        String s = safe(orgCode);
        if (s.isEmpty()) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (String part : s.split("[,，;；\\s]+")) {
            String p = part.trim();
            if (!p.isEmpty() && !out.contains(p)) {
                out.add(p);
            }
        }
        return String.join(",", out);
    }

    // ==================== 数据模型 ====================

    /** 表格一行 */
    public static class Sd3Row {
        /** 序号（合计行/总计行为 null） */
        private Integer seq;
        private String storeCode;
        private String storeName;
        /** 平台（渠道）；合计行显示「合计」，总计行显示「总计」 */
        private String chnl;
        /** 销售额（分组含税销售金额合计 + 渠道补差；2026-10-16 起含补差） */
        private BigDecimal saleAmt;
        /** 订单数（去重后的订单号个数） */
        private int orderCnt;
        /** 客单价 = 销售额 ÷ 订单数（后端算好，页面与截图共用） */
        private BigDecimal avgPrice;
        /** 行类型：detail=渠道明细 / subtotal=门店合计 / total=全表总计 */
        private String rowType;
        /**
         * 左侧三列（序号/门店编码/门店名称）的纵向合并行数（页面 rowspan 与截图 rowspan 共用）。
         *   >0 = 输出这三个单元格并 rowspan=该值（门店首行 = 明细行数 + 合计行数）
         *   =0 = 不输出这三个单元格（被上面的合并单元格盖住了，含合计行）
         */
        private int storeRowSpan;
        /** 仅总计行使用：左侧合并大格的文案（如「菏泽区域线上销售共计」） */
        private String totalLabel;

        public Sd3Row(Integer seq, String storeCode, String storeName, String chnl,
                      BigDecimal saleAmt, int orderCnt, BigDecimal avgPrice, String rowType,
                      int storeRowSpan) {
            this.seq = seq;
            this.storeCode = storeCode;
            this.storeName = storeName;
            this.chnl = chnl;
            this.saleAmt = saleAmt;
            this.orderCnt = orderCnt;
            this.avgPrice = avgPrice;
            this.rowType = rowType;
            this.storeRowSpan = storeRowSpan;
        }

        public Integer getSeq() {
            return seq;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public String getStoreName() {
            return storeName;
        }

        public String getChnl() {
            return chnl;
        }

        public BigDecimal getSaleAmt() {
            return saleAmt;
        }

        public int getOrderCnt() {
            return orderCnt;
        }

        public BigDecimal getAvgPrice() {
            return avgPrice;
        }

        public String getRowType() {
            return rowType;
        }

        public int getStoreRowSpan() {
            return storeRowSpan;
        }

        public String getTotalLabel() {
            return totalLabel;
        }

        public void setTotalLabel(String totalLabel) {
            this.totalLabel = totalLabel;
        }
    }

    /** 查询结果：明细行（含各门店合计行）+ 总计行 */
    public static class Sd3Result {
        private final List<Sd3Row> rows;
        private final Sd3Row total;

        public Sd3Result(List<Sd3Row> rows, Sd3Row total) {
            this.rows = rows;
            this.total = total;
        }

        public List<Sd3Row> getRows() {
            return rows;
        }

        public Sd3Row getTotal() {
            return total;
        }
    }
}
