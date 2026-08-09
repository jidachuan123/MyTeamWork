package com.demo.provider.utils;

import com.demo.provider.common.constants.SystemConstant;
import lombok.Data;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * <p>
 * REST API 返回结果
 * </p>
 *
 * @author hubin
 * @since 2018-06-05
 */
@Data
public class ApiResult<T> {

    /**
     * 业务错误码
     */
    private long code;
    /**
     * 结果集
     */
    private T result;
    /**
     * 描述
     */
    private String msg;
    private long page;
    private long size;
    private long count;
    private long total;
    private String traceId; //日志跟踪id

    public ApiResult() {
        // to do nothing
    }

    public ApiResult(IErrorCode errorCode) {
        errorCode = Optional.ofNullable(errorCode).orElse(ErrorCode.REQ_FAILED);
        this.code = errorCode.getCode();
        this.msg = errorCode.getMsg();
    }

    public static <T> ApiResult<T> ok(T result) {
        return restResult(result, ErrorCode.SUCCESS);
    }

    public static <T> ApiResult<T> failed(String msg) {
        return restResult(null, ErrorCode.REQ_FAILED.getCode(), msg);
    }

    public static <T> ApiResult<T> failed(IErrorCode errorCode) {
        return restResult(null, errorCode);
    }

    public static <T> ApiResult<T> restResult(T data, IErrorCode errorCode) {
        return restResult(data, errorCode.getCode(), errorCode.getMsg());
    }

    private static <T> ApiResult<T> restResult(T result, long code, String msg) {
        String traceId = MDC.get(SystemConstant.LOG_TRACE_ID);
        ApiResult<T> apiResult = new ApiResult<>();
        apiResult.setCode(code);
        apiResult.setResult(result);
        apiResult.setMsg(msg);
        apiResult.setTraceId(traceId);
        return apiResult;
    }

    public boolean ok() {
        return ErrorCode.SUCCESS.getCode()==this.code;
    }

    /**
     * 服务间调用非业务正常，异常直接释放
     */
    public T serviceData() {
        if (!ok()) {
            throw new ApiResultException(this.msg);
        }
        return result;
    }

    @Override
    public String toString() {
        return "ApiResult [code=" + code + ", result=" + result + ", msg=" + msg + "]";
    }
}
