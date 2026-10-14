package com.ygb.abroad.ai;

import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ygb.abroad.ai.provider.AiProviderRegistry;

/**
 * AI 网关（Spring 装配）：管道见 {@link AiPipeline}
 *
 * @author ygb
 */
@Service
public class AiGatewayImpl implements AiGateway
{
    @Autowired
    private AiProperties properties;

    @Autowired
    private AiPromptLoader promptLoader;

    @Autowired
    private AiStore store;

    @Autowired
    private AiRetriever retriever;

    @Autowired
    private AiProviderRegistry registry;

    private AiPipeline pipeline;

    /**
     * 装配管道：加载过滤词表
     */
    @PostConstruct
    public void init()
    {
        AiOutputFilter filter = new AiOutputFilter(promptLoader.loadFilterLists(), properties.getFilter().getMaxFieldLength());
        pipeline = new AiPipeline(store, promptLoader, registry, filter, retriever);
    }

    /**
     * 调用 Agent
     *
     * @param agentCode Agent 编码
     * @param input 输入变量
     * @param context 调用上下文
     * @return 调用结果
     */
    @Override
    public AiResult invoke(String agentCode, Map<String, Object> input, AiContext context)
    {
        return pipeline.invoke(agentCode, input, context);
    }
}
