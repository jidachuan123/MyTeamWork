package com.demo.provider.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售详情3（SD3）配置，对应 application-sd3.yml 的 sd3 节点。
 *
 * ★ 与 SD1/SD2 的 ReportDailyProperties 完全独立，互不影响。
 * ★ 原 application.yml 未做任何改动，SD3 全部参数都在这个新类里。
 *
 * 参数分三类：
 *   1. 取数参数：机构 / 渠道 / 订单号 / 日期（页面和定时任务共用一套定义）
 *   2. 定时参数：cron、定时发送时用的机构与渠道
 *   3. 推送参数：邮件收件人、企微 webhook、企微摘要文案
 */
@Component
@ConfigurationProperties(prefix = "sd3")
public class Sd3Properties {

    /** MySQL 数据源（bcm 库） */
    private DataSourceProps datasource = new DataSourceProps();

    /** 取数 SQL 文件路径：支持 classpath:xxx 或绝对路径 */
    private String sqlFile = "classpath:sd3/sales-detail3.sql";

    /** 页面打开时机构框默认值（空=不筛选） */
    private String defaultOrgCode = "";
    /** 页面打开时渠道框默认值（空=全部渠道） */
    private String defaultChnl = "";

    /** 渠道下拉选项（顺序即表格中渠道分组的展示顺序） */
    private List<String> chnlOptions = new ArrayList<>();

    /** 定时任务 cron（默认每天 8:35，避开 SD1/SD2 的 8:29） */
    private String cron = "0 35 8 * * ?";

    /** 定时发送时使用的机构（门店编码前缀；空=全部机构） */
    private String orgCode = "";
    /** 定时发送时使用的渠道（空=全部渠道） */
    private String chnl = "";
    /** 定时发送时使用的订单号（一般留空） */
    private String orderNo = "";

    /**
     * 日期来源开关：
     *   false（默认）= 本期取「前一天」；
     *   true         = 固定使用 startDate/endDate（补发历史数据时用）。
     * 与 SD1/SD2 的 use-fixed-date 语义一致：用显式开关，不靠「日期填没填」隐式推断。
     */
    private boolean useFixedDate = false;
    private String startDate = "";
    private String endDate = "";

    /** 邮件收件人（留空不发邮件） */
    private String mailTo = "";
    /** 企业微信机器人 Webhook（留空不推企微） */
    private String wecomWebhook = "";
    /** 企微摘要文案（留空用默认模板，支持占位符） */
    private String wecomDigest = "";
    /** 截图输出目录 */
    private String outputDir = "target/report";

    /** 截图顶部标题（默认「销售详情3（外卖渠道）」）；每条截图可用 title 单独覆盖 */
    private String title = "销售详情3（外卖渠道）";

    /** 底部总计行左侧合并大格的文案（如：菏泽区域线上销售共计） */
    private String totalLabel = "线上外卖销售共计";

    /**
     * 截图列表（2026-10-15 新增）：定时任务按此逐张生成截图，每个机构一张图。
     * 想发几张就配几条，顺序即发送顺序；留空则回退用上面的 org-code 单机构发一张（向后兼容）。
     */
    private List<ScreenshotTask> screenshots = new ArrayList<>();

    /** 一张截图任务 */
    public static class ScreenshotTask {
        /** 机构：门店编码「精确匹配」，多个用英文逗号分隔；空=全部机构 */
        private String orgCode = "";
        /** 文件名后缀：同一日期多张不同机构时必填（否则同名互相覆盖），留空不带后缀 */
        private String tag = "";
        /**
         * 本张截图顶部标题（如「菏泽区域线上销售」），每张图可不同。
         * 留空 = 用全局 sd3.title；全局也没配 = 默认「销售详情3（外卖渠道）」。
         */
        private String title = "";
        /**
         * 本张截图底部总计行的文案（如「菏泽区域线上销售共计」），每张图可不同。
         * 留空 = 用全局 sd3.total-label；全局也没配 = 默认「线上外卖销售共计」。
         */
        private String totalLabel = "";

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

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTotalLabel() {
            return totalLabel;
        }

        public void setTotalLabel(String totalLabel) {
            this.totalLabel = totalLabel;
        }
    }

    /** MySQL 数据源配置（前缀 sd3.datasource） */
    public static class DataSourceProps {
        private String jdbcUrl = "";
        private String username = "";
        private String password = "";
        private String driverClassName = "com.mysql.cj.jdbc.Driver";

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
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

    public String getDefaultOrgCode() {
        return defaultOrgCode;
    }

    public void setDefaultOrgCode(String defaultOrgCode) {
        this.defaultOrgCode = defaultOrgCode;
    }

    public String getDefaultChnl() {
        return defaultChnl;
    }

    public void setDefaultChnl(String defaultChnl) {
        this.defaultChnl = defaultChnl;
    }

    public List<String> getChnlOptions() {
        return chnlOptions;
    }

    public void setChnlOptions(List<String> chnlOptions) {
        this.chnlOptions = chnlOptions;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getChnl() {
        return chnl;
    }

    public void setChnl(String chnl) {
        this.chnl = chnl;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public String getTotalLabel() {
        return totalLabel;
    }

    public void setTotalLabel(String totalLabel) {
        this.totalLabel = totalLabel;
    }

    public List<ScreenshotTask> getScreenshots() {
        return screenshots;
    }

    public void setScreenshots(List<ScreenshotTask> screenshots) {
        this.screenshots = screenshots;
    }
}
