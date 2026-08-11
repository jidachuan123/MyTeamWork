package com.demo.consumer.utils;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一 API 返回结果（Consumer 端最小化反序列化 POJO）
 *
 * 与 Provider 端 ApiResult 的 JSON 结构一致，Feign 反序列化用
 */
@Data
@NoArgsConstructor
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long code;
    private T result;
    private String msg;
    private long page;
    private long size;
    private long count;
    private long total;
    private String traceId;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 0;
        r.result = data;
        r.msg = "操作成功";
        return r;
    }

    public static <T> ApiResult<T> failed(String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 1;
        r.msg = msg;
        return r;
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
