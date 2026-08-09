package com.demo.provider.external.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部接口响应包装 — 两层结构通用响应
 * <p>
 * 参考 CiccWmCallResp + CiccwmRsp 的设计：
 * <ul>
 *   <li>外层: ret（返回码）、msg（消息）、time（时间）、msgno（消息编号）</li>
 *   <li>内层 Rsp: errorNo（业务错误码）、errorInfo（错误描述）、data（业务数据 JSON 字符串）</li>
 * </ul>
 * </p>
 *
 * <pre>
 * 解析示例:
 *   ApiRespData resp = JSON.parseObject(respJson, ApiRespData.class);
 *   if (resp.isSuccess()) {
 *       MyBizDto dto = JSON.parseObject(resp.getRsp().getData(), MyBizDto.class);
 *   }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiRespData {

    /** 外层返回码 (0 = 成功) */
    private int ret;

    /** 外层消息 */
    private String msg;

    /** 响应时间 */
    private String time;

    /** 消息编号 */
    private String msgno;

    /** 内层响应 */
    private Rsp rsp;

    // ==================== 便捷方法 ====================

    /**
     * 两步校验: 外层 ret == 0 且 内层 rsp.errorNo == 0 才认为成功
     */
    public boolean isSuccess() {
        return ret == 0
                && rsp != null
                && rsp.getErrorNo() == 0;
    }

    /**
     * 获取错误信息（优先取内层 errorInfo，其次外层 msg）
     */
    public String getErrorMessage() {
        if (rsp != null && rsp.getErrorInfo() != null && !rsp.getErrorInfo().isEmpty()) {
            return rsp.getErrorInfo();
        }
        return msg != null ? msg : "未知错误";
    }

    // ==================== 内层响应 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rsp {
        /** 业务错误码 (0 = 成功) */
        private int errorNo;

        /** 错误描述 */
        private String errorInfo;

        /** 业务响应数据 — JSON 字符串，需要二次解析为具体 DTO */
        private String data;
    }
}
