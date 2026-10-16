package com.demo.provider.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 销售详情4（SD4，配送调拨汇总表）配置，对应 application-sd4.yml 的 sd4 节点。
 *
 * ★ 与 SD1/SD2 的 ReportDailyProperties、SD3 的 Sd3Properties 完全独立，互不影响。
 * ★ 原 application.yml 未做任何改动，SD4 全部参数都在这个新类里。
 *
 * 参数分四类：
 *   1. 取数固定参数：tenantId / lang / 发货仓库 / 收货机构 / 收货仓库 / 部门 / 品类 / 类型 / 记账类型
 *      （对应原 SQL 里 DECLARE 的参数，仅后端配置，页面不可见）
 *   2. 页面可输入参数：6 个日期（记账/环比/同比 各起止）+ 发货机构
 *   3. 定时参数：cron、定时发送用的发货机构与日期（use-fixed-date 显式开关）
 *   4. 推送参数：邮件收件人、企微 webhook、企微摘要文案、截图标题
 */
@Component
@ConfigurationProperties(prefix = "sd4")
public class Sd4Properties {

    /** SQL Server 数据源（RDS_SC 库） */
    private DataSourceProps datasource = new DataSourceProps();

    /** 取数 SQL 文件路径（模板，含 ${xxx} 占位符）：支持 classpath:xxx 或绝对路径 */
    private String sqlFile = "classpath:sd4/delivery-transfer-summary.sql";

    // ===== 取数固定参数（原 SQL DECLARE 参数，仅后端配置）=====

    /** @tenantId（租户，固定 8） */
    private String tenantId = "8";
    /** @lang（L=中文名称） */
    private String lang = "L";
    /** @发货仓库（空=不筛选；多个逗号分隔） */
    private String outWnoCodes = "";
    /** @收货机构（空=不筛选；多个逗号分隔，支持超长清单） */
    private String inOrgCodes = "";
    /** @收货仓库（空=不筛选；多个逗号分隔） */
    private String inWnoCodes = "";
    /** @部门（空=不筛选；多个逗号分隔，支持超长清单） */
    private String departCodes = "";
    /** @品类（空=不筛选；多个逗号分隔） */
    private String categoryCodes = "";
    /** @类型（空=全部；配送/退配/其他） */
    private String billType = "";
    /** @记账类型（空=全部；内部配送/内部批发） */
    private String accType = "";

    // ===== 页面可输入参数（6 日期 + 发货机构）=====

    /** 页面发货机构框默认值（空=不筛选） */
    private String defaultOutOrgCodes = "";

    // ===== 定时任务 =====

    /** 定时任务 cron（默认每天 8:50，避开 SD1/SD2 的 8:29 与 SD3 的 8:35） */
    private String cron = "0 50 8 * * ?";

    /** 定时发送时使用的发货机构（空=全部） */
    private String outOrgCodes = "";

    /**
     * 日期来源开关：
     *   false（默认）= 每天自动推算（记账=前一天、环比=前两天、同比=去年同日，与 SD1 一致）；
     *   true         = 固定使用下面 6 个日期（补发历史数据时用）。
     * 与 SD1/SD2/SD3 的 use-fixed-date 语义一致：用显式开关，不靠「日期填没填」隐式推断。
     */
    private boolean useFixedDate = false;
    private String accStartDate = "";
    private String accEndDate = "";
    private String cmpStartDate = "";
    private String cmpEndDate = "";
    private String yoyStartDate = "";
    private String yoyEndDate = "";

    // ===== 推送参数 =====

    /** 邮件收件人（留空不发邮件） */
    private String mailTo = "";
    /** 企业微信机器人 Webhook（留空不推企微） */
    private String wecomWebhook = "";
    /** 企微摘要文案（留空用默认模板，支持占位符 {date} {accRange} {cmpRange} {yoyRange} {org}） */
    private String wecomDigest = "";
    /** 截图输出目录 */
    private String outputDir = "target/report";

    /** 截图顶部标题（默认「配送调拨汇总表」） */
    private String title = "配送调拨汇总表";

    /** SQL Server 数据源配置（前缀 sd4.datasource） */
    public static class DataSourceProps {
        private String url = "";
        private String username = "";
        private String password = "";
        private String driverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }

    public DataSourceProps getDatasource() {
        return datasource;
    }

    public void setDatasource(DataSourceProps datasource) {
        this.datasource = datasource;
    }

    public String getSqlFile() {
        return sqlFile;
    }

    public void setSqlFile(String sqlFile) {
        this.sqlFile = sqlFile;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getOutWnoCodes() {
        return outWnoCodes;
    }

    public void setOutWnoCodes(String outWnoCodes) {
        this.outWnoCodes = outWnoCodes;
    }

    public String getInOrgCodes() {
        return inOrgCodes;
    }

    public void setInOrgCodes(String inOrgCodes) {
        this.inOrgCodes = inOrgCodes;
    }

    public String getInWnoCodes() {
        return inWnoCodes;
    }

    public void setInWnoCodes(String inWnoCodes) {
        this.inWnoCodes = inWnoCodes;
    }

    public String getDepartCodes() {
        return departCodes;
    }

    public void setDepartCodes(String departCodes) {
        this.departCodes = departCodes;
    }

    public String getCategoryCodes() {
        return categoryCodes;
    }

    public void setCategoryCodes(String categoryCodes) {
        this.categoryCodes = categoryCodes;
    }

    public String getBillType() {
        return billType;
    }

    public void setBillType(String billType) {
        this.billType = billType;
    }

    public String getAccType() {
        return accType;
    }

    public void setAccType(String accType) {
        this.accType = accType;
    }

    public String getDefaultOutOrgCodes() {
        return defaultOutOrgCodes;
    }

    public void setDefaultOutOrgCodes(String defaultOutOrgCodes) {
        this.defaultOutOrgCodes = defaultOutOrgCodes;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getOutOrgCodes() {
        return outOrgCodes;
    }

    public void setOutOrgCodes(String outOrgCodes) {
        this.outOrgCodes = outOrgCodes;
    }

    public boolean isUseFixedDate() {
        return useFixedDate;
    }

    public void setUseFixedDate(boolean useFixedDate) {
        this.useFixedDate = useFixedDate;
    }

    public String getAccStartDate() {
        return accStartDate;
    }

    public void setAccStartDate(String accStartDate) {
        this.accStartDate = accStartDate;
    }

    public String getAccEndDate() {
        return accEndDate;
    }

    public void setAccEndDate(String accEndDate) {
        this.accEndDate = accEndDate;
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

    public String getMailTo() {
        return mailTo;
    }

    public void setMailTo(String mailTo) {
        this.mailTo = mailTo;
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

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
