package com.demo.provider.precompute;

import com.demo.provider.service.SalesDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 销售报表预计算业务 Service（RDS_SC · dw schema）
 *
 * 同时支撑 销售报表1（销售详情1）与 销售报表2（销售详情2），底层共用同一引擎 dm.up_GetFine_Run，
 * 仅入参与前端聚合口径不同，通过 report_type 区分（SALE_DETAIL_1 / SALE_DETAIL_2），复用同一张宽表。
 *
 * ===== 销售报表1（SALE_DETAIL_1）=====
 *   凌晨 2:00 跑【三档 × 2 次对比】= 6 次引擎调用全量落库：
 *     deptLevels 档位编码 3=明细 / 2=部门合计 / 1=机构汇总（超市总计）
 *   查询按档直取引擎结果，零聚合（方案 A：合计/总计行不能累加，必须存引擎结果）。
 *   多机构：逐个机构跑批落库，org_code 列区分，batchId 含机构编码防冲突。
 *
 * ===== 销售报表2（SALE_DETAIL_2）=====
 *   引擎入参与 3001 SalesDetail2.vue 逐字一致：orgCode=多机构整体串（如 1101,1102,1191001，一次调用全返回，
 *   行自带机构编码）、deptLevels 留空、department 留空。
 *   落库 dept_levels=0（与报表1 的 3/2/1 天然隔离），org_code 列存**行内机构编码**（1101/1102/1191001）。
 *   前端（SalesDetail2Data.vue）查表后按机构编码 SUM + 比率重算（复用 SalesDetail2.vue 的 storeRows/buildSubtotal），
 *   与 3001 引擎直查对账零差异。
 *
 * 🔴 安全红线：引擎只读；本类只写 dw.rpt_sale_detail_precompute / dw.etl_batch_log；幂等删除仅限预计算表自身。
 *
 * 日期规则（与前端一致）：本期 queryDate=跑批日-1；环比对期=queryDate-1（前两天）；同比对期=去年本期的今天就近取同周几。
 */
@Service
public class SaleDetailPrecomputeService {

    private static final Logger log = LoggerFactory.getLogger(SaleDetailPrecomputeService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BTF = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String REPORT_TYPE = "SALE_DETAIL_1";
    public static final String REPORT_TYPE_2 = "SALE_DETAIL_2";
    public static final String DEFAULT_ORG = "1101001";
    /** 报表2 默认多机构整体串（可被 application.yml 的 report.precompute.sale2.org-code 覆盖） */
    public static final String DEFAULT_ORG_R2 = "1101,1102,1191001";
    /** 报表2 落库档位标识：与报表1 的 3/2/1 区分（报表2 是部门级明细，按机构编码聚合，不拆档位） */
    public static final int DEPT_LEVELS_R2 = 0;

    /**
     * 报表1 档位编码（写入 dept_levels 列）：3=明细 / 2=部门合计 / 1=机构汇总（超市总计）
     * 引擎 deptLevels 参数映射：3->"3"、2->"2"、1->""（空串=机构汇总，与前端第 4/6 次调用一致）
     */
    private static final int[] LEVELS = {3, 2, 1};

    /** 档位编码 -> 引擎 deptLevels 参数（1 档 = 机构汇总，传空串与前端第 4/6 次调用一致） */
    private static String engineDeptLevels(int level) {
        return level == 1 ? "" : String.valueOf(level);
    }

    @Autowired
    private SalesDetailService salesDetailService;

    @Autowired
    private PrecomputeRepository precomputeRepository;

    // =====================================================================
    // 跑批（定时 / 手动共用）—— 统一入口，按 reportType 派发
    // =====================================================================

    /**
     * 统一跑批入口（推荐使用）：直接传 orgCode 逗号串 + reportType。
     * - SALE_DETAIL_2：orgCode 为多机构整体串（如 1101,1102,1191001），一次引擎调用；
     * - 其它（默认 SALE_DETAIL_1）：orgCode 按逗号拆分为多个机构，逐个机构跑三档。
     *
     * @return { code, queryDate, reportType, orgCode/orgCodes, momRows, yoyRows, message }
     */
    public Map<String, Object> runPrecompute(LocalDate queryDate, String triggerType, String orgCodeCsv, String reportType) {
        if (REPORT_TYPE_2.equals(reportType)) {
            return runReport2(queryDate, triggerType,
                    (orgCodeCsv == null || orgCodeCsv.trim().isEmpty()) ? DEFAULT_ORG_R2 : orgCodeCsv.trim());
        }
        // 报表1：逗号串 -> 多个机构，逐个跑三档
        List<String> orgs = normalizeOrgs(java.util.Arrays.asList(
                (orgCodeCsv == null ? "" : orgCodeCsv).split(",")));
        return runPrecompute(queryDate, triggerType, orgs);
    }

    /**
     * 报表1 跑批（默认机构，兼容旧调用）：逐个机构跑 MOM + YOY 三档并落库（幂等）。
     *
     * @param orgCodes 机构编码列表（可多个；空/去重后为空则回退默认机构）
     */
    public Map<String, Object> runPrecompute(LocalDate queryDate, String triggerType, List<String> orgCodes) {
        LocalDate today = LocalDate.now();
        if (queryDate.isAfter(today.minusDays(1))) {
            throw new IllegalArgumentException("业务日期最晚为昨天（当天数据未结算），输入：" + queryDate);
        }
        if (queryDate.isBefore(LocalDate.of(2015, 1, 1))) {
            throw new IllegalArgumentException("业务日期不能早于 2015-01-01，输入：" + queryDate);
        }

        List<String> orgs = normalizeOrgs(orgCodes);
        LocalDate momDate = queryDate.minusDays(1);                 // 环比对期 = 前两天
        LocalDate yoyDate = calcYoySameDow(queryDate);              // 同比对期 = 去年本期就近同周几（与前端一致）

        log.info("[预计算] 开始跑批 queryDate={} MOM对期={} YOY对期={} trigger={} 机构={} 档位={}",
                queryDate, momDate, yoyDate, triggerType, orgs, java.util.Arrays.toString(LEVELS));

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("code", 0);
        ret.put("queryDate", queryDate.format(DTF));
        ret.put("orgCodes", orgs);

        int totalMom = 0;
        int totalYoy = 0;
        StringBuilder msg = new StringBuilder();
        for (String org : orgs) {
            int momRows = 0;
            int yoyRows = 0;
            for (int level : LEVELS) {
                momRows += runOnce(queryDate, momDate, "MOM", triggerType, level, org);
            }
            for (int level : LEVELS) {
                yoyRows += runOnce(queryDate, yoyDate, "YOY", triggerType, level, org);
            }
            totalMom += momRows;
            totalYoy += yoyRows;
            ret.put("momRows_" + org, momRows);
            ret.put("yoyRows_" + org, yoyRows);
            msg.append(org).append(":MOM ").append(momRows).append("行/YOY ").append(yoyRows).append("行; ");
        }

        ret.put("momRows", totalMom);
        ret.put("yoyRows", totalYoy);
        ret.put("message", queryDate.format(DTF) + " 回补完成 " + msg.toString().trim());
        log.info("[预计算] 跑批完成 queryDate={} 机构={} MOM={}行 YOY={}行", queryDate, orgs, totalMom, totalYoy);
        return ret;
    }

    // =====================================================================
    // 报表2 跑批（SALE_DETAIL_2）：多机构整体串一次引擎调用 × MOM/YOY
    // =====================================================================

    private Map<String, Object> runReport2(LocalDate queryDate, String triggerType, String orgCodeCsv) {
        LocalDate today = LocalDate.now();
        if (queryDate.isAfter(today.minusDays(1))) {
            throw new IllegalArgumentException("业务日期最晚为昨天（当天数据未结算），输入：" + queryDate);
        }
        if (queryDate.isBefore(LocalDate.of(2015, 1, 1))) {
            throw new IllegalArgumentException("业务日期不能早于 2015-01-01，输入：" + queryDate);
        }

        LocalDate momDate = queryDate.minusDays(1);                 // 环比对期 = 前两天
        LocalDate yoyDate = calcYoySameDow(queryDate);              // 同比对期 = 去年本期就近同周几

        log.info("[预计算] 开始跑批(报表2) queryDate={} MOM对期={} YOY对期={} trigger={} orgCode={}",
                queryDate, momDate, yoyDate, triggerType, orgCodeCsv);

        int momRows = runOnceR2(queryDate, momDate, "MOM", triggerType, orgCodeCsv);
        int yoyRows = runOnceR2(queryDate, yoyDate, "YOY", triggerType, orgCodeCsv);

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("code", 0);
        ret.put("queryDate", queryDate.format(DTF));
        ret.put("reportType", REPORT_TYPE_2);
        ret.put("orgCode", orgCodeCsv);
        ret.put("momRows", momRows);
        ret.put("yoyRows", yoyRows);
        ret.put("message", queryDate.format(DTF) + " 报表2 回补完成 MOM " + momRows + "行 / YOY " + yoyRows + "行 (org=" + orgCodeCsv + ")");
        log.info("[预计算] 报表2 跑批完成 queryDate={} org={} MOM={}行 YOY={}行", queryDate, orgCodeCsv, momRows, yoyRows);
        return ret;
    }

    /** 报表2 一次引擎调用（多机构整体串，deptLevels 留空，部门级明细） */
    /**
     * 同比日期 = 去年本期的今天（本期−1年），就近取与本期同周几的最近一天。
     * 例：本期 2026-08-27 周四 → 去年本期 2025-08-27 周三 → 最近周四 = 2025-08-28。
     * 无平局：偏移 d∈[0,6]，d≤3 往后取，d≥4 往前取。
     */
    private LocalDate calcYoySameDow(LocalDate cur) {
        LocalDate base = cur.minusYears(1);
        int d = (cur.getDayOfWeek().getValue() - base.getDayOfWeek().getValue() + 7) % 7;
        return d <= 3 ? base.plusDays(d) : base.minusDays(7 - d);
    }

    private int runOnceR2(LocalDate queryDate, LocalDate cmpDate, String comparisonType, String triggerType, String orgCodeCsv) {
        String qd = queryDate.format(DTF);
        String cd = cmpDate.format(DTF);
        PrecomputeRepository.BatchContext ctx = buildCtxR2(queryDate, cmpDate, comparisonType, triggerType, orgCodeCsv);

        List<Map<String, Object>> rows;
        try {
            // 报表2 引擎入参：与 3001 SalesDetail2.vue 逐字一致
            // orgCode = 多机构整体串（一次调用返回所有机构部门明细）；deptLevels/department 留空（后端默认口径）
            rows = salesDetailService.getSalesDetail(
                    "8", "L", "SYSTEM", "记账日期",
                    qd, qd, cd, cd,
                    "显示门店", "", "", "不显示品牌",
                    orgCodeCsv, "", "", "", "",
                    "0", "否", "显示日期");
        } catch (Exception e) {
            log.error("[预计算] 报表2 引擎查询失败 {} {} {}: {}", queryDate, orgCodeCsv, comparisonType, e.getMessage());
            precomputeRepository.logFailure(ctx, "引擎查询失败: " + e.getMessage());
            throw new RuntimeException("报表2 引擎查询失败(" + queryDate + "/" + orgCodeCsv + "/" + comparisonType + "): " + e.getMessage(), e);
        }

        int n = precomputeRepository.batchInsert(rows, ctx);
        log.info("[预计算] 报表2 写入成功 {} {} {} {} 行", queryDate, orgCodeCsv, comparisonType, n);
        return n;
    }

    private PrecomputeRepository.BatchContext buildCtxR2(LocalDate queryDate, LocalDate cmpDate,
                                                          String comparisonType, String triggerType, String orgCodeCsv) {
        // batchId 含多机构串（约 31 字符，VARCHAR(50) 足够）；dept_levels=0 标识报表2
        String batchId = queryDate.format(BTF) + "_" + orgCodeCsv + "_" + comparisonType + "_" + DEPT_LEVELS_R2;
        return new PrecomputeRepository.BatchContext(
                batchId, queryDate, comparisonType, cmpDate, cmpDate,
                orgCodeCsv, REPORT_TYPE_2, triggerType, DEPT_LEVELS_R2);
    }

    // =====================================================================
    // 报表1 单次引擎调用 + 批次上下文（保持不变）
    // =====================================================================

    /** 归一化机构编码列表：去空白、去空、去重（保序）；全部为空则回退默认机构 */
    private List<String> normalizeOrgs(List<String> orgCodes) {
        if (orgCodes == null || orgCodes.isEmpty()) {
            return java.util.Collections.singletonList(DEFAULT_ORG);
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : orgCodes) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        if (set.isEmpty()) {
            return java.util.Collections.singletonList(DEFAULT_ORG);
        }
        return new ArrayList<>(set);
    }

    /** 跑一次引擎（一个对比类型 × 一个档位 × 一个机构）并落库，失败时记录 FAILED 日志后抛出 */
    private int runOnce(LocalDate queryDate, LocalDate cmpDate, String comparisonType, String triggerType, int level, String orgCode) {
        String qd = queryDate.format(DTF);
        String cd = cmpDate.format(DTF);
        PrecomputeRepository.BatchContext ctx = buildCtx(queryDate, cmpDate, comparisonType, triggerType, level, orgCode);

        List<Map<String, Object>> rows;
        try {
            // 参数与前端实际到后端的完全一致（Controller 默认值：showBrand=不显示品牌 / comparableStore=0 / showPlan=否 / showDate=显示日期 / userNo=SYSTEM）
            // deptLevels 按档位映射：3->"3" / 2->"2" / 1->""（机构汇总）；orgCode 为当前机构
            rows = salesDetailService.getSalesDetail(
                    "8", "L", "SYSTEM", "记账日期",
                    qd, qd, cd, cd,
                    "显示门店", engineDeptLevels(level), "", "不显示品牌",
                    orgCode, "", "", "", "",
                    "0", "否", "显示日期");
        } catch (Exception e) {
            log.error("[预计算] 引擎查询失败 {} {} {} 档{}: {}", queryDate, orgCode, comparisonType, level, e.getMessage());
            precomputeRepository.logFailure(ctx, "引擎查询失败: " + e.getMessage());
            throw new RuntimeException("引擎查询失败(" + queryDate + "/" + orgCode + "/" + comparisonType + "/档" + level + "): " + e.getMessage(), e);
        }

        int n = precomputeRepository.batchInsert(rows, ctx);
        log.info("[预计算] 写入成功 {} {} {} 档{} {} 行", queryDate, orgCode, comparisonType, level, n);
        return n;
    }

    private PrecomputeRepository.BatchContext buildCtx(LocalDate queryDate, LocalDate cmpDate,
                                                       String comparisonType, String triggerType, int level, String orgCode) {
        // batchId 含机构编码，避免多机构同批跑批日志/幂等标识冲突（列宽 VARCHAR(50)，本格式约 22 字符）
        String batchId = queryDate.format(BTF) + "_" + orgCode + "_" + comparisonType + "_" + level;
        return new PrecomputeRepository.BatchContext(
                batchId, queryDate, comparisonType, cmpDate, cmpDate,
                orgCode, REPORT_TYPE, triggerType, level);
    }

    // =====================================================================
    // 查询（查预计算表，替代原引擎查询）
    // =====================================================================

    /**
     * 查询预计算表（前端销售详情1/2 的取数接口）
     *
     * @param queryDate      业务日期 yyyy-MM-dd
     * @param comparisonType MOM=环比 / YOY=同比
     * @param orgCode        机构编码（报表2 支持逗号分隔多机构，按 IN 过滤；报表1 单机构）
     * @param deptLevels     报表1：3=明细 / 2=部门合计 / 空或1=机构汇总；报表2 忽略此参数（恒为 0）
     * @param reportType     SALE_DETAIL_1 / SALE_DETAIL_2（默认 SALE_DETAIL_1）
     */
    public List<Map<String, Object>> query(String queryDate, String comparisonType, String orgCode, String deptLevels, String reportType) {
        LocalDate qd = LocalDate.parse(queryDate, DTF);
        String org = (orgCode == null || orgCode.trim().isEmpty()) ? DEFAULT_ORG : orgCode.trim();
        List<String> orgs = Arrays.asList(org.split(","));
        String rt = (reportType == null || reportType.trim().isEmpty()) ? REPORT_TYPE : reportType.trim();
        int level;
        if (REPORT_TYPE_2.equals(rt)) {
            level = DEPT_LEVELS_R2;          // 报表2：恒为 0（部门级明细，按机构编码聚合）
        } else {
            String lv = deptLevels == null ? "" : deptLevels.trim();
            if ("2".equals(lv)) {
                level = 2;          // 部门合计
            } else if ("3".equals(lv)) {
                level = 3;          // 明细
            } else {
                level = 1;          // 空 / "1" / 其它 → 机构汇总（超市总计）
            }
        }
        return precomputeRepository.queryByReportType(qd, comparisonType, orgs, rt, level);
    }

    /** 最近跑批记录（前端「预计算管理」面板，按 reportType 过滤） */
    public List<Map<String, Object>> batchLogs(int limit, String reportType) {
        String rt = (reportType == null || reportType.trim().isEmpty()) ? REPORT_TYPE : reportType.trim();
        return precomputeRepository.lastBatchLogs(limit <= 0 ? 20 : limit, rt);
    }

    /**
     * UNION ALL 追加「仅库存」门店（3003 页面勾选 Union 特殊逻辑时调用，与 /detail 接口同源）。
     * 预计算表是凌晨跑批快照；补充行库存取实时 tb_wb_gdsstock，仅用于排查/核对。
     */
    public void mergeStockOnlyRows(List<Map<String, Object>> data, String codes) {
        salesDetailService.mergeStockOnlyRows(data, codes);
    }
}
