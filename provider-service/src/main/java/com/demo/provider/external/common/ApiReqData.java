package com.demo.provider.external.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部接口请求包装 — 通用请求结构
 * <p>
 * 参考 CiccReqDataDto 的设计：外层 cmdname（接口命令名），内层 Param 承载实际业务参数。
 * 各外部系统可复用此类，也可继承扩展。
 * </p>
 *
 * <pre>
 * 使用示例:
 *   ApiReqData req = new ApiReqData("queryGoods");
 *   ApiReqData.Param param = ApiReqData.Param.builder()
 *       .data(JSON.toJSONString(bizDto))
 *       .extra("trustCompany", "zhongxin")
 *       .build();
 *   req.setParam(param);
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiReqData {

    /** 接口命令名，标识具体调用的业务功能 */
    private String cmdname;

    /** 请求参数 */
    private Param param;

    public ApiReqData(String cmdname) {
        this.cmdname = cmdname;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Param {

        /**
         * 业务数据 — JSON 字符串
         * <p>
         * 将具体业务 DTO 序列化为 JSON 字符串后传入，由被调用方自行解析。
         * 例如: data = JSON.toJSONString(goodsQueryReq)
         * </p>
         */
        private String data;

        /**
         * 扩展参数 — 存放信任公司、租户标识等非业务数据
         * <p>
         * 示例:
         *   extra.put("trustCompany", "zhongxin");
         *   extra.put("tenantId", "8");
         * </p>
         */
        private java.util.Map<String, Object> extra;
    }
}
