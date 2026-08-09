package com.demo.provider.external.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 外部接口调用核心工具
 * <p>
 * 封装对外 HTTP 调用的公共逻辑:
 * <ol>
 *   <li>构建请求体（JSON）</li>
 *   <li>发送 HTTP POST 请求</li>
 *   <li>解析响应为 ApiRespData（两层结构）</li>
 *   <li>记录调用日志（ApiCallRecord 状态标记）</li>
 *   <li>TODO: 持久化调用记录到数据库</li>
 * </ol>
 * </p>
 *
 * <p>
 * 设计为 @Component 以便注入 RestTemplate，各业务 Service 通过依赖注入使用。
 * 参考 CiccWmInvoker.commomCallCiccWm 方法，去掉了 Base64 加解密逻辑。
 * </p>
 *
 * <pre>
 * 使用示例:
 *   {@code @Autowired private ExternalApiHelper externalApiHelper;}
 *
 *   ApiReqData reqData = new ApiReqData("queryGoods");
 *   reqData.setParam(ApiReqData.Param.builder().data(jsonParams).build());
 *
 *   ApiCallRecord record = ApiCallRecord.builder()
 *       .businessCode("001").intfCode("IF001").intfName("查询").build();
 *
 *   ApiRespData resp = externalApiHelper.callExternalApi(baseUrl, reqData, record);
 * </pre>
 */
@Slf4j
@Component
public class ExternalApiHelper {

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    private static final int MAX_PARAM_LOG_LENGTH = 2000;

    /**
     * 调用外部系统接口（核心方法）
     *
     * @param url     完整请求 URL（如 http://external-system/api/endpoint）
     * @param reqData 请求数据包装（cmdname + param）
     * @param record  调用记录（入参已由调用方填充: businessCode/intfCode/paramsJson 等）
     * @return ApiRespData 响应结果，调用方用 isSuccess() 判断成功，再解析 rsp.data
     */
    public ApiRespData callExternalApi(String url, ApiReqData reqData, ApiCallRecord record) {
        // 1. 填充调用记录基础信息
        record.setCreateTime(new Date());
        log.info("===== 开始调用外部接口 =====");
        log.info("URL: {}", url);
        log.info("cmdname: {}", reqData.getCmdname());

        // 2. 日志打印请求参数（超长截断）
        logRequestParams(reqData);

        // 3. 构建 HTTP 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApiReqData> entity = new HttpEntity<>(reqData, headers);

        // 4. 发送请求
        String respString = null;
        ApiRespData respData = null;
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            respString = response.getBody();
            log.info("外部接口响应: {}", respString);

            if (respString != null && !respString.isEmpty()) {
                // 记录响应（超长截断）
                record.markSuccess(respString);

                // 5. 解析响应为两层结构
                respData = objectMapper.readValue(respString, ApiRespData.class);
                if (respData == null) {
                    record.markFailed("响应 JSON 解析为 null");
                } else if (!respData.isSuccess()) {
                    record.markFailed(respData.getErrorMessage());
                }
            } else {
                record.markFailed("响应数据为空");
            }

        } catch (RestClientException e) {
            String errMsg = "HTTP 请求异常: " + e.getMessage();
            log.error(errMsg, e);
            record.markFailed(errMsg);

        } catch (Exception e) {
            String errMsg = "调用外部接口异常: " + e.getMessage();
            log.error(errMsg, e);
            record.markFailed(errMsg);
        }

        // 6. 日志记录调用结果
        log.info("调用结果: execStatus={}, failMsg={}", record.getExecStatus(), record.getFailMsg());
        log.info("===== 外部接口调用结束 =====");

        // 7. TODO: 持久化调用记录
        // TODO: callRecordRepository.save(record);
        // 建表后取消下面这行注释，注入 Mapper/Repository 即可
        log.debug("TODO: 持久化调用记录 - businessCode={}, intfCode={}, execStatus={}",
                record.getBusinessCode(), record.getIntfCode(), record.getExecStatus());

        return respData;
    }

    /**
     * 日志打印请求参数（超 2000 字符则截断前 1900 字符）
     */
    private void logRequestParams(ApiReqData reqData) {
        if (reqData.getParam() != null && reqData.getParam().getData() != null) {
            String data = reqData.getParam().getData();
            if (data.length() > MAX_PARAM_LOG_LENGTH) {
                log.info("请求参数(截断): {}", data.substring(0, MAX_PARAM_LOG_LENGTH - 100) + "...");
            } else {
                log.info("请求参数: {}", data);
            }
        }
    }
}
