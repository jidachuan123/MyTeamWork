package com.demo.provider.utils;

// IErrorCode 已移除 MP 依赖，使用自定义接口

/**
 * 业务错误码枚举，实现 MyBatis Plus 的 IErrorCode 接口
 */
public enum ErrorCode implements IErrorCode {

    SUCCESS(0, "操作成功"),
    REQ_FAILED(1, "请求失败"),
    PARAM_ERROR(2, "参数错误"),
    NOT_FOUND(3, "数据不存在"),
    SYSTEM_ERROR(500, "系统异常");

    private final long code;
    private final String msg;

    ErrorCode(long code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public long getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
