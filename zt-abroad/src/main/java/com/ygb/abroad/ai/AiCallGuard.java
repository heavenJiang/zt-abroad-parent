package com.ygb.abroad.ai;

/**
 * 模型调用前的放行检查（由获客执行保障实现：全局或 Agent 暂停、停用、超出每天 AI 预算时拦下）
 *
 * 只在正式调用时检查；管理端试用与评测不受影响。拦下时不调用模型，结果状态为 blocked，调用方走兜底。
 *
 * @author ygb
 */
public interface AiCallGuard
{
    /**
     * 检查某 Agent 现在能否调用模型
     *
     * @param agentCode Agent 编码
     * @return 拦下原因，放行返回 null
     */
    public String blockReason(String agentCode);
}
