package com.demo.provider.external.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.demo.provider.external.common.ApiCallRecord;
import com.demo.provider.external.common.ApiReqData;
import com.demo.provider.external.common.ApiRespData;
import com.demo.provider.external.common.ExternalApiHelper;
import com.demo.provider.external.demo.dto.req.DemoQueryReq;
import com.demo.provider.external.demo.dto.resp.DemoQueryResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 示例：演示如何对接外部系统
 * <p>
 * 这是对接外部系统的标准模板，后续对接新的外部系统时:</p>
 * <ol>
 *   <li>复制 {@code demo} 包 → 重命名为目标系统名（如 {@code ciccwm}）</li>
 *   <li>在 {@code dto/req/} 下定义请求 DTO</li>
 *   <li>在 {@code dto/resp/} 下定义响应 DTO</li>
 *   <li>创建 XXXService，参照本类编写调用逻辑</li>
 * </ol>
 *
 * <p>
 * 核心流程（参考 CiccWmInvoker.commomCallCiccWm）:</p>
 * <pre>
 * ① 构建业务 DTO     — 填充请求字段
 * ② 构建 ApiReqData   — 包装 cmdname + param（含 JSON 业务数据）
 * ③ 构建 ApiCallRecord — 填充调用记录信息
 * ④ 调用 ExternalApiHelper.callExternalApi() — 发送 HTTP 请求
 * ⑤ 解析 ApiRespData   — 两层校验（ret + rsp.errorNo）
 * ⑥ 反序列化 rsp.data  — 转为具体响应 DTO
 * ⑦ TODO: 持久化 ApiCallRecord — 成功/失败均记录
 * </pre>
 */
@Slf4j
@Service
public class DemoExternalService {

    /**
     * 当前系统的标识码
     */
    private static final String SYS_CODE = "PROVIDER_SYS";

    /**
     * 被调用外部系统的标识码
     */
    private static final String RCV_SYS_CODE = "EXTERNAL_DEMO";

    /**
     * 接口编码（与对方约定）
     */
    private static final String INTF_CODE_QUERY = "DEMO_QUERY_GOODS";

    @Resource
    private ExternalApiHelper externalApiHelper;

    @Resource
    private ObjectMapper objectMapper;

    /** 对方接口基础 URL（实际应从配置读取） */
    // @Value("${external.demo.baseUrl}")
    private String baseUrl = "http://external-demo-system/api/v1";

    // ==================== 第一步: 定义各业务方法 ====================

    /**
     * 查询商品 — 对接外部系统示例
     *
     * @param goodsCode  商品编码
     * @param goodsName  商品名称
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 商品列表（失败时返回空列表）
     */
    public List<DemoQueryResp.GoodsItem> queryGoods(
            String goodsCode, String goodsName, Integer pageNum, Integer pageSize) {

        log.info("【Mock模式】返回模拟数据: goodsCode={}, goodsName={}, pageNum={}, pageSize={}",
                goodsCode, goodsName, pageNum, pageSize);

        // ============ Mock 数据 — 模拟外部系统返回 ============
        // 真实对接时，替换为下方注释的 ①~⑦ 步骤，调用 externalApiHelper

        DemoQueryResp.GoodsItem item = new DemoQueryResp.GoodsItem();
        item.setGoodsCode(goodsCode != null && !goodsCode.isEmpty() ? goodsCode : "MOCK-001");
        item.setGoodsName("模拟商品-测试数据");
        item.setGoodsModel("MODEL-X");
        item.setUnit("个");
        item.setPrice(new java.math.BigDecimal("99.99"));

        return Collections.singletonList(item);

        // ============ 真实调用代码（对接外部系统时启用） ===========
        // ① 构建业务 DTO
        // DemoQueryReq bizReq = DemoQueryReq.builder()
        //         .goodsCode(goodsCode)
        //         .goodsName(goodsName)
        //         .pageNum(pageNum)
        //         .pageSize(pageSize)
        //         .build();
        //
        // // ② 包装为 ApiReqData — data 字段放 JSON 字符串
        // ApiReqData reqData = new ApiReqData("queryGoods");
        // String paramsJson = toJson(bizReq);
        // ApiReqData.Param param = ApiReqData.Param.builder()
        //         .data(paramsJson)
        //         .build();
        // reqData.setParam(param);
        //
        // // ③ 构建调用记录
        // String batchNo = UUID.randomUUID().toString().replace("-", "");
        // ApiCallRecord record = ApiCallRecord.builder()
        //         .businessCode(goodsCode)
        //         .businessType("QUERY")
        //         .paramsJson(paramsJson)
        //         .reqSysCode(SYS_CODE)
        //         .rcvrSysCode(RCV_SYS_CODE)
        //         .reqSerialNum(batchNo)
        //         .intfCode(INTF_CODE_QUERY)
        //         .intfName("商品查询接口")
        //         .build();
        //
        // // ④ 发起调用
        // String fullUrl = baseUrl + "/goods/query";
        // ApiRespData resp = externalApiHelper.callExternalApi(fullUrl, reqData, record);
        //
        // // ⑤ 判断结果
        // if (resp == null || !resp.isSuccess()) {
        //     log.warn("外部系统调用失败: {}", resp != null ? resp.getErrorMessage() : "无响应");
        //     return Collections.emptyList();
        // }
        //
        // // ⑥ 反序列化 rsp.data → 具体响应 DTO
        // String respData = resp.getRsp().getData();
        // DemoQueryResp bizResp = fromJson(respData, DemoQueryResp.class);
        // if (bizResp == null) {
        //     log.warn("响应 data 解析失败: {}", respData);
        //     return Collections.emptyList();
        // }
        //
        // // ⑦ TODO: 持久化调用记录（已由 ExternalApiHelper 标记状态）
        // // TODO: recordMapper.insert(record);
        //
        // return bizResp.getGoodsList() != null
        //         ? bizResp.getGoodsList()
        //         : Collections.emptyList();
    }

    // ==================== JSON 工具方法（封装受检异常） ====================

    /**
     * 对象 → JSON 字符串（封装 JsonProcessingException）
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /**
     * JSON 字符串 → 对象（封装 JsonProcessingException）
     */
    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
