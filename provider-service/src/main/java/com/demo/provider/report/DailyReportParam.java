package com.demo.provider.report;

/**
 * 销售日报手动触发的查询参数。
 *
 * 前端「销售详情1 / 销售详情2」页面点击「发送日报」时，
 * 把当前页面 queryForm 的查询条件原样传过来，后端据此截图表 + 发邮件，
 * 不再写死 orgCode / 日期。
 *
 * 日期规则（与页面一致）：
 *   - startDate/endDate       = 本期（查询区间）
 *   - cmpStartDate/cmpEndDate = 环比对比区间
 *   - yoyStartDate/yoyEndDate = 同比对比区间
 *
 * SD1 使用：orgCode、deptLevels、6 个日期
 * SD2 使用：orgCode、department、deptLevels、6 个日期
 */
public class DailyReportParam {

    /** 机构编码（SD1 默认 1101001；SD2 默认 1101,1102,1191001；留空查全部） */
    private String orgCode;

    /** 部门层级（SD1 明细查询用；SD2 透传，留空走后端默认口径） */
    private String deptLevels;

    /** 部门编码（SD2 用，留空走后端默认口径） */
    private String department;

    private String startDate;
    private String endDate;
    private String cmpStartDate;
    private String cmpEndDate;
    private String yoyStartDate;
    private String yoyEndDate;

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getDeptLevels() {
        return deptLevels;
    }

    public void setDeptLevels(String deptLevels) {
        this.deptLevels = deptLevels;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
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
