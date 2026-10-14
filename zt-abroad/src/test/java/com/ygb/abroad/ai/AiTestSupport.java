package com.ygb.abroad.ai;

import java.math.BigDecimal;
import com.ygb.abroad.ai.provider.AiProviderRegistry;

/**
 * 测试装配：mock 供应商（default、backup 两个模型）+ 真实的提示词与过滤词表
 *
 * @author ygb
 */
public final class AiTestSupport
{
    public static final AiPromptLoader LOADER = new AiPromptLoader();

    private AiTestSupport()
    {
    }

    public static AiProperties properties()
    {
        AiProperties p = new AiProperties();
        AiProperties.Provider mock = new AiProperties.Provider();
        mock.setType("mock");
        mock.getModels().put("default", new AiProperties.Model(new BigDecimal("0.001"), new BigDecimal("0.002")));
        mock.getModels().put("backup", new AiProperties.Model(new BigDecimal("0.01"), new BigDecimal("0.02")));
        p.getProviders().put("mock", mock);
        return p;
    }

    public static AiPipeline pipeline(FakeAiStore store)
    {
        AiProperties p = properties();
        AiOutputFilter filter = new AiOutputFilter(LOADER.loadFilterLists(), p.getFilter().getMaxFieldLength());
        return new AiPipeline(store, LOADER, new AiProviderRegistry(p), filter, new DefaultAiRetriever());
    }
}
