package com.demo.provider.common.constants;

/**
 * 验证码常量
 * @author guomh
 * @date 2019/11/25
 */
public interface CaptchaConstant {

    /**
     * 验证码保存session的图片验证码key
     */
    String SESSION_KEY_VALIDATE_IMAGE_CODE = "SESSION_KEY_VALIDATE_IMAGE_CODE";
    /**
     * 验证码保存session的短信验证码key
     */
    String SESSION_KEY_VALIDATE_SMS_CODE = "SESSION_KEY_VALIDATE_SMS_CODE";
    /**
     * 验证码保存redis的图片验证码key
     */
    String REDIS_KEY_VALIDATE_IMAGE_CODE = "REDIS_KEY_VALIDATE_IMAGE_CODE_";
    /**
     * 验证码保存redis的短信验证码key
     */
    String REDIS_KEY_VALIDATE_SMS_CODE = "REDIS_KEY_VALIDATE_SMS_CODE_";
    /**
     * 验证码表单名称
     */
    String FORM_VALIDATE_CODE = "validateCode";
    /**
     * 验证码随机数名称
     */
    String FORM_RANDOM_KEY = "randomKey";
    /**
     * 验证码类型表单名称
     */
    String FORM_CAPTCHA_TYPE_KEY = "captchaTypeKey";
    /**
     * 验证码的位数
     */
    int RANDOM_SIZE = 4;
    /**
     * 验证码过期秒数
     */
    int EXPIRE_SECOND = 300;
    
    /**
     * 找回密码短信验证码前缀redis
     */
    String REDIS_KEY_GETPWD_COUNT_PREFIX = "KEY_COUNT_GETBACKPASSWORD_";
    /**
     * 登录发送短信验证码前缀redis
     */
    String REDIS_KEY_LOGIN_SEND_SMS_COUNT_PREFIX = "KEY_COUNT_LOGIN_SEND_SMS_CODE_";

}
