package com.demo.provider.external.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 外部接口调用记录实体
 * <p>
 * 参考 CftIntfCallRecord，记录每次对外调用的完整链路信息，
 * 便于排查问题和审计追溯。
 * </p>
 *
 * <pre>
 * 构建示例:
 *   ApiCallRecord record = ApiCallRecord.builder()
 *       .businessCode("PD12509300010")
 *       .businessType("QUERY")
 *       .paramsJson(paramsJson)
 *       .reqSysCode("MY_SYSTEM")
 *       .rcvrSysCode("EXTERNAL_SYS")
 *       .reqSerialNum(UUID.randomUUID().toString())
 *       .intfCode("IF001")
 *       .intfName("商品查询接口")
 *       .build();
 * </pre>
 *
 * TODO: 建表后添加 @TableName 注解并实现持久化
 * TODO: 建表 SQL 参考:
 * <pre>
 * CREATE TABLE ext_intf_call_record (
 *     id                BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     business_code     VARCHAR(64)    COMMENT '业务编号',
 *     business_type     VARCHAR(32)    COMMENT '业务类型',
 *     params_json       TEXT           COMMENT '请求参数 JSON',
 *     req_sys_code      VARCHAR(32)    COMMENT '发起方系统码',
 *     rcvr_sys_code     VARCHAR(32)    COMMENT '接收方系统码',
 *     req_serial_num    VARCHAR(64)    COMMENT '请求流水号',
 *     intf_code         VARCHAR(32)    COMMENT '接口编码',
 *     intf_name         VARCHAR(128)   COMMENT '接口名称',
 *     exec_status       VARCHAR(2)     COMMENT '执行状态: 1-成功 0-失败',
 *     fail_msg          VARCHAR(1024)  COMMENT '失败原因',
 *     resp_data         TEXT           COMMENT '响应数据',
 *     create_time       DATETIME       COMMENT '创建时间'
 * );
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiCallRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务编号（如盘点单号、商品编码） */
    private String businessCode;

    /** 业务类型（自定义标识，如 QUERY / PUSH / CALLBACK） */
    private String businessType;

    /** 请求参数的 JSON 字符串 */
    private String paramsJson;

    /** 发起方系统编码 */
    private String reqSysCode;

    /** 接收方系统编码 */
    private String rcvrSysCode;

    /** 请求流水号（UUID，每次调用唯一） */
    private String reqSerialNum;

    /** 接口编码 */
    private String intfCode;

    /** 接口名称 */
    private String intfName;

    /** 执行状态: "1"=成功, "0"=失败 */
    private String execStatus;

    /** 失败原因（成功时为空） */
    private String failMsg;

    /** 响应数据 JSON 字符串（超长截断） */
    private String respData;

    /** 创建时间 */
    private Date createTime;

    // ==================== 便捷方法 ====================

    /**
     * 标记调用成功，填充响应数据（超 10000 字符自动截断）
     */
    public void markSuccess(String respData) {
        this.execStatus = "1";
        this.failMsg = null;
        this.respData = respData != null && respData.length() > 10000
                ? respData.substring(0, 10000)
                : respData;
    }

    /**
     * 标记调用失败
     */
    public void markFailed(String failMsg) {
        this.execStatus = "0";
        this.failMsg = failMsg;
    }
}
