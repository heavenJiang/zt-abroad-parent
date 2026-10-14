package com.ygb.abroad.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ygb.abroad.ai.provider.AiProviderRegistry;

/**
 * AI 网关装配
 *
 * @author ygb
 */
@Configuration
public class AiConfig
{
    /**
     * 模型注册表（mock + openai-compatible）
     *
     * @param properties abroad.ai 配置
     * @return 注册表
     */
    @Bean
    public AiProviderRegistry aiProviderRegistry(AiProperties properties)
    {
        return new AiProviderRegistry(properties);
    }
}
