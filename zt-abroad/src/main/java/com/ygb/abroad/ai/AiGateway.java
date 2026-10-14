package com.ygb.abroad.ai;

import java.util.Map;

/**
 * AI 网关：业务代码调用大模型的唯一入口
 * <p>
 * 管道：读取 Agent 配置 → 去标识化 → 可选检索 → 渲染提示词并调用主模型（失败切备用） → Schema 校验（失败重试 1 次）
 * → 输出过滤 → 还原占位符并按审核策略返回或进入审核队列 → 写调用日志。
 * 不抛异常：所有失败都体现在 {@link AiResult#getStatus()} 中，调用方据此走兜底逻辑。
 * </p>
 *
 * @author ygb
 */
public interface AiGateway
{
    /**
     * 调用 Agent
     *
     * @param agentCode Agent 编码（abr_ai_agent.agent_code）
     * @param input 输入变量（填充 prompts/&lt;agent&gt;/&lt;版本&gt;/user.md 中的 {{变量}}）
     * @param context 调用上下文（业务类型与 ID、操作人、需去标识化的姓名、检索到的事实）
     * @return 调用结果
     */
    public AiResult invoke(String agentCode, Map<String, Object> input, AiContext context);
}
