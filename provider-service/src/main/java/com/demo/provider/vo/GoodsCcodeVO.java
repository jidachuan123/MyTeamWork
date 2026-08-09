package com.demo.provider.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 品类编码出参 VO — 返回给前端下拉框
 *
 * JSON 输出: { "c_ccode": "01" }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsCcodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("c_ccode")
    private String ccode;
}
