package com.ygb.abroad.ai;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrAiAgent;

/**
 * 检索（网关第 3 步）：从院校库 / 知识库取事实并附来源，填入提示词变量 {{facts}}
 *
 * @author ygb
 */
public interface AiRetriever
{
    /**
     * 检索事实
     *
     * @param agent Agent 配置
     * @param maskedInput 去标识化后的输入
     * @param context 调用上下文
     * @return 事实列表（每条建议含 source 字段），无则返回 null
     */
    public List<Map<String, Object>> retrieve(AbrAiAgent agent, Map<String, Object> maskedInput, AiContext context);
}
