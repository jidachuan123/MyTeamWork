package com.demo.consumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 商品列表查询入参 DTO（Consumer 端，用于 Feign 调用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int page = 1;
    private int size = 20;
    private String keyword;
    private String ccode;
}
