package com.ygb.abroad.service;

import java.util.List;
import com.ygb.abroad.ai.AiResult;
import com.ygb.abroad.domain.AbrAiAgent;
import com.ygb.abroad.domain.vo.AbrAiTrialBody;

/**
 * AI Agent 配置 服务层
 * 
 * @author ygb
 */
public interface IAbrAiAgentService
{
    /**
     * 查询 Agent 列表（含提示词目录是否完整）
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
     * 修改 Agent 配置（编码不可改；校验审核策略、模型是否已配置、提示词版本是否存在）
     * 
     * @param agent Agent
     * @return 影响行数
     */
    public int updateAbrAiAgent(AbrAiAgent agent);

    /**
     * 试用 Agent：经网关完整执行一次（允许已停用的 Agent），记调用日志，业务类型 trial
     * 
     * @param agentCode Agent 编码
     * @param body 输入、姓名、事实
     * @param operator 操作人
     * @return 网关结果
     */
    public AiResult trialAbrAiAgent(String agentCode, AbrAiTrialBody body, String operator);
}
