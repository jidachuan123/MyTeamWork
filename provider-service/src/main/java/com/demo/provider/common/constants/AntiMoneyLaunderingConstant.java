package com.demo.provider.common.constants;

/**
 * <p>
 * 反洗钱常量
 * </p>
 *
 * @author weihaobin
 * @since 2022-09-04
 */
public interface AntiMoneyLaunderingConstant {
    /**
     * 主体类型 1:个人 2:机构
     */
    String AML_CSTP_1 = "1";
    String AML_CSTP_2 = "2";

    /**
     * 申请部门 默认  0_196
     */
    String AML_MBRC_0196="0_196";

    /**
     * 0:直销 1：代销
     */
    String AML_CHANNEL_1="1";
    String AML_CHANNEL_0="0";

    String AML_RSCD_TEAMWORK="teamwork";
    /**
     * 反洗钱成功状态码
     */
    String AML_RETURN_CODE_10000="10000";
    /**
     * 反洗钱成功状态码
     * 0 通过(无匹配 or 匹配接受 or 匹配排除)
     * 1 预警
     */
    int AML_RETURN_CODE_0=0;
}
