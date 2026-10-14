package com.ygb.abroad.ai;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 网关配置（application.yml 的 abroad.ai）
 *
 * @author ygb
 */
@Component
@ConfigurationProperties(prefix = "abroad.ai")
public class AiProperties
{
    /** 模型供应商：名称 → 配置（Agent 中的模型写作 名称:模型） */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    /** 输出过滤 */
    private Filter filter = new Filter();

    public Map<String, Provider> getProviders()
    {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers)
    {
        this.providers = providers;
    }

    public Filter getFilter()
    {
        return filter;
    }

    public void setFilter(Filter filter)
    {
        this.filter = filter;
    }

    /**
     * 模型供应商
     */
    public static class Provider
    {
        /** 类型：openai-compatible / mock */
        private String type;

        /** 接口地址（openai-compatible，如 https://api.deepseek.com/v1） */
        private String baseUrl;

        /** API Key（只从环境变量注入，禁止写入仓库、禁止写日志） */
        private String apiKey;

        /** 可用模型：模型名 → 单价 */
        private Map<String, Model> models = new LinkedHashMap<>();

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public Map<String, Model> getModels()
        {
            return models;
        }

        public void setModels(Map<String, Model> models)
        {
            this.models = models;
        }

        @Override
        public String toString()
        {
            return "Provider[type=" + type + ", baseUrl=" + baseUrl + ", models=" + models.keySet() + "]";
        }
    }

    /**
     * 模型单价（元 / 1000 token）
     */
    public static class Model
    {
        /** 输入单价 */
        private BigDecimal inputPricePer1k = BigDecimal.ZERO;

        /** 输出单价 */
        private BigDecimal outputPricePer1k = BigDecimal.ZERO;

        public Model()
        {
        }

        public Model(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k)
        {
            this.inputPricePer1k = inputPricePer1k;
            this.outputPricePer1k = outputPricePer1k;
        }

        public BigDecimal getInputPricePer1k()
        {
            return inputPricePer1k;
        }

        public void setInputPricePer1k(BigDecimal inputPricePer1k)
        {
            this.inputPricePer1k = inputPricePer1k;
        }

        public BigDecimal getOutputPricePer1k()
        {
            return outputPricePer1k;
        }

        public void setOutputPricePer1k(BigDecimal outputPricePer1k)
        {
            this.outputPricePer1k = outputPricePer1k;
        }
    }

    /**
     * 输出过滤配置
     */
    public static class Filter
    {
        /** 单个字符串字段最大长度，超过视为整篇文书 */
        private int maxFieldLength = AiOutputFilter.DEFAULT_MAX_FIELD_LENGTH;

        public int getMaxFieldLength()
        {
            return maxFieldLength;
        }

        public void setMaxFieldLength(int maxFieldLength)
        {
            this.maxFieldLength = maxFieldLength;
        }
    }
}
