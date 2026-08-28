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
