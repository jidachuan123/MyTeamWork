package com.demo.provider.utils;

/**
 * API 结果异常，用于服务间调用时抛出
 */
public class ApiResultException extends RuntimeException {

    public ApiResultException(String message) {
        super(message);
    }

    public ApiResultException(String message, Throwable cause) {
        super(message, cause);
    }
}
