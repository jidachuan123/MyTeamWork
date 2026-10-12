package com.demo.provider.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售日报定时任务配置（对应 application.yml 的 report.daily 节点）
 *
 * 配置驱动：主邮件（main）/ 副邮件（secondary）各自的收件人与截图列表都在 yml 中声明，
 * 新增/删除截图只需在对应分组的 screenshots 列表增删一条记录，无需改代码。
 *
 * yml 结构示例：
 * <pre>
 * report:
 *   daily:
 *     main:
 *       mail-to: "xxx@qq.com"
 *       screenshots:
 *         - type: sd1            # sd1=销售详情1，sd2=销售详情2
 *           org-code: "1101001"  # 机构编码（多机构逗号分隔）
 *           tag: ""              # 文件名后缀（同报表多张不同机构时必填，防覆盖）
 *     secondary:
 *       mail-to: "yyy@qq.com"
 *       screenshots: [...]
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "report.daily")
public class ReportDailyProperties {

    /** 定时任务 cron 表达式 */
    private String cron = "0 15 8 * * ?";
    /** 发件人（需与 spring.mail.username 一致） */
    private String mailFrom;
    /** 默认收件人兜底（仅手动触发接口未指定收件人时使用） */
    private String mailTo;
    /** Chrome/Edge 可执行文件路径，留空自动探测 */
    private String chromePath;
    /** HTML/截图输出目录 */
    private String outputDir;

    /** 主邮件：收件人 + 截图列表 */
    private MailGroup main = new MailGroup();
    /** 副邮件：收件人 + 截图列表 */
    private MailGroup secondary = new MailGroup();

    /** 一个邮件分组：一个收件人 + 多张截图 */
    public static class MailGroup {
        private String mailTo = "";
        private List<ScreenshotTask> screenshots = new ArrayList<>();

        /**
         * 企业微信群机器人 Webhook 地址（2026-10-11 新增）。
         * 留空 = 不推企微；填了就把本组截图推到对应群（先发一条摘要，再逐张发图）。
         * 与 mail-to 互不冲突：两个都填就邮件和企微都发；想停邮件把 mail-to 清空即可。
         * 获取方式：企业微信群 → 右上角设置 → 群机器人 → 添加 → 复制 Webhook 地址。
         */
        private String wecomWebhook = "";

        /**
         * 企业微信推送的「摘要文案」（2026-10-12 新增）。
         * 留空 = 用代码默认模板（标题 + 报表范围 + 本期/环比/同比）。
         * 填了 = 完全按你写的原文发送（markdown 语法可用：**加粗** / > 引用）。
         *
         * 支持占位符（发消息时自动替换，不想用可以不写）：
         *   {date} 报表日期（固定日期模式取配置的结束日期，否则取本期日期）
         *   {cur}  本期区间，如 2026-09-01 ~ 2026-09-30
         *   {cmp}  环比区间
         *   {yoy}  同比区间
         *   {range} 报表范围（各截图任务的名称+机构拼成）
         *
         * yml 多行写法（推荐，换行用 \n 会很难读）：
         * <pre>
         * wecom-digest: |
         *   **销售日报 {date}**
         *   > 本期：{cur}
         *   > 环比：{cmp}
         * </pre>
         */
        private String wecomDigest = "";

        // ===== 日期来源配置（2026-10-01 新增）=====
        // useFixedDate=false（默认）：本组所有截图按「本期=前一天、环比=前两天、同比=去年同日」推算（原逻辑，行为不变）
        // useFixedDate=true        ：本组所有截图统一用下面 6 个日期查询，不再推算
        // ★ 用显式开关而不是「日期有没有填」来判断，避免漏填一个日期就静默改变整个分组的口径
        private boolean useFixedDate = false;

        // 本期开始 / 结束日期（yyyy-MM-dd）；留空则该字段回退到推算值，并在日志里 WARN
        private String startDate = "";
        private String endDate = "";
        // 环比对比开始 / 结束日期
        private String cmpStartDate = "";
        private String cmpEndDate = "";
        // 同比对比开始 / 结束日期
        private String yoyStartDate = "";
        private String yoyEndDate = "";

        public String getMailTo() {
            return mailTo;
        }

        public void setMailTo(String mailTo) {
            this.mailTo = mailTo;
        }

        public List<ScreenshotTask> getScreenshots() {
            return screenshots;
        }

        public void setScreenshots(List<ScreenshotTask> screenshots) {
            this.screenshots = screenshots;
        }

        public String getWecomWebhook() {
            return wecomWebhook;
        }

        public void setWecomWebhook(String wecomWebhook) {
            this.wecomWebhook = wecomWebhook;
        }

        public String getWecomDigest() {
            return wecomDigest;
        }

        public void setWecomDigest(String wecomDigest) {
            this.wecomDigest = wecomDigest;
        }

        public boolean isUseFixedDate() {
            return useFixedDate;
        }

        public void setUseFixedDate(boolean useFixedDate) {
            this.useFixedDate = useFixedDate;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public String getCmpStartDate() {
            return cmpStartDate;
        }

        public void setCmpStartDate(String cmpStartDate) {
            this.cmpStartDate = cmpStartDate;
        }

        public String getCmpEndDate() {
            return cmpEndDate;
        }

        public void setCmpEndDate(String cmpEndDate) {
            this.cmpEndDate = cmpEndDate;
        }

        public String getYoyStartDate() {
            return yoyStartDate;
        }

        public void setYoyStartDate(String yoyStartDate) {
            this.yoyStartDate = yoyStartDate;
        }

        public String getYoyEndDate() {
            return yoyEndDate;
        }

        public void setYoyEndDate(String yoyEndDate) {
            this.yoyEndDate = yoyEndDate;
        }
    }

    /** 一张截图任务 */
    public static class ScreenshotTask {
        /** 报表类型：sd1=销售详情1，sd2=销售详情2 */
        private String type = "sd1";
        /** 机构编码（多机构用英文逗号分隔，如 "1101,1102,1191001"） */
        private String orgCode = "";
        /** 文件名后缀：同一报表多张不同机构时必填（如 "1101031" → sales-detail-{date}-1101031.png），留空则不带后缀 */
        private String tag = "";
        /** 追加「仅库存」门店（UNION ALL 补充行，目前用于副收件人销售详情2）：逗号分隔机构编码，
         *  如 "1104901,1103801"。这些门店即使引擎查询未返回（无销售）也会出现在截图里，仅带库存三列，
         *  其余指标按 0 处理；引擎已返回的门店不重复追加（防库存翻倍）。留空不追加。 */
        private String unionStockCodes = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getOrgCode() {
            return orgCode;
        }

        public void setOrgCode(String orgCode) {
            this.orgCode = orgCode;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getUnionStockCodes() {
            return unionStockCodes;
        }

        public void setUnionStockCodes(String unionStockCodes) {
            this.unionStockCodes = unionStockCodes;
        }
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getMailTo() {
        return mailTo;
    }

    public void setMailTo(String mailTo) {
        this.mailTo = mailTo;
    }

    public String getChromePath() {
        return chromePath;
    }

    public void setChromePath(String chromePath) {
        this.chromePath = chromePath;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public MailGroup getMain() {
        return main;
    }

    public void setMain(MailGroup main) {
        this.main = main;
    }

    public MailGroup getSecondary() {
        return secondary;
    }

    public void setSecondary(MailGroup secondary) {
        this.secondary = secondary;
    }
}
