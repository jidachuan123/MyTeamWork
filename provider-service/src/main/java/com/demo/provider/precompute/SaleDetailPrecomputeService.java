package com.demo.provider.precompute;

import com.demo.provider.service.SalesDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 销售报表1 预计算业务 Service（RDS_SC · dw schema）
 *
 * 核心思路（设计文档第二章 + 2026-08-18 方案修正）：
 *   凌晨 2:00 跑【三档 × 2 次对比】= 6 次引擎调用全量落库：
 *     deptLevels 档位编码 3=明细 / 2=部门合计 / 1=机构汇总（超市总计），
 *     对应引擎 deptLevels 参数 "3" / "2" / ""（空=机构汇总，与前端第 4/6 次调用一致）。
 *   ⚠️ 修正原因：合计/总计行若用 GROUP BY 累加，来客数/客单价/同环比增长率等
 *      比率、均值、去重口径列会算错——必须存引擎直查结果，查询按档直取、零聚合。
 *   ⚠️ 多机构（2026-08-18）：手动触发/定时任务均支持多个机构编码（英文逗号分隔），
 *      逐个机构跑批落库；org_code 列区分，batchId 含机构编码防冲突。
 *
 * 🔴 安全红线：
 *   - 引擎 dm.up_GetFine_Run 只读；本类只写 dw.rpt_sale_detail_precompute / dw.etl_batch_log
 *   - 幂等：每次跑批前先删预计算表自身该日该档旧数据（带全条件），绝不碰生产表
 *
 * 日期规则（与前端 SalesDetail.vue defaultQueryForm 完全一致）：
 *   本期 queryDate   = 跑批日 - 1（昨天）
 *   环比对期 momDate  = queryDate - 1（前两天）
 *   同比对期 yoyDate  = queryDate + 1 再减一年 = 跑批日的去年今天（前端"去年的今天"规则）
 */
@Service
public class SaleDetailPrecomputeService {

    private static final Logger log = LoggerFactory.getLogger(SaleDetailPrecomputeService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BTF = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final String REPORT_TYPE = "SALE_DETAIL_1";
    public static final String DEFAULT_ORG = "1101001";

    /**
     * 档位编码（写入 dept_levels 列，INT）：3=明细 / 2=部门合计 / 1=机构汇总（超市总计）
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
    // 跑批（定时 / 手动共用）
    // =====================================================================

    /**
     * 对指定业务日期执行一次完整跑批：MOM + YOY 各跑一遍引擎并落库（幂等）。
     * 默认机构（DEFAULT_ORG=1101001），兼容旧调用。
     *
     * @param queryDate   业务日期（定时=昨天；手动=用户输入任意历史日期）
     * @param triggerType SCHEDULE=定时 / MANUAL=手动
     * @return { code, queryDate, message, momRows, yoyRows }
     */
    public Map<String, Object> runPrecompute(LocalDate queryDate, String triggerType) {
        return runPrecompute(queryDate, triggerType, java.util.Collections.singletonList(DEFAULT_ORG));
    }

    /**
     * 对指定业务日期执行一次完整跑批（支持多机构）：逐个机构跑 MOM + YOY 三档并落库（幂等）。
     *
     * @param queryDate   业务日期（定时=昨天；手动=用户输入任意历史日期）
     * @param triggerType SCHEDULE=定时 / MANUAL=手动
     * @param orgCodes    机构编码列表（可多个；空/去重后为空则回退默认机构）
     * @return { code, queryDate, orgCodes, message, momRows, yoyRows, momRows_<org>?, yoyRows_<org>? }
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
        LocalDate yoyDate = queryDate.plusDays(1).minusYears(1);    // 同比对期 = 去年今天（与前端一致）

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
     * 查询预计算表（前端销售详情3 的取数接口）——按档位直取引擎结果，零聚合
     *
     * @param queryDate      业务日期 yyyy-MM-dd
     * @param comparisonType MOM=环比 / YOY=同比
     * @param orgCode        机构编码
     * @param deptLevels     3=明细 / 2=部门合计 / 空或1=机构汇总（超市总计）
     */
    public List<Map<String, Object>> query(String queryDate, String comparisonType, String orgCode, String deptLevels) {
        LocalDate qd = LocalDate.parse(queryDate, DTF);
        String org = (orgCode == null || orgCode.trim().isEmpty()) ? DEFAULT_ORG : orgCode.trim();
        String lv = deptLevels == null ? "" : deptLevels.trim();
        int level;
        if ("2".equals(lv)) {
            level = 2;          // 部门合计
        } else if ("3".equals(lv)) {
            level = 3;          // 明细
        } else {
            level = 1;          // 空 / "1" / 其它 → 机构汇总（超市总计）
        }
        return precomputeRepository.queryByDeptLevel(qd, comparisonType, org, REPORT_TYPE, level);
    }

    /** 最近跑批记录（前端「预计算管理」面板） */
    public List<Map<String, Object>> batchLogs(int limit) {
        return precomputeRepository.lastBatchLogs(limit <= 0 ? 20 : limit);
    }
}
