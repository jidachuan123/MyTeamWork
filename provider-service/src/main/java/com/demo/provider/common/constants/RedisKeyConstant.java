package com.demo.provider.common.constants;

/**
 * redisKey常量
 * @author guomh
 * @date 2020/07/21
 */
public interface RedisKeyConstant {

    /**
     * 客户模拟token前缀
     */
    String CUSTOMER_TOKEN_PREFIX = "teamwork_customer_token_";
    /**
     * 所有租户信息
     */
    String TENANTINFO_LIST = "teamwork_tenantInfo_List";
    /**
     * 单个租户信息前缀
     */
    String TENANTINFO_PREFIX = "teamwork_tenantInfo_";
    /**
     * 租户接口地址缓存前缀
     */
    String TENANTINFO_INTERFACE_ADDR_PREFIX = "teamwork_tenantInfo_interface_addr_";
    /**
     * ip地址及过滤模块列表
     */
    String IPFILTER_MODULE_LIST = "teamwork_ipFilter_module_list";
    /**
     * ip地址过滤列表
     */
    String IPFILTER_LIST = "teamwork_ipFilter_list";
    /**
     * 调用保险公司接口token前缀
     */
    String INSUR_CO_TOKEN_PREFIX = "teamwork_insurance_company_token_";
    
    String INSUR_CO_REFRESH_TOKEN_PREFIX = "teamwork_insurance_company_refresh_token_";
    
    String CITICT_ACCOUNT_ID_PREFIX = "teamwork_citict_account_id_";
    
    String BANK_DICTI_INIT_COUNT = "teamwork_bank_dict_init_count";
    
    String BANK_DICTI_CODE_MAP_PREFIX = "teamwork_bank_dict_code_map_";
    
    String BANK_DICTI_DESC_MAP_PREFIX = "teamwork_bank_dict_desc_map_";
    
    String BANK_DICTI_CLASSFY_MAP_PREFIX = "teamwork_bank_dict_classfy_map_";
    
    String CFT_DICT_CVT_INIT_COUNT = "teamwork_cft_dict_cvt_init_count";
    
    String CFT_DICT_CVT_CODE_MAP_PREFIX = "teamwork_cft_dict_cvt_code_map_";
    
    String CFT_DICT_CVT_DESC_MAP_PREFIX = "teamwork_cft_dict_cvt_desc_map_";
    
    String CFT_DICT_CVT_CLASSFY_MAP_PREFIX = "teamwork_cft_dict_cvt_classfy_map_";
    
    String CFT_DICT_CVT_CLASSFY_RULE_TYPE_MAP = "teamwork_cft_dict_cvt_classfy_rule_type_map";

    /**
     * 租户薪资递延wms项目列表
     */
    String SALARY_WMS_PROJECT_ID_SET_PREFIX = "teamwork_salary_wmsProjectId_set_";
    /**
     * 保单查询反洗钱要素，查询合同地址，压缩包地址前缀
     */
    String POLICYTRUSTAML_CONTRACT_FILEPATH_LISTID_PREFIX = "policyTrustAml_contract_filePathListId_";
}
