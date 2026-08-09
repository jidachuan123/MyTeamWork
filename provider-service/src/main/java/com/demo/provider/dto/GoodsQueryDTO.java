package com.demo.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品列表查询入参 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码（从 1 开始），默认 1 */
    private int page = 1;

    /** 每页条数，默认 20 */
    private int size = 20;

    /** 搜索关键词（匹配商品编码、条码、名称、PLU号） */
    private String keyword;

    /** 品类编码过滤 */
    private String ccode;
}
