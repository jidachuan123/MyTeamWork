package com.demo.provider.external.demo.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 示例：商品查询请求 DTO
 * <p>
 * 对接外部系统时，按对方接口文档定义请求字段。
 * 字段命名建议用 @JsonProperty 对齐对方接口的 JSON key。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoQueryReq {

    /** 商品编码 */
    private String goodsCode;

    /** 商品名称（模糊匹配） */
    private String goodsName;

    /** 页码（从 1 开始） */
    private Integer pageNum;

    /** 每页条数 */
    private Integer pageSize;
}
