package com.ygb.abroad.ai;

import java.util.List;
import java.util.Map;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.AbrAiCallLog;
import com.ygb.abroad.domain.AbrAiReview;
import tools.jackson.databind.JsonNode;

/**
 * 网关的持久化接口（Agent 配置、调用日志、审核队列），便于不依赖 Spring 测试管道
 *
 * @author ygb
 */
public interface AiStore
{
    /**
     * 预取调用日志ID（审核记录、抽检随机数要用）
     *
     * @return 调用ID
     */
    public Long nextCallId();

    /**
     * 按编码查询 Agent
     *
     * @param agentCode Agent 编码
     * @return Agent，不存在返回 null
     */
    public AbrAiAgent selectAgentByCode(String agentCode);

    /**
     * 写调用日志（callId 已预取）
     *
     * @param log 调用日志
     */
    public void insertCallLog(AbrAiCallLog log);

    /**
     * 写审核记录
     *
     * @param review 审核记录
     * @return 审核ID
     */
    public Long insertReview(AbrAiReview review);

    /**
     * 数据库评测集中某个 Agent 启用的样例（评测命令用）
     *
     * @param agentCode Agent 编码
     * @return 样例名（db-样例ID）→ 样例定义
     */
    public default List<Map.Entry<String, JsonNode>> selectEvalCases(String agentCode)
    {
        return List.of();
    }
}
