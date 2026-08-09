package com.demo.provider.external.demo.dto.resp;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 示例：商品查询响应 DTO
 * <p>
 * 对应外部接口返回的 data 字段内容（ApiRespData.rsp.data 反序列化为此类）。
 * 字段命名用 @JsonProperty 对齐对方接口的 JSON key。
 * </p>
 */
@Data
@NoArgsConstructor
public class DemoQueryResp {

    /** 商品列表 */
    private List<GoodsItem> goodsList;

    /** 总条数 */
    private Long totalCount;

    @Data
    @NoArgsConstructor
    public static class GoodsItem {
        private String goodsCode;
        private String goodsName;
        private String goodsModel;
        private String unit;
        private BigDecimal price;
    }
}
