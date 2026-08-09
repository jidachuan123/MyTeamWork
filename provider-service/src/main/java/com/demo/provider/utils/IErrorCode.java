package com.demo.provider.utils;

/**
 * 错误码接口，定义业务错误码的 code 和 msg 规范。
 * 注：MyBatis Plus 3.5.x 已移除 IErrorCode，此处自定义替代。
 */
public interface IErrorCode {

    /** 获取错误码 */
    long getCode();

    /** 获取错误信息 */
    String getMsg();
}
