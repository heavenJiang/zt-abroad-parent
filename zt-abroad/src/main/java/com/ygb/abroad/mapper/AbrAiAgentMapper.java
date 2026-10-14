package com.ygb.abroad.mapper;

import java.util.List;
import com.ygb.abroad.domain.AbrAiAgent;

/**
 * AI Agent 配置 数据层
 * 
 * @author ygb
 */
public interface AbrAiAgentMapper
{
    /**
     * 查询 Agent 列表
     * 
     * @param agent 查询条件
     * @return Agent 集合
     */
    public List<AbrAiAgent> selectAbrAiAgentList(AbrAiAgent agent);

    /**
     * 按ID查询 Agent
     * 
     * @param agentId Agent ID
     * @return Agent
     */
    public AbrAiAgent selectAbrAiAgentById(Long agentId);

    /**
     * 按编码查询 Agent
     * 
     * @param agentCode Agent 编码
     * @return Agent
     */
    public AbrAiAgent selectAbrAiAgentByCode(String agentCode);

    /**
     * 修改 Agent 配置（编码不可改）
     * 
     * @param agent Agent
     * @return 影响行数
     */
    public int updateAbrAiAgent(AbrAiAgent agent);
}
