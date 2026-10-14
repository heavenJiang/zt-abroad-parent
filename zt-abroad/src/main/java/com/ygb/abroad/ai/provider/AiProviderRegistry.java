package com.ygb.abroad.ai.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import com.ygb.abroad.ai.AiJson;
import com.ygb.abroad.ai.AiProperties;

/**
 * 模型注册表：解析 provider:model，选择供应商实现，按配置单价计算费用
 *
 * @author ygb
 */
public class AiProviderRegistry
{
    /** 供应商类型：OpenAI 兼容 */
    public static final String TYPE_OPENAI_COMPATIBLE = "openai-compatible";

    /** 供应商类型：mock */
    public static final String TYPE_MOCK = "mock";

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final AiProperties properties;

    private final Map<String, AiProvider> byType = new HashMap<>();

    /**
     * 创建注册表（内置 mock 与 openai-compatible 两种实现）
     *
     * @param properties 配置
     */
    public AiProviderRegistry(AiProperties properties)
    {
        this(properties, new MockAiProvider(), new OpenAiCompatibleProvider());
    }

    /**
     * 创建注册表（可替换实现，测试用）
     *
     * @param properties 配置
     * @param mock mock 实现
     * @param openAi OpenAI 兼容实现
     */
    public AiProviderRegistry(AiProperties properties, AiProvider mock, AiProvider openAi)
    {
        this.properties = properties;
        byType.put(TYPE_MOCK, mock);
        byType.put(TYPE_OPENAI_COMPATIBLE, openAi);
    }

    /**
     * 校验模型标识是否已配置
     *
     * @param spec provider:model
     * @return 错误说明，已配置返回 null
     */
    public String check(String spec)
    {
        try
        {
            resolve(spec);
            return null;
        }
        catch (AiProviderException e)
        {
            return e.getMessage();
        }
    }

    /**
     * 解析模型标识
     *
     * @param spec provider:model
     * @return 模型引用
     * @throws AiProviderException 格式错误或未配置
     */
    public ModelRef resolve(String spec)
    {
        int i = spec == null ? -1 : spec.indexOf(':');
        if (i <= 0 || i == spec.length() - 1)
        {
            throw new AiProviderException("模型标识「" + spec + "」格式应为 provider:model");
        }
        String providerName = spec.substring(0, i);
        String model = spec.substring(i + 1);
        AiProperties.Provider provider = properties.getProviders().get(providerName);
        if (provider == null)
        {
            throw new AiProviderException("模型供应商「" + providerName + "」未在 abroad.ai.providers 中配置");
        }
        AiProvider impl = byType.get(provider.getType());
        if (impl == null)
        {
            throw new AiProviderException("模型供应商「" + providerName + "」的类型「" + provider.getType() + "」不支持（可选 openai-compatible、mock）");
        }
        AiProperties.Model price = provider.getModels().get(model);
        if (price == null)
        {
            throw new AiProviderException("模型「" + model + "」未在 abroad.ai.providers." + providerName + ".models 中配置");
        }
        return new ModelRef(spec, providerName, model, provider, price, impl);
    }

    /**
     * 计算费用
     *
     * @param ref 模型
     * @param promptTokens 输入 token
     * @param completionTokens 输出 token
     * @return 费用（元，6 位小数）
     */
    public static BigDecimal cost(ModelRef ref, int promptTokens, int completionTokens)
    {
        BigDecimal in = AiJson.nz(ref.getPrice().getInputPricePer1k()).multiply(BigDecimal.valueOf(promptTokens));
        BigDecimal out = AiJson.nz(ref.getPrice().getOutputPricePer1k()).multiply(BigDecimal.valueOf(completionTokens));
        return in.add(out).divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }

    /**
     * 已解析的模型
     */
    public static class ModelRef
    {
        private final String spec;

        private final String providerName;

        private final String model;

        private final AiProperties.Provider provider;

        private final AiProperties.Model price;

        private final AiProvider impl;

        ModelRef(String spec, String providerName, String model, AiProperties.Provider provider, AiProperties.Model price, AiProvider impl)
        {
            this.spec = spec;
            this.providerName = providerName;
            this.model = model;
            this.provider = provider;
            this.price = price;
            this.impl = impl;
        }

        public String getSpec()
        {
            return spec;
        }

        public String getProviderName()
        {
            return providerName;
        }

        public String getModel()
        {
            return model;
        }

        public AiProperties.Provider getProvider()
        {
            return provider;
        }

        public AiProperties.Model getPrice()
        {
            return price;
        }

        public AiProvider getImpl()
        {
            return impl;
        }
    }
}
