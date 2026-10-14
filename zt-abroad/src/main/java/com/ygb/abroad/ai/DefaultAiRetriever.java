package com.ygb.abroad.ai;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.ygb.abroad.domain.AbrAiAgent;

/**
 * 默认检索：不主动检索，直接使用调用方在上下文中传入的事实（AiContext.facts）
 *
 * @author ygb
 */
@Component
public class DefaultAiRetriever implements AiRetriever
{
    /**
     * 返回上下文中的事实
     *
     * @param agent Agent 配置
     * @param maskedInput 去标识化后的输入
     * @param context 调用上下文
     * @return 事实列表，可能为 null
     */
    @Override
    public List<Map<String, Object>> retrieve(AbrAiAgent agent, Map<String, Object> maskedInput, AiContext context)
    {
        return context == null ? null : context.getFacts();
    }
}
