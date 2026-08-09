package com.demo.consumer.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 品类编码出参 VO（Consumer 端，接收 Provider JSON 反序列化）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsCcodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("c_ccode")
    private String ccode;
}
